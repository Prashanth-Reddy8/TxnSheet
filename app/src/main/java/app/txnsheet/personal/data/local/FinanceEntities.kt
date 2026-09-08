package app.txnsheet.personal.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(tableName = "finance_preferences")
data class FinancePreferencesEntity(
    @PrimaryKey val id: Int = 1,
    val openingCashMinor: Long? = null,
    val monthlyIncomeMinor: Long? = null,
    val emergencyMonths: Int = 6,
    val savingsTargetPercent: Int = 20,
    val essentialCapPercent: Int = 60,
    /** yyyy-MM: the opening cash is measured before this month's first transaction. */
    val openingMonth: String = "",
)

@Entity(tableName = "monthly_budgets")
data class BudgetEntity(
    @PrimaryKey val month: String,
    val incomeMinor: Long? = null,
    val essentialMinor: Long? = null,
    val discretionaryMinor: Long? = null,
    val debtMinor: Long? = null,
    val savingsMinor: Long? = null,
)

@Entity(tableName = "debts")
data class DebtEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String = "Other",
    val originalMinor: Long,
    val outstandingMinor: Long,
    val annualInterestPercent: Double,
    val emiMinor: Long,
    val tenureMonths: Int,
    val emisPaid: Int,
    val dueDay: Int,
    val startDate: String,
    val extraPaymentMinor: Long = 0,
    val priority: String = "Medium",
    val active: Boolean = true,
)

@Entity(tableName = "finance_goals")
data class GoalEntity(
    @PrimaryKey val id: String,
    val name: String,
    val targetMinor: Long,
    val currentMinor: Long,
    val targetDate: String,
    val priority: String = "Medium",
)

@Entity(
    tableName = "transaction_annotations",
    foreignKeys = [ForeignKey(
        entity = TransactionEntity::class,
        parentColumns = ["transactionId"],
        childColumns = ["transactionId"],
        onDelete = ForeignKey.CASCADE,
    )],
)
data class TransactionAnnotationEntity(
    @PrimaryKey val transactionId: String,
    val essential: Boolean? = null,
    /** Owner-confirmed INCOME, EXPENSE, TRANSFER, REFUND or CARD_PAYMENT. */
    val flowType: String? = null,
)

@Entity(tableName = "workbook_snapshots")
data class WorkbookSnapshotEntity(
    @PrimaryKey val id: Int = 1,
    val title: String,
    val importedAtEpochMs: Long,
    val sheetsJson: String,
)

data class FinanceData(
    val preferences: FinancePreferencesEntity = FinancePreferencesEntity(),
    val budgets: List<BudgetEntity> = emptyList(),
    val debts: List<DebtEntity> = emptyList(),
    val goals: List<GoalEntity> = emptyList(),
    val annotations: List<TransactionAnnotationEntity> = emptyList(),
    val workbook: WorkbookSnapshotEntity? = null,
)
