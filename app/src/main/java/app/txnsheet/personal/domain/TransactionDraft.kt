package app.txnsheet.personal.domain

import java.time.Instant

/** The direction of money movement from the owner's point of view. */
enum class Direction {
    DEBIT,
    CREDIT,
}

/** Stable values written to the ledger's method column. */
enum class TransactionMethod {
    UPI,
    CARD,
    CREDIT_CARD,
    DEBIT_CARD,
    BANK_TRANSFER,
    AUTO_DEBIT,
    ATM,
    CASH,
    FEE,
    OTHER,
}

/**
 * A privacy-safe, structured candidate produced by the parser.
 *
 * Amount and direction are nullable because review items can be incomplete. The model deliberately
 * contains no raw or normalized message body.
 */
data class TransactionDraft(
    val amountMinor: Long?,
    val balanceMinor: Long? = null,
    val currency: String = "INR",
    val direction: Direction?,
    val method: TransactionMethod = TransactionMethod.OTHER,
    val counterparty: String? = null,
    val category: String = "Uncategorized",
    val institution: String? = null,
    val accountLast4: String? = null,
    val referenceId: String? = null,
    val eventTime: Instant,
    val capturedTime: Instant,
    val sourcePackage: String,
    val sourceLabel: String? = null,
    val confidence: Double,
    val parserRule: String,
)
