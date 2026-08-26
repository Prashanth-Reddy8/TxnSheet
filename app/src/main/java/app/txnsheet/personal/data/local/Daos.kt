package app.txnsheet.personal.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(transaction: TransactionEntity)

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Query("SELECT * FROM transactions WHERE transactionId = :id LIMIT 1")
    suspend fun byId(id: String): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE transactionId = :id LIMIT 1")
    fun observeById(id: String): Flow<TransactionEntity?>

    @Query("SELECT * FROM transactions ORDER BY eventTimeEpochMs DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE status = :status ORDER BY eventTimeEpochMs DESC")
    fun observeByStatus(status: String): Flow<List<TransactionEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM transactions WHERE eventFingerprint = :fingerprint)")
    suspend fun fingerprintExists(fingerprint: String): Boolean

    @Query("SELECT COUNT(*) FROM transactions WHERE status = 'REVIEW'")
    fun observeReviewCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM transactions WHERE status IN ('QUEUED','RETRY','SYNCING')")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT COALESCE(SUM(amountMinor), 0) FROM transactions WHERE direction = :direction AND eventTimeEpochMs >= :startEpochMs AND eventTimeEpochMs < :endEpochMs AND status != 'IGNORED'")
    fun observeTotal(direction: String, startEpochMs: Long, endEpochMs: Long): Flow<Long>

    @Query("UPDATE transactions SET status = :status, remoteRange = :remoteRange, syncedAtEpochMs = :syncedAt WHERE transactionId = :id")
    suspend fun updateSyncState(id: String, status: String, remoteRange: String?, syncedAt: Long?)

    @Query("UPDATE transactions SET status = :status WHERE transactionId = :id")
    suspend fun updateStatus(id: String, status: String)

    @Query("UPDATE transactions SET amountMinor = :amountMinor, direction = :direction, method = :method, counterparty = :counterparty, category = :category, notes = :notes, status = :status WHERE transactionId = :id")
    suspend fun updateReviewedFields(
        id: String,
        amountMinor: Long,
        direction: String,
        method: String,
        counterparty: String?,
        category: String,
        notes: String,
        status: String,
    )

    @Query("UPDATE transactions SET counterparty = :counterparty, category = :category, notes = :notes WHERE transactionId = :id")
    suspend fun updateEditableFields(id: String, counterparty: String?, category: String, notes: String)

    @Query("DELETE FROM transactions WHERE transactionId = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()
}

@Dao
interface ReviewDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: ReviewItemEntity)

    @Query("SELECT * FROM review_items WHERE transactionId = :id LIMIT 1")
    suspend fun byId(id: String): ReviewItemEntity?

    @Query("SELECT review_items.* FROM review_items INNER JOIN transactions USING(transactionId) ORDER BY transactions.eventTimeEpochMs DESC")
    fun observeAll(): Flow<List<ReviewItemEntity>>

    @Query("DELETE FROM review_items WHERE transactionId = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM review_items WHERE expiresAtEpochMs <= :nowEpochMs")
    suspend fun purgeExpired(nowEpochMs: Long): Int

    @Query("DELETE FROM review_items")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM review_items")
    suspend fun count(): Int
}

@Dao
interface SyncJobDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(job: SyncJobEntity)

    @Query("SELECT * FROM sync_jobs WHERE state IN ('QUEUED','RETRY','SYNCING') AND (nextAttemptEpochMs IS NULL OR nextAttemptEpochMs <= :nowEpochMs) ORDER BY attemptCount, transactionId LIMIT :limit")
    suspend fun ready(nowEpochMs: Long, limit: Int): List<SyncJobEntity>

    @Query("SELECT * FROM sync_jobs WHERE transactionId = :id LIMIT 1")
    suspend fun byId(id: String): SyncJobEntity?

    @Query("UPDATE sync_jobs SET state = :state, attemptCount = :attempts, nextAttemptEpochMs = :nextAttempt, errorCode = :errorCode, remoteRange = :remoteRange WHERE transactionId = :id")
    suspend fun updateState(
        id: String,
        state: String,
        attempts: Int,
        nextAttempt: Long?,
        errorCode: String?,
        remoteRange: String?,
    )

    @Query("DELETE FROM sync_jobs WHERE transactionId = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM sync_jobs")
    suspend fun deleteAll()
}

