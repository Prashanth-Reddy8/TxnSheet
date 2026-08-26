package app.txnsheet.personal.sync

import android.app.PendingIntent
import android.content.Intent
import app.txnsheet.personal.data.local.AppConfigEntity
import app.txnsheet.personal.data.local.ConfigDao
import app.txnsheet.personal.data.remote.AuthorizationOutcome
import app.txnsheet.personal.data.remote.EphemeralAccessToken
import app.txnsheet.personal.data.remote.GoogleAuthorizationGateway
import app.txnsheet.personal.data.remote.SchemaValidationResult
import app.txnsheet.personal.data.remote.SheetsGateway
import app.txnsheet.personal.data.remote.SheetsHttpException
import app.txnsheet.personal.data.remote.SheetsProtocolException
import app.txnsheet.personal.data.remote.TransactionSheetContract
import app.txnsheet.personal.data.remote.WorkbookCreationRequest
import app.txnsheet.personal.data.remote.WorkbookDescriptor
import kotlinx.coroutines.CancellationException
import java.io.IOException

sealed interface WorkbookSetupOutcome {
    data class Ready(val workbook: WorkbookDescriptor) : WorkbookSetupOutcome
    data class ResolutionRequired(val pendingIntent: PendingIntent) : WorkbookSetupOutcome
    data class Error(val code: String, val isTransient: Boolean) : WorkbookSetupOutcome
}

sealed interface GoogleDisconnectOutcome {
    data class Disconnected(val revocationConfirmed: Boolean) : GoogleDisconnectOutcome
    data class ResolutionRequired(val pendingIntent: PendingIntent) : GoogleDisconnectOutcome
    data class Error(val code: String, val isTransient: Boolean) : GoogleDisconnectOutcome
}

/**
 * Activity-facing owner of the authorization → initialize/validate → persist sequence.
 * Access tokens remain stack-local and are never included in the result or database config.
 */
