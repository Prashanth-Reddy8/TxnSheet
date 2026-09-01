package app.txnsheet.personal.data.repository

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import app.txnsheet.personal.data.local.AppConfigEntity
import app.txnsheet.personal.data.local.CorrectionAuditEntity
import app.txnsheet.personal.data.local.ReviewItemEntity
import app.txnsheet.personal.data.local.SyncJobEntity
import app.txnsheet.personal.data.local.TransactionEntity
import app.txnsheet.personal.data.local.TxnSheetDatabase
import app.txnsheet.personal.diagnostics.PrivacySafeDiagnostics
import app.txnsheet.personal.domain.Direction
import app.txnsheet.personal.domain.TransactionDraft
import app.txnsheet.personal.domain.TransactionMethod
import app.txnsheet.personal.parsing.FingerprintFactory
import app.txnsheet.personal.parsing.ParseAction
import app.txnsheet.personal.parsing.ParseContext
import app.txnsheet.personal.parsing.ParseIssue
import app.txnsheet.personal.parsing.TextNormalizer
import app.txnsheet.personal.parsing.TransactionParser
import app.txnsheet.personal.security.EncryptedPayload
import app.txnsheet.personal.security.ReviewPayloadCrypto
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.UnrecoverableKeyException
import java.time.Clock
import java.time.Instant
import java.util.Locale
import java.util.UUID
import android.security.keystore.KeyPermanentlyInvalidatedException
import kotlinx.coroutines.CancellationException

sealed interface IngestionResult {
    data class Saved(val transactionId: String, val destination: Destination) : IngestionResult
    data class Ignored(val action: ParseAction, val issues: Set<ParseIssue>) : IngestionResult
    data object Duplicate : IngestionResult
    data object FailedSafely : IngestionResult
}

enum class Destination { SYNC_QUEUE, REVIEW }

/**
 * The sole entry point from notification/share capture into durable ledger state.
 * Raw text is only retained for review and is encrypted before any database write.
 */
