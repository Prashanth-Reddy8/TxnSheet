package app.txnsheet.personal.ui

import app.txnsheet.personal.data.local.AppConfigEntity
import app.txnsheet.personal.data.local.CategoryRuleEntity
import app.txnsheet.personal.data.local.DiagnosticEventEntity
import app.txnsheet.personal.data.local.SourceAppEntity
import app.txnsheet.personal.data.local.TransactionEntity

data class TxnSheetUiState(
    val loading: Boolean = true,
    val config: AppConfigEntity = AppConfigEntity(),
    val transactions: List<TransactionEntity> = emptyList(),
    val sources: List<SourceAppEntity> = emptyList(),
    val categoryRules: List<CategoryRuleEntity> = emptyList(),
    val diagnostics: List<DiagnosticEventEntity> = emptyList(),
    val notificationAccessGranted: Boolean = false,
    val actionInProgress: Boolean = false,
    val transientMessage: String? = null,
    val reviewSourceById: Map<String, String?> = emptyMap(),
) {
    val reviewTransactions: List<TransactionEntity>
        get() = transactions.filter { it.status == "REVIEW" }

    val pendingCount: Int
        get() = transactions.count { it.status in PENDING_STATUSES }

    val authRequiredCount: Int
        get() = transactions.count { it.status == "AUTH_REQUIRED" }

    val syncIssueCount: Int
        get() = transactions.count { it.status in SYNC_ISSUE_STATUSES }

    val enabledSourceCount: Int
        get() = sources.count(SourceAppEntity::enabled)

    val sheetConnected: Boolean
        get() = !config.spreadsheetId.isNullOrBlank()

    companion object {
        private val PENDING_STATUSES = setOf("QUEUED", "RETRY", "SYNCING", "AUTH_REQUIRED")
        private val SYNC_ISSUE_STATUSES = setOf("FAILED", "SCHEMA_ERROR", "SHEET_REQUIRED")
    }
}

sealed interface UiEffect {
    data class LaunchGoogleAuthorization(val pendingIntent: android.app.PendingIntent) : UiEffect
    data class OpenSpreadsheet(val spreadsheetId: String) : UiEffect
    data class NavigateToTransaction(val transactionId: String, val review: Boolean) : UiEffect
    data class Message(val text: String) : UiEffect
    data object CloseCurrent : UiEffect
}

enum class ActivityFilter(val label: String) {
    ALL("All"),
    DEBITS("Debits"),
    CREDITS("Credits"),
    NEEDS_REVIEW("Needs review"),
    NOT_SYNCED("Not synced"),
}