@Dao
interface SourceAppDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(source: SourceAppEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(source: SourceAppEntity): Long

    @Query("SELECT * FROM source_apps ORDER BY enabled DESC, label COLLATE NOCASE")
    fun observeAll(): Flow<List<SourceAppEntity>>

    @Query("SELECT * FROM source_apps WHERE packageName = :packageName LIMIT 1")
    suspend fun byPackage(packageName: String): SourceAppEntity?

    @Query("SELECT enabled FROM source_apps WHERE packageName = :packageName LIMIT 1")
    suspend fun isEnabled(packageName: String): Boolean?

    @Query("UPDATE source_apps SET enabled = :enabled WHERE packageName = :packageName")
    suspend fun setEnabled(packageName: String, enabled: Boolean)

    @Query("UPDATE source_apps SET label = :label, lastSeenEpochMs = :seenAtEpochMs WHERE packageName = :packageName")
    suspend fun touch(packageName: String, label: String, seenAtEpochMs: Long)

    /**
     * Registers a notification source without ever changing the owner's allow-list choice.
     * Newly discovered packages are deliberately disabled.
     */
    @Transaction
    suspend fun recordSeen(packageName: String, label: String, seenAtEpochMs: Long): Boolean {
        insertIfAbsent(
            SourceAppEntity(
                packageName = packageName,
                label = label,
                enabled = false,
                firstSeenEpochMs = seenAtEpochMs,
                lastSeenEpochMs = seenAtEpochMs,
            ),
        )
        touch(packageName, label, seenAtEpochMs)
        return isEnabled(packageName) == true
    }

    @Query("DELETE FROM source_apps")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM source_apps WHERE enabled = 1")
    fun observeEnabledCount(): Flow<Int>
}

@Dao
interface CategoryRuleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: CategoryRuleEntity)

    @Query("SELECT * FROM category_rules ORDER BY priority DESC, ruleId ASC")
    fun observeAll(): Flow<List<CategoryRuleEntity>>

    @Query("SELECT * FROM category_rules ORDER BY priority DESC, ruleId ASC")
    suspend fun allByPriority(): List<CategoryRuleEntity>

    @Query("DELETE FROM category_rules WHERE ruleId = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM category_rules")
    suspend fun deleteAll()
}

@Dao
interface ConfigDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(config: AppConfigEntity)

    @Query("SELECT * FROM app_config WHERE id = 1 LIMIT 1")
    suspend fun get(): AppConfigEntity?

    @Query("SELECT * FROM app_config WHERE id = 1 LIMIT 1")
    fun observe(): Flow<AppConfigEntity?>

    @Query("UPDATE sync_jobs SET state = 'QUEUED', nextAttemptEpochMs = NULL, errorCode = NULL WHERE state IN (:states)")
    suspend fun requeueBlockedSyncJobs(states: List<String>): Int

    @Query("UPDATE transactions SET status = 'QUEUED' WHERE status IN (:statuses)")
    suspend fun requeueBlockedTransactions(statuses: List<String>): Int

    @Query("UPDATE sync_jobs SET state = 'SHEET_REQUIRED', nextAttemptEpochMs = NULL, errorCode = 'SHEET_DISCONNECTED' WHERE state IN ('QUEUED','RETRY','SYNCING','AUTH_REQUIRED','SCHEMA_ERROR')")
    suspend fun blockPendingJobsForDisconnect(): Int

    @Query("UPDATE transactions SET status = 'SHEET_REQUIRED' WHERE status IN ('QUEUED','RETRY','SYNCING','AUTH_REQUIRED','SCHEMA_ERROR')")
    suspend fun blockPendingTransactionsForDisconnect(): Int

    @Query("DELETE FROM app_config")
    suspend fun deleteAll()
}

@Dao
interface DiagnosticDao {
    @Insert
    suspend fun insert(event: DiagnosticEventEntity)

    @Query("SELECT * FROM diagnostic_events ORDER BY createdAtEpochMs DESC LIMIT :limit")
    fun observeRecent(limit: Int = 100): Flow<List<DiagnosticEventEntity>>

    @Query("DELETE FROM diagnostic_events WHERE createdAtEpochMs < :cutoffEpochMs")
    suspend fun purgeBefore(cutoffEpochMs: Long): Int

    @Query("DELETE FROM diagnostic_events")
    suspend fun deleteAll()
}

@Dao
interface CorrectionAuditDao {
    @Insert
    suspend fun insert(entry: CorrectionAuditEntity)

    @Query("SELECT * FROM correction_audit WHERE transactionId = :id ORDER BY changedAtEpochMs DESC")
    fun observeFor(id: String): Flow<List<CorrectionAuditEntity>>

    @Query("DELETE FROM correction_audit")
    suspend fun deleteAll()
}

@Dao
interface AtomicStoreDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTransaction(transaction: TransactionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReview(item: ReviewItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSync(job: SyncJobEntity)

    @Transaction
    suspend fun insertForReview(transaction: TransactionEntity, review: ReviewItemEntity) {
        insertTransaction(transaction)
        upsertReview(review)
    }

    @Transaction
    suspend fun insertForSync(transaction: TransactionEntity, job: SyncJobEntity) {
        insertTransaction(transaction)
        upsertSync(job)
    }
}
