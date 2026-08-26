package app.txnsheet.personal.data.remote

import app.txnsheet.personal.data.local.TransactionEntity
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID

object TransactionSheetContract {
    const val SCHEMA_VERSION = 1
    const val TRANSACTIONS_TAB = "Transactions"
    const val DASHBOARD_TAB = "Dashboard"
    const val CONFIG_TAB = "Config"

    val HEADERS: List<String> = listOf(
        "transaction_id",
        "event_time",
        "captured_time",
        "amount",
        "currency",
        "direction",
        "method",
        "counterparty",
        "category",
        "institution",
        "account_last4",
        "reference_id",
        "balance",
        "source_app",
        "source_label",
        "confidence",
        "parser_rule",
        "notes",
    )

    const val HEADER_RANGE = "Transactions!A1:R1"
    const val UUID_RANGE = "Transactions!A2:A"
    const val APPEND_RANGE = "Transactions!A:R"
    const val CONFIG_RANGE = "Config!A:B"
}

data class WorkbookCreationRequest(
    val title: String,
    val timezone: String,
    val currency: String,
    val appVersion: String,
    val createdAtEpochMs: Long,
    val schemaVersion: Int = TransactionSheetContract.SCHEMA_VERSION,
)

data class WorkbookDescriptor(
    val spreadsheetId: String,
    val spreadsheetUrl: String?,
    val transactionsTabId: Int,
    val dashboardTabId: Int,
    val configTabId: Int,
)

data class RemoteTransactionLocation(
    val rowNumber: Int,
    val range: String = "Transactions!A$rowNumber:R$rowNumber",
)

data class AppendReceipt(val updatedRange: String)

sealed interface SchemaValidationResult {
    data object Valid : SchemaValidationResult

    data class Invalid(val reason: Reason) : SchemaValidationResult {
        enum class Reason {
            HEADER_MISMATCH,
            CONFIG_MISSING,
            SCHEMA_VERSION_UNSUPPORTED,
        }
    }
}

interface SheetsGateway {
    suspend fun createWorkbook(
        token: EphemeralAccessToken,
        request: WorkbookCreationRequest,
    ): WorkbookDescriptor

    suspend fun validateSchema(
        token: EphemeralAccessToken,
        spreadsheetId: String,
        supportedSchemaVersion: Int = TransactionSheetContract.SCHEMA_VERSION,
    ): SchemaValidationResult

    /** Reads workbook metadata without requesting broader Drive access. */
    suspend fun inspectWorkbook(
        token: EphemeralAccessToken,
        spreadsheetId: String,
    ): WorkbookDescriptor

    suspend fun findTransactionByUuid(
        token: EphemeralAccessToken,
        spreadsheetId: String,
        transactionId: String,
    ): RemoteTransactionLocation?

    suspend fun appendTransaction(
        token: EphemeralAccessToken,
        spreadsheetId: String,
        transaction: TransactionEntity,
        timezone: String,
    ): AppendReceipt

    /**
     * Updates only the verified UUID row. Column A is never mutated.
     * Callers must first obtain [location] with [findTransactionByUuid].
     */
    suspend fun updateCorrection(
        token: EphemeralAccessToken,
        spreadsheetId: String,
        transaction: TransactionEntity,
        timezone: String,
        location: RemoteTransactionLocation,
    ): AppendReceipt
}

class SheetsHttpException(
    val statusCode: Int,
    val apiStatus: String?,
    val retryAfterMillis: Long?,
    val apiReason: String? = null,
) : Exception("Google Sheets request failed (HTTP $statusCode${apiStatus?.let { ", $it" } ?: ""})") {
    val isTransient: Boolean
        get() = statusCode in setOf(408, 425, 429) ||
            statusCode in 500..599 ||
            apiReason in TRANSIENT_REASONS

    private companion object {
        val TRANSIENT_REASONS = setOf(
            "RATE_LIMIT_EXCEEDED",
            "RESOURCE_EXHAUSTED",
            "rateLimitExceeded",
            "userRateLimitExceeded",
            "backendError",
        )
    }
}

class SheetsProtocolException(code: String) : Exception(code)

object TransactionSheetRowMapper {
    fun toRow(transaction: TransactionEntity, timezone: String): List<Any> {
        require(runCatching { UUID.fromString(transaction.transactionId) }.isSuccess) {
            "transactionId must be a UUID"
        }
        val amountMinor = requireNotNull(transaction.amountMinor) { "amountMinor is required" }
        val direction = requireNotNull(transaction.direction) { "direction is required" }
        require(amountMinor > 0) { "amountMinor must be positive" }
        require(transaction.confidence in 0.0..1.0) { "confidence must be between 0 and 1" }

        val zone = ZoneId.of(timezone)
        return listOf(
            transaction.transactionId,
            GoogleDateSerial.fromEpochMillis(transaction.eventTimeEpochMs, zone),
            GoogleDateSerial.fromEpochMillis(transaction.capturedTimeEpochMs, zone),
            BigDecimal.valueOf(amountMinor, 2),
            transaction.currency,
            direction,
            transaction.method,
            transaction.counterparty.orEmpty(),
            transaction.category,
            transaction.institution.orEmpty(),
            transaction.accountLast4.orEmpty(),
            transaction.referenceId.orEmpty(),
            transaction.balanceMinor?.let { BigDecimal.valueOf(it, 2) } ?: "",
            transaction.sourceApp,
            transaction.sourceLabel.orEmpty(),
            BigDecimal.valueOf(transaction.confidence),
            transaction.parserRule,
            transaction.notes,
        ).also { check(it.size == TransactionSheetContract.HEADERS.size) }
    }
}

object GoogleDateSerial {
    private val epochDate = LocalDate.of(1899, 12, 30)
    private const val NANOS_PER_DAY = 86_400_000_000_000.0

    /** Converts an Instant to the local wall-clock serial used by a timezone-configured Sheet. */
    fun fromEpochMillis(epochMillis: Long, zoneId: ZoneId): Double {
        val local = Instant.ofEpochMilli(epochMillis).atZone(zoneId).toLocalDateTime()
        val days = ChronoUnit.DAYS.between(epochDate, local.toLocalDate()).toDouble()
        return days + (local.toLocalTime().toNanoOfDay() / NANOS_PER_DAY)
    }
}
