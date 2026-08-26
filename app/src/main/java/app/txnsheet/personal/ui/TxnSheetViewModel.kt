package app.txnsheet.personal.ui

import android.app.Application
import android.content.Intent
import androidx.room.withTransaction
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.txnsheet.personal.TxnSheetApplication
import app.txnsheet.personal.data.local.AppConfigEntity
import app.txnsheet.personal.data.local.CategoryRuleEntity
import app.txnsheet.personal.data.local.CorrectionAuditEntity
import app.txnsheet.personal.data.local.SyncJobEntity
import app.txnsheet.personal.data.repository.Destination
import app.txnsheet.personal.data.repository.IngestionResult
import app.txnsheet.personal.domain.Direction
import app.txnsheet.personal.domain.TransactionMethod
import app.txnsheet.personal.parsing.ParseAction
import app.txnsheet.personal.parsing.ParseContext
import app.txnsheet.personal.parsing.ParseOrigin
import app.txnsheet.personal.sync.WorkbookSetupOutcome
import app.txnsheet.personal.sync.GoogleDisconnectOutcome
import app.txnsheet.personal.ui.screens.ReviewSubmission
import app.txnsheet.personal.ui.screens.ManualTransactionSubmission
import app.txnsheet.personal.ui.screens.TransactionEdits
import java.time.Instant
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TxnSheetViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as TxnSheetApplication).container
    private val database = container.database
    private val runtime = MutableStateFlow(RuntimeUiState())
    private val effectChannel = Channel<UiEffect>(Channel.BUFFERED)

    val effects = effectChannel.receiveAsFlow()

    private val persistentState = combine(
        database.transactionDao().observeAll(),
        database.configDao().observe(),
        database.sourceAppDao().observeAll(),
        database.categoryRuleDao().observeAll(),
        database.diagnosticDao().observeRecent(),
    ) { transactions, config, sources, categoryRules, diagnostics ->
        PersistentUiState(
            config = config ?: AppConfigEntity(),
            transactions = transactions,
            sources = sources,
            categoryRules = categoryRules,
            diagnostics = diagnostics,
        )
    }

    val uiState = combine(persistentState, runtime) { persistent, volatile ->
        TxnSheetUiState(
            loading = false,
            config = persistent.config,
            transactions = persistent.transactions,
            sources = persistent.sources,
            categoryRules = persistent.categoryRules,
            diagnostics = persistent.diagnostics,
            notificationAccessGranted = volatile.notificationAccessGranted,
            actionInProgress = volatile.actionInProgress,
            reviewSourceById = volatile.reviewSourceById,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TxnSheetUiState())

    init {
        viewModelScope.launch(Dispatchers.IO) { container.initializeLocalState() }
    }

    fun refreshSystemReadiness(notificationAccessGranted: Boolean) {
        runtime.update { it.copy(notificationAccessGranted = notificationAccessGranted) }
    }

    fun completeOnboarding() = launchAction {
        val current = database.configDao().get() ?: AppConfigEntity()
        database.configDao().upsert(current.copy(onboardingComplete = true))
    }

    fun setSourceEnabled(packageName: String, enabled: Boolean) = launchAction {
        database.sourceAppDao().setEnabled(packageName, enabled)
    }

    fun ingestManual(rawText: String, fromShare: Boolean) = launchAction {
        val now = Instant.now()
        when (
            val result = container.transactionIngestor.ingest(
                rawText = rawText,
                context = ParseContext(
                    sourcePackage = if (fromShare) "app.txnsheet.personal.share" else "app.txnsheet.personal.paste",
                    sourceLabel = if (fromShare) "Android share" else "Manual paste",
                    captureTime = now,
                    eventTime = now,
                    origin = if (fromShare) ParseOrigin.SHARE else ParseOrigin.PASTE,
                    sourceIsKnown = true,
                    defaultCurrency = uiState.value.config.currency,
                ),
            )
        ) {
            is IngestionResult.Saved -> effectChannel.send(
                UiEffect.NavigateToTransaction(
                    transactionId = result.transactionId,
                    review = result.destination == Destination.REVIEW,
                ),
            )
            IngestionResult.Duplicate -> effectChannel.send(UiEffect.Message("This transaction is already saved."))
            IngestionResult.FailedSafely -> effectChannel.send(UiEffect.Message("That alert could not be safely imported."))
            is IngestionResult.Ignored -> effectChannel.send(
                UiEffect.Message(
                    when (result.action) {
                        ParseAction.IGNORE_SECURITY -> "Security and OTP messages are never captured."
                        ParseAction.IGNORE_PROMO -> "Promotional text was ignored."
                        ParseAction.IGNORE_INFO -> "No completed transaction was found."
                        else -> "Add a little more transaction detail and try again."
                    },
                ),
            )
        }
    }

    fun createManualTransaction(submission: ManualTransactionSubmission) = launchAction {
        when (
            val result = container.transactionIngestor.createManualTransaction(
                amountMinor = submission.amountMinor,
                direction = Direction.valueOf(submission.direction),
                eventTime = Instant.ofEpochMilli(submission.eventTimeEpochMs),
                method = TransactionMethod.valueOf(submission.method),
                counterparty = submission.counterparty,
                category = submission.category,
                notes = submission.notes,
                currency = uiState.value.config.currency,
            )
        ) {
            is IngestionResult.Saved -> effectChannel.send(
                UiEffect.NavigateToTransaction(result.transactionId, review = false),
            )
            IngestionResult.Duplicate -> effectChannel.send(UiEffect.Message("This transaction is already saved."))
            else -> effectChannel.send(UiEffect.Message("The transaction could not be saved safely."))
        }
    }

    fun loadReviewSource(transactionId: String) {
        if (transactionId in runtime.value.reviewSourceById) return
        viewModelScope.launch(Dispatchers.IO) {
            val source = container.transactionIngestor.reviewPayload(transactionId)
            runtime.update {
                it.copy(reviewSourceById = it.reviewSourceById + (transactionId to source))
            }
        }
    }

    fun clearReviewSource(transactionId: String) {
        runtime.update { it.copy(reviewSourceById = it.reviewSourceById - transactionId) }
    }

    fun acceptReview(transactionId: String, submission: ReviewSubmission) = launchAction {
        val accepted = container.transactionIngestor.acceptReview(
            transactionId = transactionId,
            amountMinor = submission.amountMinor,
            direction = Direction.valueOf(submission.direction),
            method = TransactionMethod.valueOf(submission.method),
            counterparty = submission.counterparty,
            category = submission.category,
            notes = submission.notes,
        )
        if (accepted) {
            clearReviewSource(transactionId)
            effectChannel.send(UiEffect.Message("Approved and queued for sync."))
            effectChannel.send(UiEffect.CloseCurrent)
        } else {
            effectChannel.send(UiEffect.Message("This item is no longer available for review."))
        }
    }

    fun discardReview(transactionId: String) = launchAction {
        if (container.transactionIngestor.discardReview(transactionId)) {
            clearReviewSource(transactionId)
            effectChannel.send(UiEffect.Message("Review item discarded."))
            effectChannel.send(UiEffect.CloseCurrent)
        }
    }

    fun saveTransactionEdits(transactionId: String, edits: TransactionEdits) = launchAction {
        val current = database.transactionDao().byId(transactionId) ?: return@launchAction
        if (current.status == "SYNCING") {
            effectChannel.send(UiEffect.Message("Wait for the current sync to finish before editing."))
            return@launchAction
        }
        val changed = buildList {
            if (current.counterparty != edits.counterparty) add("counterparty")
            if (current.category != edits.category) add("category")
            if (current.notes != edits.notes) add("notes")
        }
        if (changed.isEmpty()) return@launchAction
        if (current.status == "SYNCED") {
            val queued = container.transactionIngestor.correctSyncedTransaction(
                transactionId = transactionId,
                amountMinor = requireNotNull(current.amountMinor),
                direction = Direction.valueOf(requireNotNull(current.direction)),
                method = TransactionMethod.valueOf(current.method),
                counterparty = edits.counterparty,
                category = edits.category,
                notes = edits.notes,
            )
            if (!queued) {
                effectChannel.send(UiEffect.Message("The synced row could not be queued for correction."))
                return@launchAction
            }
        } else {
            database.withTransaction {
                val latest = database.transactionDao().byId(transactionId)
                    ?: return@withTransaction
                database.transactionDao().updateEditableFields(
                    id = transactionId,
                    counterparty = edits.counterparty,
                    category = edits.category,
                    notes = edits.notes,
                )
                database.correctionAuditDao().insert(
                    CorrectionAuditEntity(
                        transactionId = transactionId,
                        changedFieldNames = changed.joinToString(","),
                        changedAtEpochMs = System.currentTimeMillis(),
                    ),
                )
                if (latest.status != "REVIEW") {
                    database.syncJobDao().upsert(
                        SyncJobEntity(
                            transactionId = transactionId,
                            state = "QUEUED",
                            remoteRange = latest.remoteRange,
                        ),
                    )
                    database.transactionDao().updateStatus(transactionId, "QUEUED")
                }
            }
            if (current.status != "REVIEW") container.syncScheduler.enqueue()
        }
        effectChannel.send(UiEffect.Message("Changes saved."))
    }

    fun deleteLocalTransaction(transactionId: String) = launchAction {
        database.transactionDao().delete(transactionId)
        effectChannel.send(UiEffect.Message("Local record removed. Your Sheet was not changed."))
        effectChannel.send(UiEffect.CloseCurrent)
    }

    fun addCategoryRule(term: String, category: String) = launchAction {
        val normalized = term.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
        if (normalized.length < 2 || category.isBlank()) return@launchAction
        val priority = (uiState.value.categoryRules.maxOfOrNull { it.priority } ?: 0) + 1
        database.categoryRuleDao().upsert(
            CategoryRuleEntity(
                ruleId = UUID.randomUUID().toString(),
                matchType = "CONTAINS",
                normalizedTerm = normalized.take(80),
                category = category.trim().take(60),
                priority = priority,
            ),
        )
    }

    fun deleteCategoryRule(ruleId: String) = launchAction {
        database.categoryRuleDao().delete(ruleId)
    }

    fun createWorkbook(title: String) {
        runtime.update { it.copy(pendingGoogleAction = PendingGoogleAction.Create(title)) }
        launchAction { handleWorkbookOutcome(container.workbookSetupCoordinator.createWorkbook(title)) }
    }

    fun linkWorkbook(idOrUrl: String) {
        runtime.update { it.copy(pendingGoogleAction = PendingGoogleAction.Link(idOrUrl)) }
        launchAction { handleWorkbookOutcome(container.workbookSetupCoordinator.linkWorkbook(idOrUrl)) }
    }

    fun reconnectGoogle() {
        runtime.update { it.copy(pendingGoogleAction = PendingGoogleAction.Reconnect) }
        launchAction { handleWorkbookOutcome(container.workbookSetupCoordinator.reconnect()) }
    }

    fun disconnectGoogle() {
        runtime.update { it.copy(pendingGoogleAction = PendingGoogleAction.Disconnect) }
        launchAction { handleDisconnectOutcome(container.workbookSetupCoordinator.disconnect()) }
    }

    fun completeGoogleAuthorization(data: Intent?) = launchAction {
        if (runtime.value.pendingGoogleAction == PendingGoogleAction.Disconnect) {
            handleDisconnectOutcome(container.workbookSetupCoordinator.completeDisconnectAuthorization(data))
            return@launchAction
        }
        val outcome = when (val action = runtime.value.pendingGoogleAction) {
            is PendingGoogleAction.Create -> container.workbookSetupCoordinator.completeCreateAuthorization(data, action.title)
            is PendingGoogleAction.Link -> container.workbookSetupCoordinator.completeLinkAuthorization(data, action.idOrUrl)
            PendingGoogleAction.Reconnect -> container.workbookSetupCoordinator.completeReconnectAuthorization(data)
            PendingGoogleAction.Disconnect -> error("handled above")
            null -> {
                effectChannel.send(UiEffect.Message("Google setup session expired. Please try again."))
                return@launchAction
            }
        }
        handleWorkbookOutcome(outcome)
    }

    private suspend fun handleDisconnectOutcome(outcome: GoogleDisconnectOutcome) {
        when (outcome) {
            is GoogleDisconnectOutcome.Disconnected -> {
                runtime.update { it.copy(pendingGoogleAction = null) }
                effectChannel.send(
                    UiEffect.Message(
                        if (outcome.revocationConfirmed) "Google access revoked. Your Sheet remains yours."
                        else "Google ledger disconnected locally. Your Sheet remains yours.",
                    ),
                )
            }
            is GoogleDisconnectOutcome.ResolutionRequired -> {
                effectChannel.send(UiEffect.LaunchGoogleAuthorization(outcome.pendingIntent))
            }
            is GoogleDisconnectOutcome.Error -> {
                runtime.update { it.copy(pendingGoogleAction = null) }
                effectChannel.send(UiEffect.Message("Google disconnect could not finish (${outcome.code})."))
            }
        }
    }

    private suspend fun handleWorkbookOutcome(outcome: WorkbookSetupOutcome) {
        when (outcome) {
            is WorkbookSetupOutcome.Ready -> {
                runtime.update { it.copy(pendingGoogleAction = null) }
                effectChannel.send(UiEffect.Message("Private Google ledger is ready."))
            }
            is WorkbookSetupOutcome.ResolutionRequired -> {
                effectChannel.send(UiEffect.LaunchGoogleAuthorization(outcome.pendingIntent))
            }
            is WorkbookSetupOutcome.Error -> {
                runtime.update { it.copy(pendingGoogleAction = null) }
                effectChannel.send(UiEffect.Message(workbookErrorMessage(outcome.code)))
            }
        }
    }

    fun syncNow() {
        container.syncScheduler.enqueue()
        viewModelScope.launch { effectChannel.send(UiEffect.Message("Sync queued.")) }
    }

    fun openSpreadsheet() {
        uiState.value.config.spreadsheetId?.let { id ->
            viewModelScope.launch { effectChannel.send(UiEffect.OpenSpreadsheet(id)) }
        }
    }

    fun eraseLocalData() = launchAction {
        container.localDataEraser.erase()
        database.configDao().upsert(AppConfigEntity())
        runtime.update { RuntimeUiState(notificationAccessGranted = it.notificationAccessGranted) }
        effectChannel.send(UiEffect.Message("All local TxnSheet data was erased. Your Sheet was not changed."))
    }

    private fun launchAction(block: suspend () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            runtime.update { it.copy(actionInProgress = true) }
            try {
                block()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: IllegalArgumentException) {
                effectChannel.send(UiEffect.Message("Please check the entered details."))
            } catch (_: RuntimeException) {
                effectChannel.send(UiEffect.Message("That action could not be completed safely."))
            } finally {
                runtime.update { it.copy(actionInProgress = false) }
            }
        }
    }

    private fun workbookErrorMessage(code: String): String = when {
        code == "GOOGLE_AUTH_CANCELLED" -> "Google connection was cancelled."
        code == "SPREADSHEET_ID_INVALID" -> "Enter a valid Google Sheets URL or spreadsheet ID."
        code.startsWith("SCHEMA_") -> "That spreadsheet does not match the protected TxnSheet schema."
        code == "AUTH_REQUIRED" -> "Google permission needs to be granted again."
        code.contains("NETWORK") || code.contains("HTTP_5") -> "Network unavailable. Your local ledger is safe; try again later."
        else -> "Google Sheet setup could not finish ($code)."
    }

    private data class RuntimeUiState(
        val notificationAccessGranted: Boolean = false,
        val actionInProgress: Boolean = false,
        val reviewSourceById: Map<String, String?> = emptyMap(),
        val pendingGoogleAction: PendingGoogleAction? = null,
    )

    private sealed interface PendingGoogleAction {
        data class Create(val title: String) : PendingGoogleAction
        data class Link(val idOrUrl: String) : PendingGoogleAction
        data object Reconnect : PendingGoogleAction
        data object Disconnect : PendingGoogleAction
    }

    private data class PersistentUiState(
        val config: AppConfigEntity,
        val transactions: List<app.txnsheet.personal.data.local.TransactionEntity>,
        val sources: List<app.txnsheet.personal.data.local.SourceAppEntity>,
        val categoryRules: List<CategoryRuleEntity>,
        val diagnostics: List<app.txnsheet.personal.data.local.DiagnosticEventEntity>,
    )
}
