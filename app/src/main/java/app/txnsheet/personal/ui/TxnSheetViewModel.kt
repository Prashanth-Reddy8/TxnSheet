package app.txnsheet.personal.ui

import android.app.Application
import android.net.Uri
import androidx.room.withTransaction
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.txnsheet.personal.TxnSheetApplication
import app.txnsheet.personal.data.local.AppConfigEntity
import app.txnsheet.personal.data.local.CategoryRuleEntity
import app.txnsheet.personal.data.local.CorrectionAuditEntity
import app.txnsheet.personal.data.local.FinanceData
import app.txnsheet.personal.data.local.FinancePreferencesEntity
import app.txnsheet.personal.data.local.BudgetEntity
import app.txnsheet.personal.data.local.DebtEntity
import app.txnsheet.personal.data.local.GoalEntity
import app.txnsheet.personal.data.local.TransactionAnnotationEntity
import app.txnsheet.personal.data.repository.WorkbookPreview
import app.txnsheet.personal.data.repository.LocalWorkbookReader
import app.txnsheet.personal.data.repository.Destination
import app.txnsheet.personal.data.repository.IngestionResult
import app.txnsheet.personal.domain.Direction
import app.txnsheet.personal.domain.TransactionMethod
import app.txnsheet.personal.parsing.ParseAction
import app.txnsheet.personal.parsing.ParseContext
import app.txnsheet.personal.parsing.ParseOrigin
import app.txnsheet.personal.ui.screens.ReviewSubmission
import app.txnsheet.personal.ui.screens.ManualTransactionSubmission
import app.txnsheet.personal.ui.screens.TransactionEdits
import java.time.Instant
import java.time.YearMonth
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

    private val planState = combine(
        database.financeDao().observePreferences(),
        database.financeDao().observeBudgets(),
        database.financeDao().observeDebts(),
        database.financeDao().observeGoals(),
        database.financeDao().observeAnnotations(),
    ) { preferences, budgets, debts, goals, annotations ->
        FinanceData(preferences ?: FinancePreferencesEntity(), budgets, debts, goals, annotations)
    }
    private val financeState = combine(planState, database.financeDao().observeWorkbook()) { plan, workbook ->
        plan.copy(workbook = workbook)
    }

    val uiState = combine(persistentState, runtime, financeState) { persistent, volatile, finance ->
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
            finance = finance,
            selectedMonth = volatile.selectedMonth,
            workbookPreview = volatile.workbookPreview,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TxnSheetUiState())

    init {
        viewModelScope.launch(Dispatchers.IO) { container.initializeLocalState() }
    }

    fun refreshSystemReadiness(notificationAccessGranted: Boolean) {
        runtime.update { it.copy(notificationAccessGranted = notificationAccessGranted) }
    }

    fun selectMonth(month: String) {
        runCatching { YearMonth.parse(month) }.getOrNull() ?: return
        runtime.update { it.copy(selectedMonth = month) }
    }

    fun saveBudget(value: BudgetEntity) = launchAction {
        database.financeDao().upsert(value)
        effectChannel.send(UiEffect.Message("Budget saved for ${value.month}."))
    }
    fun saveDebt(value: DebtEntity) = launchAction { database.financeDao().upsert(value) }
    fun deleteDebt(id: String) = launchAction { database.financeDao().deleteDebt(id) }
    fun saveGoal(value: GoalEntity) = launchAction { database.financeDao().upsert(value) }
    fun deleteGoal(id: String) = launchAction { database.financeDao().deleteGoal(id) }
    fun savePreferences(value: FinancePreferencesEntity) = launchAction { database.financeDao().upsert(value) }
    fun saveAnnotation(value: TransactionAnnotationEntity) = launchAction {
        if (database.transactionDao().byId(value.transactionId) != null) database.financeDao().upsert(value)
    }

    fun previewWorkbook(uri: Uri) = launchAction {
        val resolver = getApplication<Application>().contentResolver
        val preview = resolver.openInputStream(uri)?.use { LocalWorkbookReader.read(it) }
            ?: throw IllegalArgumentException("File could not be opened")
        runtime.update { it.copy(workbookPreview = preview) }
    }

    fun dismissWorkbookPreview() { runtime.update { it.copy(workbookPreview = null) } }

    fun importWorkbook() = launchAction {
        val preview = runtime.value.workbookPreview ?: return@launchAction
        database.withTransaction {
            database.financeDao().upsert(preview.snapshot)
            // Only insert absent records. Re-import never overwrites corrections or plans.
            preview.transactions.forEach { row ->
                if (!database.transactionDao().fingerprintExists(row.eventFingerprint)) database.transactionDao().insert(row)
            }
            preview.budgets.forEach { database.financeDao().insertIfAbsent(it) }
            preview.debts.forEach { database.financeDao().insertIfAbsent(it) }
            preview.goals.forEach { database.financeDao().insertIfAbsent(it) }
            preview.annotations.forEach { database.financeDao().insertIfAbsent(it) }
        }
        dismissWorkbookPreview()
        effectChannel.send(UiEffect.Message("Workbook imported. Original tabs are available in Workbook data."))
    }

    fun rememberMerchant(transactionId: String) = launchAction {
        val transaction = database.transactionDao().byId(transactionId) ?: return@launchAction
        val merchant = transaction.counterparty?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.length >= 2 } ?: return@launchAction
        database.categoryRuleDao().upsert(CategoryRuleEntity(
            ruleId = "merchant:" + UUID.nameUUIDFromBytes(merchant.toByteArray()),
            matchType = "EXACT", normalizedTerm = merchant, category = transaction.category,
            priority = (uiState.value.categoryRules.maxOfOrNull { it.priority } ?: 0) + 1,
        ))
        effectChannel.send(UiEffect.Message("Future payments to this merchant will use ${transaction.category}."))
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
            effectChannel.send(UiEffect.Message("Approved and saved on this phone."))
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
        require(edits.amountMinor in 1..999_999_999_999L && edits.direction in setOf("DEBIT", "CREDIT"))
        require(edits.method in TransactionMethod.entries.map { it.name })
        val current = database.transactionDao().byId(transactionId) ?: return@launchAction
        val changed = buildList {
            if (current.amountMinor != edits.amountMinor) add("amount")
            if (current.direction != edits.direction) add("direction")
            if (current.method != edits.method) add("method")
            if (current.counterparty != edits.counterparty) add("counterparty")
            if (current.category != edits.category) add("category")
            if (current.notes != edits.notes) add("notes")
        }
        if (changed.isEmpty()) return@launchAction
        database.withTransaction {
            database.transactionDao().update(current.copy(
                counterparty = edits.counterparty,
                category = edits.category,
                notes = edits.notes,
                amountMinor = edits.amountMinor,
                direction = edits.direction,
                method = edits.method,
            ))
            database.correctionAuditDao().insert(
                CorrectionAuditEntity(
                    transactionId = transactionId,
                    changedFieldNames = changed.joinToString(","),
                    changedAtEpochMs = System.currentTimeMillis(),
                ),
            )
        }
        effectChannel.send(UiEffect.Message("Changes saved."))
    }

    fun deleteLocalTransaction(transactionId: String) = launchAction {
        database.transactionDao().delete(transactionId)
        effectChannel.send(UiEffect.Message("Transaction removed from this phone."))
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

    fun eraseLocalData() = launchAction {
        container.localDataEraser.erase()
        database.configDao().upsert(AppConfigEntity())
        runtime.update { RuntimeUiState(notificationAccessGranted = it.notificationAccessGranted) }
        effectChannel.send(UiEffect.Message("All local TxnSheet data was erased."))
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
            } catch (_: Exception) {
                effectChannel.send(UiEffect.Message("Could not read this file. Choose a valid .xlsx workbook saved on your phone."))
            } finally {
                runtime.update { it.copy(actionInProgress = false) }
            }
        }
    }

    private data class RuntimeUiState(
        val notificationAccessGranted: Boolean = false,
        val actionInProgress: Boolean = false,
        val reviewSourceById: Map<String, String?> = emptyMap(),
        val selectedMonth: String = YearMonth.now().toString(),
        val workbookPreview: WorkbookPreview? = null,
    )

    private data class PersistentUiState(
        val config: AppConfigEntity,
        val transactions: List<app.txnsheet.personal.data.local.TransactionEntity>,
        val sources: List<app.txnsheet.personal.data.local.SourceAppEntity>,
        val categoryRules: List<CategoryRuleEntity>,
        val diagnostics: List<app.txnsheet.personal.data.local.DiagnosticEventEntity>,
    )
}
