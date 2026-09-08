package app.txnsheet.personal.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TransactionEntity::class,
        ReviewItemEntity::class,
        SyncJobEntity::class,
        SourceAppEntity::class,
        ParserRuleEntity::class,
        CategoryRuleEntity::class,
        AppConfigEntity::class,
        DiagnosticEventEntity::class,
        CorrectionAuditEntity::class,
        FinancePreferencesEntity::class,
        BudgetEntity::class,
        DebtEntity::class,
        GoalEntity::class,
        TransactionAnnotationEntity::class,
        WorkbookSnapshotEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class TxnSheetDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun reviewDao(): ReviewDao
    abstract fun syncJobDao(): SyncJobDao
    abstract fun sourceAppDao(): SourceAppDao
    abstract fun categoryRuleDao(): CategoryRuleDao
    abstract fun configDao(): ConfigDao
    abstract fun diagnosticDao(): DiagnosticDao
    abstract fun correctionAuditDao(): CorrectionAuditDao
    abstract fun atomicStoreDao(): AtomicStoreDao
    abstract fun financeDao(): FinanceDao

    companion object {
        @Volatile private var instance: TxnSheetDatabase? = null

        /** Additive migration: existing captures, source choices and review data are retained. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `finance_preferences` (`id` INTEGER NOT NULL, `openingCashMinor` INTEGER, `monthlyIncomeMinor` INTEGER, `emergencyMonths` INTEGER NOT NULL, `savingsTargetPercent` INTEGER NOT NULL, `essentialCapPercent` INTEGER NOT NULL, `openingMonth` TEXT NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `monthly_budgets` (`month` TEXT NOT NULL, `incomeMinor` INTEGER, `essentialMinor` INTEGER, `discretionaryMinor` INTEGER, `debtMinor` INTEGER, `savingsMinor` INTEGER, PRIMARY KEY(`month`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `debts` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `type` TEXT NOT NULL, `originalMinor` INTEGER NOT NULL, `outstandingMinor` INTEGER NOT NULL, `annualInterestPercent` REAL NOT NULL, `emiMinor` INTEGER NOT NULL, `tenureMonths` INTEGER NOT NULL, `emisPaid` INTEGER NOT NULL, `dueDay` INTEGER NOT NULL, `startDate` TEXT NOT NULL, `extraPaymentMinor` INTEGER NOT NULL, `priority` TEXT NOT NULL, `active` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `finance_goals` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `targetMinor` INTEGER NOT NULL, `currentMinor` INTEGER NOT NULL, `targetDate` TEXT NOT NULL, `priority` TEXT NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `transaction_annotations` (`transactionId` TEXT NOT NULL, `essential` INTEGER, `flowType` TEXT, PRIMARY KEY(`transactionId`), FOREIGN KEY(`transactionId`) REFERENCES `transactions`(`transactionId`) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `workbook_snapshots` (`id` INTEGER NOT NULL, `title` TEXT NOT NULL, `importedAtEpochMs` INTEGER NOT NULL, `sheetsJson` TEXT NOT NULL, PRIMARY KEY(`id`))")
            }
        }

        fun create(context: Context): TxnSheetDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    TxnSheetDatabase::class.java,
                    "txnsheet.db",
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }
    }
}