class WorkbookSetupCoordinator(
    private val authorization: GoogleAuthorizationGateway,
    private val sheets: SheetsGateway,
    private val configDao: ConfigDao,
    private val scheduler: LedgerSyncScheduler,
    private val clockMillis: () -> Long = System::currentTimeMillis,
) {
    suspend fun createWorkbook(title: String = DEFAULT_TITLE): WorkbookSetupOutcome =
        withSilentAuthorization { token -> createAuthorized(token, title) }

    suspend fun completeCreateAuthorization(
        data: Intent?,
        title: String = DEFAULT_TITLE,
    ): WorkbookSetupOutcome = withActivityAuthorization(data) { token ->
        createAuthorized(token, title)
    }

    /**
     * Accepts an app-created/already-granted Sheet id or standard URL. With drive.file scope this
     * intentionally cannot open an arbitrary Drive file that the user has never granted.
     */
    suspend fun linkWorkbook(idOrUrl: String): WorkbookSetupOutcome {
        val id = extractSpreadsheetId(idOrUrl)
            ?: return WorkbookSetupOutcome.Error("SPREADSHEET_ID_INVALID", false)
        return withSilentAuthorization { token -> linkAuthorized(token, id) }
    }

    suspend fun completeLinkAuthorization(
        data: Intent?,
        idOrUrl: String,
    ): WorkbookSetupOutcome {
        val id = extractSpreadsheetId(idOrUrl)
            ?: return WorkbookSetupOutcome.Error("SPREADSHEET_ID_INVALID", false)
        return withActivityAuthorization(data) { token -> linkAuthorized(token, id) }
    }

    suspend fun reconnect(): WorkbookSetupOutcome {
        val spreadsheetId = configDao.get()?.spreadsheetId
            ?: return WorkbookSetupOutcome.Error("SPREADSHEET_NOT_CONFIGURED", false)
        return withSilentAuthorization { token -> linkAuthorized(token, spreadsheetId) }
    }

    suspend fun completeReconnectAuthorization(data: Intent?): WorkbookSetupOutcome {
        val spreadsheetId = configDao.get()?.spreadsheetId
            ?: return WorkbookSetupOutcome.Error("SPREADSHEET_NOT_CONFIGURED", false)
        return withActivityAuthorization(data) { token -> linkAuthorized(token, spreadsheetId) }
    }

    suspend fun disconnect(): GoogleDisconnectOutcome =
        when (val outcome = authorization.authorizeSilently()) {
            is AuthorizationOutcome.Authorized -> revokeAndDisconnect(outcome)
            is AuthorizationOutcome.ResolutionRequired -> GoogleDisconnectOutcome.ResolutionRequired(
                outcome.pendingIntent,
            )
            is AuthorizationOutcome.Unavailable -> {
                if (outcome.isTransient) {
                    GoogleDisconnectOutcome.Error(outcome.code, true)
                } else {
                    clearLocalLinkage()
                    GoogleDisconnectOutcome.Disconnected(revocationConfirmed = false)
                }
            }
        }

    suspend fun completeDisconnectAuthorization(data: Intent?): GoogleDisconnectOutcome {
        if (data == null) return GoogleDisconnectOutcome.Error("GOOGLE_AUTH_CANCELLED", false)
        return when (val outcome = authorization.finishAuthorization(data)) {
            is AuthorizationOutcome.Authorized -> revokeAndDisconnect(outcome)
            is AuthorizationOutcome.ResolutionRequired -> GoogleDisconnectOutcome.ResolutionRequired(
                outcome.pendingIntent,
            )
            is AuthorizationOutcome.Unavailable -> GoogleDisconnectOutcome.Error(
                outcome.code,
                outcome.isTransient,
            )
        }
    }

    private suspend fun createAuthorized(
        token: EphemeralAccessToken,
        title: String,
    ): WorkbookSetupOutcome = guardedSheetsCall(token) {
        val config = configDao.get() ?: AppConfigEntity()
        val descriptor = sheets.createWorkbook(
            token,
            WorkbookCreationRequest(
                title = title.trim().take(100).ifBlank { DEFAULT_TITLE },
                timezone = config.timezone,
                currency = config.currency,
                appVersion = config.appVersion,
                createdAtEpochMs = clockMillis(),
                schemaVersion = config.schemaVersion,
            ),
        )
        persist(config, descriptor)
        scheduler.enqueue()
        WorkbookSetupOutcome.Ready(descriptor)
    }

    private suspend fun linkAuthorized(
        token: EphemeralAccessToken,
        spreadsheetId: String,
    ): WorkbookSetupOutcome = guardedSheetsCall(token) {
        val config = configDao.get() ?: AppConfigEntity()
        val descriptor = sheets.inspectWorkbook(token, spreadsheetId)
        when (val validation = sheets.validateSchema(
            token,
            spreadsheetId,
            config.schemaVersion,
        )) {
            SchemaValidationResult.Valid -> Unit
            is SchemaValidationResult.Invalid -> return@guardedSheetsCall WorkbookSetupOutcome.Error(
                code = "SCHEMA_${validation.reason.name}",
                isTransient = false,
            )
        }
        persist(config, descriptor)
        scheduler.enqueue()
        WorkbookSetupOutcome.Ready(descriptor)
    }

    private suspend fun persist(config: AppConfigEntity, descriptor: WorkbookDescriptor) {
        configDao.upsert(
            config.copy(
                spreadsheetId = descriptor.spreadsheetId,
                transactionsTabId = descriptor.transactionsTabId,
                dashboardTabId = descriptor.dashboardTabId,
                configTabId = descriptor.configTabId,
                schemaVersion = TransactionSheetContract.SCHEMA_VERSION,
            ),
        )
        configDao.requeueBlockedSyncJobs(RESUMABLE_STATES)
        configDao.requeueBlockedTransactions(RESUMABLE_STATES)
    }

    private suspend fun revokeAndDisconnect(
        authorized: AuthorizationOutcome.Authorized,
    ): GoogleDisconnectOutcome {
        val account = authorized.account
            ?: return GoogleDisconnectOutcome.Error("GOOGLE_ACCOUNT_MISSING", false)
        if (!authorization.revoke(account)) {
            return GoogleDisconnectOutcome.Error("GOOGLE_REVOKE_FAILED", true)
        }
        clearLocalLinkage()
        return GoogleDisconnectOutcome.Disconnected(revocationConfirmed = true)
    }

    private suspend fun clearLocalLinkage() {
        val config = configDao.get() ?: AppConfigEntity()
        configDao.upsert(
            config.copy(
                spreadsheetId = null,
                transactionsTabId = null,
                dashboardTabId = null,
                configTabId = null,
                lastSuccessfulSyncEpochMs = null,
            ),
        )
        configDao.blockPendingJobsForDisconnect()
        configDao.blockPendingTransactionsForDisconnect()
        scheduler.cancel()
    }

    private suspend fun withSilentAuthorization(
        action: suspend (EphemeralAccessToken) -> WorkbookSetupOutcome,
    ): WorkbookSetupOutcome = when (val outcome = authorization.authorizeSilently()) {
        is AuthorizationOutcome.Authorized -> action(outcome.token)
        is AuthorizationOutcome.ResolutionRequired -> WorkbookSetupOutcome.ResolutionRequired(
            outcome.pendingIntent,
        )
        is AuthorizationOutcome.Unavailable -> WorkbookSetupOutcome.Error(
            outcome.code,
            outcome.isTransient,
        )
    }

    private suspend fun withActivityAuthorization(
        data: Intent?,
        action: suspend (EphemeralAccessToken) -> WorkbookSetupOutcome,
    ): WorkbookSetupOutcome {
        if (data == null) return WorkbookSetupOutcome.Error("GOOGLE_AUTH_CANCELLED", false)
        return when (val outcome = authorization.finishAuthorization(data)) {
            is AuthorizationOutcome.Authorized -> action(outcome.token)
            is AuthorizationOutcome.ResolutionRequired -> WorkbookSetupOutcome.ResolutionRequired(
                outcome.pendingIntent,
            )
            is AuthorizationOutcome.Unavailable -> WorkbookSetupOutcome.Error(
                outcome.code,
                outcome.isTransient,
            )
        }
    }

    private suspend fun guardedSheetsCall(
        token: EphemeralAccessToken,
        block: suspend () -> WorkbookSetupOutcome,
    ): WorkbookSetupOutcome = try {
        block()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: SheetsHttpException) {
        if (error.statusCode == 401 || (error.statusCode == 403 && !error.isTransient)) {
            authorization.clearCachedToken(token)
            WorkbookSetupOutcome.Error("AUTH_REQUIRED", false)
        } else {
            WorkbookSetupOutcome.Error(
                code = listOfNotNull(
                    "SHEETS_HTTP_${error.statusCode}",
                    error.apiStatus?.takeIf(String::isNotBlank),
                    error.apiReason?.takeIf(String::isNotBlank),
                ).joinToString("_").take(96),
                isTransient = error.isTransient,
            )
        }
    } catch (_: IOException) {
        WorkbookSetupOutcome.Error("NETWORK_IO", true)
    } catch (error: SheetsProtocolException) {
        WorkbookSetupOutcome.Error(
            code = error.message?.take(64) ?: "SHEETS_PROTOCOL",
            isTransient = false,
        )
    } catch (_: IllegalArgumentException) {
        WorkbookSetupOutcome.Error("WORKBOOK_REQUEST_INVALID", false)
    } catch (_: RuntimeException) {
        WorkbookSetupOutcome.Error("WORKBOOK_SETUP_UNAVAILABLE", true)
    }

    private fun extractSpreadsheetId(input: String): String? {
        val trimmed = input.trim()
        val candidate = SHEETS_URL_ID.find(trimmed)?.groupValues?.getOrNull(1) ?: trimmed
        return candidate.takeIf(SPREADSHEET_ID::matches)
    }

    private companion object {
        const val DEFAULT_TITLE = "TxnSheet Ledger"
        val SPREADSHEET_ID = Regex("^[A-Za-z0-9_-]{10,200}$")
        val SHEETS_URL_ID = Regex("https?://docs\\.google\\.com/spreadsheets/d/([A-Za-z0-9_-]+)")
        val RESUMABLE_STATES = listOf(
            SyncStatus.AUTH_REQUIRED,
            SyncStatus.SHEET_REQUIRED,
            SyncStatus.SCHEMA_ERROR,
        )
    }
}
