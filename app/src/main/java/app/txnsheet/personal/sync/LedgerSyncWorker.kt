package app.txnsheet.personal.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.room.withTransaction
import app.txnsheet.personal.data.local.SyncJobEntity
import app.txnsheet.personal.data.local.TxnSheetDatabase
import app.txnsheet.personal.data.remote.AuthorizationOutcome
import app.txnsheet.personal.data.remote.EphemeralAccessToken
import app.txnsheet.personal.data.remote.GoogleSheetsHttpGateway
import app.txnsheet.personal.data.remote.PlayServicesGoogleAuthorizationGateway
import app.txnsheet.personal.data.remote.SchemaValidationResult
import app.txnsheet.personal.data.remote.SheetsGateway
import app.txnsheet.personal.data.remote.SheetsHttpException
import app.txnsheet.personal.data.remote.SheetsProtocolException
import kotlinx.coroutines.CancellationException
import java.io.IOException
import kotlin.math.min

/**
 * Serial, at-least-once ledger writer. Each append is guarded by a remote UUID lookup, making a
 * process death or ambiguous response safe to replay without creating another ledger row.
 */
class LedgerSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    private val database = TxnSheetDatabase.create(appContext)
    private val authorization = PlayServicesGoogleAuthorizationGateway(appContext)
    private val sheets: SheetsGateway = GoogleSheetsHttpGateway(SHARED_HTTP_CLIENT)
    private val scheduler = LedgerSyncScheduler(appContext)

    override suspend fun doWork(): Result {
        val now = System.currentTimeMillis()
        val jobs = database.syncJobDao().ready(now, MAX_WRITES_PER_RUN)
        if (jobs.isEmpty()) return Result.success()

        val config = database.configDao().get()
        val spreadsheetId = config?.spreadsheetId
        if (config == null || spreadsheetId.isNullOrBlank()) {
            jobs.forEach { markBlocked(it, SyncStatus.SHEET_REQUIRED, "SHEET_NOT_CONFIGURED") }
            return Result.success()
        }

        val authorized = when (val outcome = authorization.authorizeSilently()) {
            is AuthorizationOutcome.Authorized -> outcome
            is AuthorizationOutcome.ResolutionRequired -> {
                jobs.forEach { markBlocked(it, SyncStatus.AUTH_REQUIRED, "AUTH_RESOLUTION_REQUIRED") }
                return Result.success()
            }
            is AuthorizationOutcome.Unavailable -> {
                if (outcome.isTransient) {
                    scheduleBatchRetry(jobs, "AUTH_TRANSIENT")
                } else {
                    jobs.forEach { markBlocked(it, SyncStatus.AUTH_REQUIRED, outcome.code) }
                }
                return Result.success()
            }
        }

        try {
            when (val schema = sheets.validateSchema(authorized.token, spreadsheetId)) {
                SchemaValidationResult.Valid -> Unit
                is SchemaValidationResult.Invalid -> {
                    jobs.forEach {
                        markBlocked(
                            it,
                            SyncStatus.SCHEMA_ERROR,
                            "SCHEMA_${schema.reason.name}",
                        )
                    }
                    return Result.success()
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: SheetsHttpException) {
            handleBatchHttpFailure(jobs, authorized.token, error)
            return Result.success()
        } catch (_: IOException) {
            scheduleBatchRetry(jobs, "NETWORK_IO")
            return Result.success()
        } catch (_: SheetsProtocolException) {
            jobs.forEach { markBlocked(it, SyncStatus.SCHEMA_ERROR, "SCHEMA_PROTOCOL") }
            return Result.success()
        } catch (_: RuntimeException) {
            scheduleBatchRetry(jobs, "SYNC_GATEWAY_UNAVAILABLE")
            return Result.success()
        }

        var stopForTransientFailure = false
        for (job in jobs) {
            if (stopForTransientFailure) break
            val transaction = database.transactionDao().byId(job.transactionId)
            if (transaction == null) {
                database.syncJobDao().updateState(
                    id = job.transactionId,
                    state = SyncStatus.FAILED,
                    attempts = job.attemptCount + 1,
                    nextAttempt = null,
                    errorCode = "LOCAL_TRANSACTION_MISSING",
                    remoteRange = job.remoteRange,
                )
                continue
            }
            if (transaction.amountMinor == null || transaction.direction == null) {
                database.syncJobDao().updateState(
                    id = job.transactionId,
                    state = SyncStatus.FAILED,
                    attempts = job.attemptCount + 1,
                    nextAttempt = null,
                    errorCode = "LOCAL_TRANSACTION_INCOMPLETE",
                    remoteRange = job.remoteRange,
                )
                database.transactionDao().updateStatus(job.transactionId, SyncStatus.REVIEW)
                continue
            }

            val activeJob = job.copy(
                state = SyncStatus.SYNCING,
                attemptCount = job.attemptCount + 1,
                nextAttemptEpochMs = null,
                errorCode = null,
            )
            database.withTransaction {
                database.syncJobDao().updateState(
                    id = activeJob.transactionId,
                    state = activeJob.state,
                    attempts = activeJob.attemptCount,
                    nextAttempt = null,
                    errorCode = null,
                    remoteRange = activeJob.remoteRange,
                )
                database.transactionDao().updateStatus(activeJob.transactionId, SyncStatus.SYNCING)
            }

            try {
                val remote = sheets.findTransactionByUuid(
                    authorized.token,
                    spreadsheetId,
                    transaction.transactionId,
                )
                val range = when {
                    remote == null -> sheets.appendTransaction(
                        authorized.token,
                        spreadsheetId,
                        transaction,
                        config.timezone,
                    ).updatedRange

                    transaction.remoteRange != null || job.remoteRange != null ->
                        sheets.updateCorrection(
                            authorized.token,
                            spreadsheetId,
                            transaction,
                            config.timezone,
                            remote,
                        ).updatedRange

                    else -> remote.range
                }
                markSynced(activeJob, range)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: SheetsHttpException) {
                when (classifyHttp(error)) {
                    FailureKind.AUTH -> {
                        authorization.clearCachedToken(authorized.token)
                        markBlocked(activeJob, SyncStatus.AUTH_REQUIRED, safeHttpCode(error))
                        jobs.dropWhile { it.transactionId != job.transactionId }
                            .drop(1)
                            .forEach {
                                markBlocked(it, SyncStatus.AUTH_REQUIRED, "AUTH_SESSION_INVALID")
                            }
                        return Result.success()
                    }
                    FailureKind.TRANSIENT -> {
                        scheduleRetry(activeJob, safeHttpCode(error), error.retryAfterMillis)
                        stopForTransientFailure = true
                    }
                    FailureKind.SCHEMA -> markBlocked(
                        activeJob,
                        SyncStatus.SCHEMA_ERROR,
                        safeHttpCode(error),
                    )
                    FailureKind.PERMANENT -> markBlocked(
                        activeJob,
                        SyncStatus.FAILED,
                        safeHttpCode(error),
                    )
                }
            } catch (_: IOException) {
                scheduleRetry(activeJob, "NETWORK_IO", null)
                stopForTransientFailure = true
            } catch (_: SheetsProtocolException) {
                markBlocked(activeJob, SyncStatus.FAILED, "SHEETS_PROTOCOL")
            } catch (_: IllegalArgumentException) {
                markBlocked(activeJob, SyncStatus.FAILED, "LOCAL_ROW_INVALID")
            } catch (_: RuntimeException) {
                scheduleRetry(activeJob, "SYNC_GATEWAY_UNAVAILABLE", null)
                stopForTransientFailure = true
            }
        }

        if (!stopForTransientFailure) {
            val remaining = database.syncJobDao().ready(System.currentTimeMillis(), 1)
            if (remaining.isNotEmpty()) scheduler.enqueueContinuation()
        }
        return Result.success()
    }

    private suspend fun markSynced(job: SyncJobEntity, remoteRange: String) {
        val syncedAt = System.currentTimeMillis()
        database.withTransaction {
            database.syncJobDao().updateState(
                id = job.transactionId,
                state = SyncStatus.SYNCED,
                attempts = job.attemptCount,
                nextAttempt = null,
                errorCode = null,
                remoteRange = remoteRange,
            )
            database.transactionDao().updateSyncState(
                id = job.transactionId,
                status = SyncStatus.SYNCED,
                remoteRange = remoteRange,
                syncedAt = syncedAt,
            )
        }
        val config = database.configDao().get()
        if (config != null) {
            database.configDao().upsert(config.copy(lastSuccessfulSyncEpochMs = syncedAt))
        }
    }

    private suspend fun markBlocked(job: SyncJobEntity, state: String, errorCode: String) {
        database.withTransaction {
            database.syncJobDao().updateState(
                id = job.transactionId,
                state = state,
                attempts = job.attemptCount,
                nextAttempt = null,
                errorCode = errorCode.take(64),
                remoteRange = job.remoteRange,
            )
            database.transactionDao().updateStatus(job.transactionId, state)
        }
    }

    private suspend fun scheduleBatchRetry(jobs: List<SyncJobEntity>, code: String) {
        val retryable = jobs.filter { it.attemptCount + 1 < MAX_ATTEMPTS }
        val exhausted = jobs - retryable.toSet()
        exhausted.forEach {
            markBlocked(
                it.copy(attemptCount = it.attemptCount + 1),
                SyncStatus.FAILED,
                "RETRY_LIMIT_REACHED",
            )
        }
        if (retryable.isEmpty()) return
        val maxAttempt = retryable.maxOfOrNull { it.attemptCount + 1 } ?: 1
        val delay = retryDelay(maxAttempt, null)
        retryable.forEach { job ->
            val attempts = job.attemptCount + 1
            database.withTransaction {
                database.syncJobDao().updateState(
                    id = job.transactionId,
                    state = SyncStatus.RETRY,
                    attempts = attempts,
                    nextAttempt = System.currentTimeMillis() + delay,
                    errorCode = code,
                    remoteRange = job.remoteRange,
                )
                database.transactionDao().updateStatus(job.transactionId, SyncStatus.RETRY)
            }
        }
        scheduler.enqueueContinuation(delay)
    }

    private suspend fun scheduleRetry(
        job: SyncJobEntity,
        code: String,
        serverDelayMillis: Long?,
    ) {
        if (job.attemptCount >= MAX_ATTEMPTS) {
            markBlocked(job, SyncStatus.FAILED, "RETRY_LIMIT_REACHED")
            return
        }
        val delay = retryDelay(job.attemptCount, serverDelayMillis)
        database.withTransaction {
            database.syncJobDao().updateState(
                id = job.transactionId,
                state = SyncStatus.RETRY,
                attempts = job.attemptCount,
                nextAttempt = System.currentTimeMillis() + delay,
                errorCode = code.take(64),
                remoteRange = job.remoteRange,
            )
            database.transactionDao().updateStatus(job.transactionId, SyncStatus.RETRY)
        }
        scheduler.enqueueContinuation(delay)
    }

    private suspend fun handleBatchHttpFailure(
        jobs: List<SyncJobEntity>,
        token: EphemeralAccessToken,
        error: SheetsHttpException,
    ) {
        when (classifyHttp(error)) {
            FailureKind.AUTH -> {
                authorization.clearCachedToken(token)
                jobs.forEach { markBlocked(it, SyncStatus.AUTH_REQUIRED, safeHttpCode(error)) }
            }
            FailureKind.TRANSIENT -> scheduleBatchRetry(jobs, safeHttpCode(error))
            FailureKind.SCHEMA -> jobs.forEach {
                markBlocked(it, SyncStatus.SCHEMA_ERROR, safeHttpCode(error))
            }
            FailureKind.PERMANENT -> jobs.forEach {
                markBlocked(it, SyncStatus.FAILED, safeHttpCode(error))
            }
        }
    }

    private fun classifyHttp(error: SheetsHttpException): FailureKind = when {
        error.statusCode == 401 -> FailureKind.AUTH
        error.isTransient -> FailureKind.TRANSIENT
        error.statusCode == 403 -> FailureKind.AUTH
        error.statusCode in setOf(400, 404, 409, 410, 412, 422) -> FailureKind.SCHEMA
        else -> FailureKind.PERMANENT
    }

    private fun safeHttpCode(error: SheetsHttpException): String = buildString {
        append("SHEETS_HTTP_")
        append(error.statusCode)
        error.apiStatus?.take(24)?.let { append('_').append(it.uppercase()) }
    }.take(64)

    private fun retryDelay(attempt: Int, serverDelayMillis: Long?): Long {
        val exponent = (attempt - 1).coerceIn(0, 9)
        val local = BASE_RETRY_MILLIS * (1L shl exponent)
        return min(MAX_RETRY_MILLIS, maxOf(local, serverDelayMillis ?: 0L))
    }

    private enum class FailureKind { AUTH, TRANSIENT, SCHEMA, PERMANENT }

    private companion object {
        const val MAX_WRITES_PER_RUN = 10
        const val MAX_ATTEMPTS = 8
        const val BASE_RETRY_MILLIS = 30_000L
        const val MAX_RETRY_MILLIS = 5 * 60 * 60 * 1_000L
        val SHARED_HTTP_CLIENT = GoogleSheetsHttpGateway.defaultClient()
    }
}

object SyncStatus {
    const val QUEUED = "QUEUED"
    const val SYNCING = "SYNCING"
    const val RETRY = "RETRY"
    const val SYNCED = "SYNCED"
    const val REVIEW = "REVIEW"
    const val AUTH_REQUIRED = "AUTH_REQUIRED"
    const val SHEET_REQUIRED = "SHEET_REQUIRED"
    const val SCHEMA_ERROR = "SCHEMA_ERROR"
    const val FAILED = "FAILED"
}
