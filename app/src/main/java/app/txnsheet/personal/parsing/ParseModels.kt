package app.txnsheet.personal.parsing

import app.txnsheet.personal.domain.TransactionDraft
import java.time.Instant
import java.util.Locale

/** Final parser routing decisions from the approved specification. */
enum class ParseAction {
    AUTO_SYNC,
    REVIEW,
    IGNORE_SECURITY,
    IGNORE_PROMO,
    IGNORE_INFO,
    MANUAL_REQUIRED,
}

enum class ParseOrigin {
    NOTIFICATION,
    SHARE,
    PASTE,
    SAMPLE,
}

/** Explainable evidence used by the deterministic confidence score. */
enum class ParseEvidence {
    AMOUNT,
    DIRECTION,
    COMPLETED_ACTION,
    KNOWN_SOURCE_OR_RULE,
    METHOD_OR_ACCOUNT,
    REFERENCE,
    COUNTERPARTY_OR_ATM,
}

/** Privacy-safe diagnostic reasons; none of these contain message-derived values. */
enum class ParseIssue {
    EMPTY_OR_REDACTED,
    INPUT_TOO_LONG,
    OTP_OR_SECURITY_MESSAGE,
    PROMOTIONAL_MESSAGE,
    PAYMENT_NOT_COMPLETED,
    BALANCE_ONLY,
    INSUFFICIENT_EVIDENCE,
    MISSING_AMOUNT,
    MISSING_DIRECTION,
    CONFLICTING_DIRECTIONS,
    MULTIPLE_TRANSACTION_AMOUNTS,
    MALFORMED_AMOUNT,
    IMPLAUSIBLE_AMOUNT,
    MANUAL_CONFIRMATION_REQUIRED,
}

data class ParseContext(
    val sourcePackage: String,
    val captureTime: Instant,
    val eventTime: Instant = captureTime,
    val sourceLabel: String? = null,
    val institution: String? = null,
    val origin: ParseOrigin = ParseOrigin.NOTIFICATION,
    val sourceIsKnown: Boolean = false,
    val defaultCurrency: String = "INR",
    /** Android message identity, used transiently for duplicate suppression; never shown or logged. */
    val notificationIdentity: String? = null,
) {
    init {
        require(sourcePackage.isNotBlank()) { "sourcePackage must not be blank" }
        require(defaultCurrency.matches(Regex("[A-Za-z]{3}"))) {
            "defaultCurrency must be a three-letter ISO currency code"
        }
    }

    internal val normalizedCurrency: String
        get() = defaultCurrency.uppercase(Locale.ROOT)
}

data class ParseResult(
    val action: ParseAction,
    val draft: TransactionDraft?,
    val confidence: Double,
    val evidence: Set<ParseEvidence> = emptySet(),
    val issues: Set<ParseIssue> = emptySet(),
) {
    init {
        require(confidence in 0.0..1.0) { "confidence must be between 0 and 1" }
        require((action == ParseAction.AUTO_SYNC || action == ParseAction.REVIEW) == (draft != null)) {
            "Only AUTO_SYNC and REVIEW results may contain a transaction draft"
        }
    }
}
