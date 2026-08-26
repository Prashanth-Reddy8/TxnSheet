package app.txnsheet.personal.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

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
    ],
    version = 1,
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

    companion object {
        @Volatile private var instance: TxnSheetDatabase? = null

        fun create(context: Context): TxnSheetDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    TxnSheetDatabase::class.java,
                    "txnsheet.db",
                )
                    .build()
                    .also { instance = it }
            }
    }
}
