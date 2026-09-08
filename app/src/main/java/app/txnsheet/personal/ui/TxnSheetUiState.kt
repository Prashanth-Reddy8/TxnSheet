package app.txnsheet.personal.ui

import app.txnsheet.personal.data.local.AppConfigEntity
import app.txnsheet.personal.data.local.CategoryRuleEntity
import app.txnsheet.personal.data.local.DiagnosticEventEntity
import app.txnsheet.personal.data.local.SourceAppEntity
import app.txnsheet.personal.data.local.TransactionEntity
import app.txnsheet.personal.data.local.FinanceData
import app.txnsheet.personal.data.repository.WorkbookPreview
import java.time.YearMonth

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
    val finance: FinanceData = FinanceData(),
    val selectedMonth: String = YearMonth.now().toString(),
    val workbookPreview: WorkbookPreview? = null,
) {
    val reviewTransactions: List<TransactionEntity>
        get() = transactions.filter { it.status == "REVIEW" }

    val enabledSourceCount: Int
        get() = sources.count(SourceAppEntity::enabled)
}

sealed interface UiEffect {
    data class NavigateToTransaction(val transactionId: String, val review: Boolean) : UiEffect
    data class Message(val text: String) : UiEffect
    data object CloseCurrent : UiEffect
}

enum class ActivityFilter(val label: String) {
    ALL("All"),
    DEBITS("Debits"),
    CREDITS("Credits"),
    NEEDS_REVIEW("Needs review"),
}