class TransactionIngestor(
    private val database: TxnSheetDatabase,
    private val reviewCrypto: ReviewPayloadCrypto,
    private val diagnostics: PrivacySafeDiagnostics,
    private val categoryRules: CategoryRuleResolver,
    private val syncWorkEnqueuer: SyncWorkEnqueuer = SyncWorkEnqueuer.NONE,
    private val clock: Clock = Clock.systemUTC(),
) {
    suspend fun createManualTransaction(
        amountMinor: Long,
        direction: Direction,
        eventTime: Instant,
        method: TransactionMethod,
        counterparty: String?,
        category: String,
        notes: String,
        currency: String = "INR",
    ): IngestionResult {
        require(amountMinor in 1..MAX_AMOUNT_MINOR) { "amountMinor is outside the supported range" }
        require(currency.matches(Regex("[A-Za-z]{3}"))) { "currency must be a three-letter code" }
        val transactionId = UUID.randomUUID().toString()
        val safeCounterparty = counterparty?.trim()?.take(96)?.takeIf(String::isNotBlank)
        val selectedCategory = category.trim().take(64).ifBlank { "Uncategorized" }
        val resolvedCategory = if (selectedCategory == "Uncategorized") {
            categoryRules.resolve(safeCounterparty, selectedCategory)
        } else {
            selectedCategory
        }
        val capturedAt = Instant.ofEpochMilli(clock.millis())
        val transaction = TransactionEntity(
            transactionId = transactionId,
            eventTimeEpochMs = eventTime.toEpochMilli(),
            capturedTimeEpochMs = capturedAt.toEpochMilli(),
            amountMinor = amountMinor,
            currency = currency.uppercase(Locale.ROOT),
            direction = direction.name,
            method = method.name,
            counterparty = safeCounterparty,
            category = resolvedCategory,
            institution = null,
            accountLast4 = null,
            referenceId = null,
            balanceMinor = null,
            sourceApp = MANUAL_SOURCE,
            sourceLabel = "Manual entry",
            confidence = 1.0,
            parserRule = "manual_entry@1.0.0",
            notes = notes.trim().take(240),
            eventFingerprint = sha256("manual-v1|$transactionId"),
            status = STATUS_QUEUED,
        )
        return try {
            database.atomicStoreDao().insertForSync(
                transaction,
                SyncJobEntity(transactionId = transactionId, state = STATUS_QUEUED),
            )
            enqueueSyncSafely()
            IngestionResult.Saved(transactionId, Destination.SYNC_QUEUE)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: SQLiteConstraintException) {
            IngestionResult.Duplicate
        } catch (_: Exception) {
            recordSafely("MANUAL_ENTRY_FAILED", severity = "ERROR")
            IngestionResult.FailedSafely
        }
    }

    suspend fun ingest(rawText: String, context: ParseContext): IngestionResult {
        return try {
            purgeExpiredReviewPayloads()
            val result = TransactionParser.parse(rawText, context)
            val draft = result.draft
            if (draft == null) {
                val issueSuffix = result.issues
                    .map(ParseIssue::name)
                    .sorted()
                    .joinToString("_")
                    .take(120)
                recordSafely(
                    "PARSE_${result.action.name}${issueSuffix.takeIf(String::isNotBlank)?.let { "_$it" }.orEmpty()}",
                    severity = "INFO",
                )
                return IngestionResult.Ignored(result.action, result.issues)
            }

            val categorizedDraft = draft.copy(
                category = categoryRules.resolve(draft.counterparty, draft.category),
            )
            val fingerprint = fingerprint(categorizedDraft, rawText, context)
            if (database.transactionDao().fingerprintExists(fingerprint)) {
                recordSafely("DUPLICATE_SUPPRESSED", severity = "INFO")
                return IngestionResult.Duplicate
            }

            val transactionId = UUID.randomUUID().toString()
            val status = when (result.action) {
                ParseAction.AUTO_SYNC -> STATUS_QUEUED
                ParseAction.REVIEW -> STATUS_REVIEW
                else -> {
                    recordSafely("PARSE_ROUTE_INVALID", severity = "ERROR")
                    return IngestionResult.FailedSafely
                }
            }
            val transaction = categorizedDraft.toEntity(
                transactionId = transactionId,
                eventFingerprint = fingerprint,
                status = status,
            )

            when (result.action) {
                ParseAction.AUTO_SYNC -> {
                    database.atomicStoreDao().insertForSync(
                        transaction,
                        SyncJobEntity(transactionId = transactionId, state = STATUS_QUEUED),
                    )
                    enqueueSyncSafely()
                    IngestionResult.Saved(transactionId, Destination.SYNC_QUEUE)
                }

                ParseAction.REVIEW -> {
                    val encrypted = reviewCrypto.encrypt(rawText)
                    val retentionDays = reviewRetentionDays()
                    database.atomicStoreDao().insertForReview(
                        transaction,
                        ReviewItemEntity(
                            transactionId = transactionId,
                            encryptedPayload = encrypted.ciphertext,
                            initializationVector = encrypted.initializationVector,
                            reason = reviewReason(result.issues),
                            expiresAtEpochMs = clock.millis() + retentionDays * MILLIS_PER_DAY,
                        ),
                    )
                    IngestionResult.Saved(transactionId, Destination.REVIEW)
                }

                else -> IngestionResult.FailedSafely
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: SQLiteConstraintException) {
            recordSafely("DUPLICATE_RACE_SUPPRESSED", severity = "INFO")
            IngestionResult.Duplicate
        } catch (error: Exception) {
            if (isKeyInvalidation(error)) {
                recoverFromInvalidatedReviewKey()
                recordSafely("REVIEW_KEY_INVALIDATED", severity = "ERROR")
            } else {
                recordSafely("INGESTION_FAILED", severity = "ERROR")
            }
            IngestionResult.FailedSafely
        }
    }

    /** Decrypts on demand; the plaintext is never cached or logged by this repository. */
    suspend fun reviewPayload(transactionId: String): String? {
        val item = database.reviewDao().byId(transactionId) ?: return null
        if (item.expiresAtEpochMs <= clock.millis()) {
            database.reviewDao().delete(transactionId)
            recordSafely("REVIEW_PAYLOAD_EXPIRED", severity = "INFO")
            return null
        }
        return try {
            reviewCrypto.decrypt(
                EncryptedPayload(item.encryptedPayload, item.initializationVector),
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            if (isKeyInvalidation(error)) {
                recoverFromInvalidatedReviewKey()
                recordSafely("REVIEW_KEY_INVALIDATED", severity = "ERROR")
            } else {
                database.reviewDao().delete(transactionId)
                recordSafely("REVIEW_PAYLOAD_UNREADABLE", severity = "ERROR")
            }
            null
        }
    }

    suspend fun acceptReview(
        transactionId: String,
        amountMinor: Long,
        direction: Direction,
        method: TransactionMethod,
        counterparty: String?,
        category: String,
        notes: String,
    ): Boolean {
        require(amountMinor in 1..MAX_AMOUNT_MINOR) { "amountMinor is outside the supported range" }
        val safeCounterparty = counterparty?.trim()?.take(96)?.takeIf(String::isNotBlank)
        val safeCategory = category.trim().take(64).ifBlank { "Uncategorized" }
        val safeNotes = notes.trim().take(240)

        val accepted = database.withTransaction {
            val current = database.transactionDao().byId(transactionId) ?: return@withTransaction false
            if (current.status != STATUS_REVIEW) return@withTransaction false
            val changedFields = buildList {
                if (current.amountMinor != amountMinor) add("amount")
                if (current.direction != direction.name) add("direction")
                if (current.method != method.name) add("method")
                if (current.counterparty != safeCounterparty) add("counterparty")
                if (current.category != safeCategory) add("category")
                if (current.notes != safeNotes) add("notes")
            }
            database.transactionDao().update(
                current.copy(
                    amountMinor = amountMinor,
                    direction = direction.name,
                    method = method.name,
                    counterparty = safeCounterparty,
                    category = safeCategory,
                    notes = safeNotes,
                    status = STATUS_QUEUED,
                ),
            )
            database.reviewDao().delete(transactionId)
            database.syncJobDao().upsert(
                SyncJobEntity(transactionId = transactionId, state = STATUS_QUEUED),
            )
            if (changedFields.isNotEmpty()) {
                database.correctionAuditDao().insert(
                    CorrectionAuditEntity(
                        transactionId = transactionId,
                        changedFieldNames = changedFields.joinToString(","),
                        changedAtEpochMs = clock.millis(),
                    ),
                )
            }
            true
        }
        if (accepted) enqueueSyncSafely()
        return accepted
    }

    suspend fun discardReview(transactionId: String): Boolean = database.withTransaction {
        val current = database.transactionDao().byId(transactionId) ?: return@withTransaction false
        if (current.status != STATUS_REVIEW) return@withTransaction false
        database.transactionDao().delete(transactionId)
        true
    }

    /** Queues an in-place correction; the worker verifies the UUID before updating the remote row. */
    suspend fun correctSyncedTransaction(
        transactionId: String,
        amountMinor: Long,
        direction: Direction,
        method: TransactionMethod,
        counterparty: String?,
        category: String,
        notes: String,
    ): Boolean {
        require(amountMinor in 1..MAX_AMOUNT_MINOR) { "amountMinor is outside the supported range" }
        val safeCounterparty = counterparty?.trim()?.take(96)?.takeIf(String::isNotBlank)
        val safeCategory = category.trim().take(64).ifBlank { "Uncategorized" }
        val safeNotes = notes.trim().take(240)
        val queued = database.withTransaction {
            val current = database.transactionDao().byId(transactionId) ?: return@withTransaction false
            if (current.status != STATUS_SYNCED || current.remoteRange.isNullOrBlank()) {
                return@withTransaction false
            }
            val changedFields = buildList {
                if (current.amountMinor != amountMinor) add("amount")
                if (current.direction != direction.name) add("direction")
                if (current.method != method.name) add("method")
                if (current.counterparty != safeCounterparty) add("counterparty")
                if (current.category != safeCategory) add("category")
                if (current.notes != safeNotes) add("notes")
            }
            if (changedFields.isEmpty()) return@withTransaction false
            database.transactionDao().update(
                current.copy(
                    amountMinor = amountMinor,
                    direction = direction.name,
                    method = method.name,
                    counterparty = safeCounterparty,
                    category = safeCategory,
                    notes = safeNotes,
                    status = STATUS_QUEUED,
                ),
            )
            database.syncJobDao().upsert(
                SyncJobEntity(
                    transactionId = transactionId,
                    state = STATUS_QUEUED,
                    remoteRange = current.remoteRange,
                ),
            )
            database.correctionAuditDao().insert(
                CorrectionAuditEntity(
                    transactionId = transactionId,
                    changedFieldNames = changedFields.joinToString(","),
                    changedAtEpochMs = clock.millis(),
                ),
            )
            true
        }
        if (queued) enqueueSyncSafely()
        return queued
    }

    suspend fun purgeExpiredReviewPayloads(): Int =
        database.reviewDao().purgeExpired(clock.millis())

    suspend fun validateReviewEncryptionState() {
        if (database.reviewDao().count() == 0) return
        if (!reviewCrypto.hasKey()) {
            recoverFromInvalidatedReviewKey()
            recordSafely("REVIEW_KEY_MISSING", severity = "ERROR")
            return
        }
        try {
            reviewCrypto.verifyUsable()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            if (isKeyInvalidation(error)) {
                recoverFromInvalidatedReviewKey()
                recordSafely("REVIEW_KEY_INVALIDATED", severity = "ERROR")
            } else {
                recordSafely("REVIEW_KEY_CHECK_FAILED", severity = "ERROR")
            }
        }
    }

    private suspend fun reviewRetentionDays(): Long =
        (database.configDao().get() ?: AppConfigEntity()).reviewRetentionDays
            .coerceIn(MIN_REVIEW_RETENTION_DAYS, MAX_REVIEW_RETENTION_DAYS)
            .toLong()

    private fun fingerprint(
        draft: TransactionDraft,
        rawText: String,
        context: ParseContext,
    ): String {
        return if (draft.amountMinor != null && draft.direction != null) {
            FingerprintFactory.create(draft, context.sourcePackage, rawText, context.captureTime)
        } else {
            val contentHash = sha256(TextNormalizer.normalize(rawText))
            val captureMinute = Math.floorDiv(context.captureTime.epochSecond, 60L)
            sha256(
                listOf(
                    "review-v1",
                    context.sourcePackage.lowercase(Locale.ROOT),
                    contentHash,
                    captureMinute.toString(),
                ).joinToString("|"),
            )
        }
    }

    private fun TransactionDraft.toEntity(
        transactionId: String,
        eventFingerprint: String,
        status: String,
    ) = TransactionEntity(
        transactionId = transactionId,
        eventTimeEpochMs = eventTime.toEpochMilli(),
        capturedTimeEpochMs = capturedTime.toEpochMilli(),
        amountMinor = amountMinor,
        currency = currency,
        direction = direction?.name,
        method = method.name,
        counterparty = counterparty,
        category = category,
        institution = institution,
        accountLast4 = accountLast4,
        referenceId = referenceId,
        balanceMinor = balanceMinor,
        sourceApp = sourcePackage,
        sourceLabel = sourceLabel,
        confidence = confidence,
        parserRule = parserRule,
        notes = "",
        eventFingerprint = eventFingerprint,
        status = status,
    )

    private fun reviewReason(issues: Set<ParseIssue>): String = issues
        .map(ParseIssue::name)
        .sorted()
        .joinToString(",")
        .ifBlank { "CONFIRM_DETAILS" }
        .take(160)

    private fun enqueueSyncSafely() {
        try {
            syncWorkEnqueuer.enqueue()
        } catch (_: RuntimeException) {
            // The durable QUEUED job remains in Room and can be picked up by a later app start.
        }
    }

    private suspend fun recordSafely(code: String, severity: String) {
        try {
            diagnostics.record(code = code, component = "ingestion", severity = severity)
        } catch (_: RuntimeException) {
            // Diagnostics must never break or weaken transaction capture.
        }
    }

    private suspend fun recoverFromInvalidatedReviewKey() {
        try {
            database.reviewDao().deleteAll()
            reviewCrypto.deleteKey()
        } catch (_: Exception) {
            // A later startup retries cleanup; no plaintext is exposed in either case.
        }
    }

    private fun isKeyInvalidation(error: Throwable): Boolean {
        var cause: Throwable? = error
        while (cause != null) {
            if (cause is KeyPermanentlyInvalidatedException || cause is UnrecoverableKeyException) {
                return true
            }
            cause = cause.cause
        }
        return false
    }

    private fun sha256(value: String): String = MessageDigest
        .getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private companion object {
        const val STATUS_QUEUED = "QUEUED"
        const val STATUS_REVIEW = "REVIEW"
        const val STATUS_SYNCED = "SYNCED"
        const val MANUAL_SOURCE = "manual"
        const val MILLIS_PER_DAY = 86_400_000L
        const val MIN_REVIEW_RETENTION_DAYS = 1
        const val MAX_REVIEW_RETENTION_DAYS = 7
        const val MAX_AMOUNT_MINOR = 100_000_000_000L
    }
}
