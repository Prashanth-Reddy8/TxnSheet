package app.txnsheet.personal.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FinanceDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(budget: BudgetEntity)
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(debt: DebtEntity)
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(goal: GoalEntity)
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(annotation: TransactionAnnotationEntity)
    @Query("SELECT * FROM finance_preferences WHERE id = 1 LIMIT 1")
    fun observePreferences(): Flow<FinancePreferencesEntity?>

    @Query("SELECT * FROM monthly_budgets ORDER BY month DESC")
    fun observeBudgets(): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM debts ORDER BY active DESC, name COLLATE NOCASE")
    fun observeDebts(): Flow<List<DebtEntity>>

    @Query("SELECT * FROM finance_goals ORDER BY targetDate, name COLLATE NOCASE")
    fun observeGoals(): Flow<List<GoalEntity>>

    @Query("SELECT * FROM transaction_annotations")
    fun observeAnnotations(): Flow<List<TransactionAnnotationEntity>>

    @Query("SELECT * FROM workbook_snapshots WHERE id = 1 LIMIT 1")
    fun observeWorkbook(): Flow<WorkbookSnapshotEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(preferences: FinancePreferencesEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(budget: BudgetEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(debt: DebtEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(goal: GoalEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(annotation: TransactionAnnotationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(workbook: WorkbookSnapshotEntity)

    @Query("DELETE FROM debts WHERE id = :id")
    suspend fun deleteDebt(id: String)

    @Query("DELETE FROM finance_goals WHERE id = :id")
    suspend fun deleteGoal(id: String)
}
