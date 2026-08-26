package app.txnsheet.personal.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transactions",
    indices = [Index(value = ["eventFingerprint"], unique = true), Index("status")],
)
data class TransactionEntity(
    @PrimaryKey val transactionId: String,
    val eventTimeEpochMs: Long,
    val capturedTimeEpochMs: Long,
    val amountMinor: Long?,
    val currency: String,
    val direction: String?,
    val method: String,
    val counterparty: String?,
    val category: String,
    val institution: String?,
    val accountLast4: String?,
    val referenceId: String?,
    val balanceMinor: Long?,
    val sourceApp: String,
    val sourceLabel: String?,
    val confidence: Double,
    val parserRule: String,
    val notes: String,
    val eventFingerprint: String,
    val status: String,
    val remoteRange: String? = null,
    val syncedAtEpochMs: Long? = null,
)

@Entity(
    tableName = "review_items",
    foreignKeys = [
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["transactionId"],
            childColumns = ["transactionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("expiresAtEpochMs")],
)
data class ReviewItemEntity(
    @PrimaryKey val transactionId: String,
    val encryptedPayload: ByteArray,
    val initializationVector: ByteArray,
    val reason: String,
    val expiresAtEpochMs: Long,
    val attempts: Int = 0,
)

@Entity(
    tableName = "sync_jobs",
    foreignKeys = [
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["transactionId"],
            childColumns = ["transactionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("state"), Index("nextAttemptEpochMs")],
)
data class SyncJobEntity(
    @PrimaryKey val transactionId: String,
    val state: String,
    val attemptCount: Int = 0,
    val nextAttemptEpochMs: Long? = null,
    val errorCode: String? = null,
    val remoteRange: String? = null,
)

@Entity(tableName = "source_apps")
data class SourceAppEntity(
    @PrimaryKey val packageName: String,
    val label: String,
    val enabled: Boolean = false,
    val firstSeenEpochMs: Long,
    val lastSeenEpochMs: Long,
)

@Entity(tableName = "parser_rules", primaryKeys = ["ruleId", "version"])
data class ParserRuleEntity(
    val ruleId: String,
    val version: String,
    val enabled: Boolean,
    val patternData: String,
    val origin: String,
)

@Entity(tableName = "category_rules", indices = [Index("priority")])
data class CategoryRuleEntity(
    @PrimaryKey val ruleId: String,
    val matchType: String,
    val normalizedTerm: String,
    val category: String,
    val priority: Int,
)

@Entity(tableName = "app_config")
data class AppConfigEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val spreadsheetId: String? = null,
    val transactionsTabId: Int? = null,
    val dashboardTabId: Int? = null,
    val configTabId: Int? = null,
    val timezone: String = "Asia/Kolkata",
    val currency: String = "INR",
    val reviewRetentionDays: Int = 7,
    val schemaVersion: Int = 1,
    val appVersion: String = "1.0.0",
    val onboardingComplete: Boolean = false,
    val lastSuccessfulSyncEpochMs: Long? = null,
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}

@Entity(tableName = "diagnostic_events", indices = [Index("createdAtEpochMs")])
data class DiagnosticEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAtEpochMs: Long,
    val code: String,
    val severity: String,
    val component: String,
    val redactedContext: String? = null,
)

@Entity(
    tableName = "correction_audit",
    foreignKeys = [
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["transactionId"],
            childColumns = ["transactionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("transactionId")],
)
data class CorrectionAuditEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val transactionId: String,
    val changedFieldNames: String,
    val changedAtEpochMs: Long,
)
