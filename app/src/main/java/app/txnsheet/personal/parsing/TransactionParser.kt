package app.txnsheet.personal.parsing

import app.txnsheet.personal.domain.Direction
import app.txnsheet.personal.domain.TransactionDraft
import app.txnsheet.personal.domain.TransactionMethod
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.max

/**
 * Deterministic, local-only parser for the conservative baseline transaction formats.
 *
 * All patterns operate on an input capped at [TextNormalizer.MAX_PARSER_INPUT_CHARS]. Variable
 * message-derived captures and quantifiers are explicitly bounded.
 */
object TransactionParser {
    private const val MAX_REASONABLE_AMOUNT_MINOR = 100_000_000_000L

    private const val AMOUNT_TOKEN =
        "(?:[0-9]{1,3}(?:,[0-9]{2}){1,4},[0-9]{3}|" +
            "[0-9]{1,3}(?:,[0-9]{3}){1,3}|[0-9]{1,12})(?:\\.[0-9]{1,2})?"

    private val amountPatterns = listOf(
        Regex(
            """(?:₹|INR\b|RS\.?(?=\s|[0-9]))[\s:=-]{0,6}($AMOUNT_TOKEN)(?![0-9,]|\.[0-9])""",
            RegexOption.IGNORE_CASE,
        ),
        Regex(
            """\b(?:AMOUNT|AMT)\b(?:\s{1,3}OF\b)?[\s:=-]{0,6}""" +
                """(?:(?:₹|INR\b|RS\.?)\s{0,3})?($AMOUNT_TOKEN)(?![0-9,]|\.[0-9])""",
            RegexOption.IGNORE_CASE,
        ),
        Regex(
            """(?<![0-9.,])($AMOUNT_TOKEN)(?![0-9,]|\.[0-9])\s{0,3}(?:INR|RUPEES?)\b""",
            RegexOption.IGNORE_CASE,
        ),
    )

    private val currencyMarker = Regex("""(?:₹|\bINR\b|\bRS\.?(?=\s|[0-9]))""", RegexOption.IGNORE_CASE)
    private val balanceMarker = Regex(
        """\b(?:(?:AVAILABLE|AVL)\s{1,3})?(?:BALANCE|BAL)\b""",
        RegexOption.IGNORE_CASE,
    )

    private val otpOrSecurityMarker = Regex(
        """\b(?:OTP|ONE[\s-]{1,2}TIME\s{1,3}PASSWORD|VERIFICATION\s{1,3}CODE|""" +
            """DO\s{1,3}NOT\s{1,3}SHARE|VALID\s{1,3}FOR)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val promotionalMarker = Regex(
        """\b(?:OFFER|PRE[\s-]{0,1}APPROVED|APPLY\s{1,3}NOW|SALE|COUPON)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val negatedOrFutureAction = Regex(
        """\b(?:NOT|NEVER)\s{1,3}(?:BEEN\s{1,3})?(?:DEBITED|CREDITED|PAID|SENT|CHARGED)\b|""" +
            """\bWILL\s{1,3}(?:BE\s{1,3})?(?:DEBITED|CREDITED|PAID|SENT|CHARGED)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val incompletePayment = Regex(
        """\b(?:PAYMENT|TRANSACTION|TRANSFER)\b[^;]{0,72}\b(?:FAILED|DECLINED|PENDING|CANCELLED|CANCELED|UNSUCCESSFUL|INITIATED|PROCESSING)\b|""" +
            """\b(?:FAILED|DECLINED|PENDING|CANCELLED|CANCELED|UNSUCCESSFUL)\s{1,3}(?:PAYMENT|TRANSACTION|TRANSFER)\b""",
        RegexOption.IGNORE_CASE,
    )

    private val debitAction = Regex(
        """\b(?:DEBITED|PAID|SENT|SPENT|WITHDRAWN|WITHDRAWAL|CHARGED)\b|""" +
            """\b(?:TRANSFERRED|TRANSFER)\s{1,3}TO\b""",
        RegexOption.IGNORE_CASE,
    )
    private val creditAction = Regex(
        """\b(?:CREDITED|REFUNDED|DEPOSITED)\b|\bCASH\s{1,3}DEPOSIT\b|""" +
            """\b(?:TRANSFERRED|TRANSFER)\s{1,3}FROM\b|""" +
            """\bRECEIVED(?=\s{1,3}(?:FROM\b|VIA\b|₹|INR\b|RS\.?|$AMOUNT_TOKEN\s{1,3}(?:INR|RUPEES?)\b))""",
        RegexOption.IGNORE_CASE,
    )
    private val genericCompletedAction = Regex(
        """\b(?:TRANSACTION|PAYMENT)\s{1,3}(?:SUCCESSFUL|COMPLETED)\b""",
        RegexOption.IGNORE_CASE,
    )

    private val upiMethod = Regex("""\b(?:UPI|VPA)\b""", RegexOption.IGNORE_CASE)
    private val cardMethod = Regex(
        """\b(?:(?:DEBIT|CREDIT)\s{1,3})?CARD\b|\b(?:POS|ECOM|E-COMMERCE)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val bankTransferMethod = Regex(
        """\b(?:IMPS|NEFT|RTGS|BANK\s{1,3}TRANSFER)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val atmMethod = Regex(
        """\bATM\b|\bCASH\s{1,3}WITHDRAWAL\b""",
        RegexOption.IGNORE_CASE,
    )
    private val cashMethod = Regex(
        """\bCASH\s{1,3}DEPOSIT(?:ED)?\b""",
        RegexOption.IGNORE_CASE,
    )
    private val feeMethod = Regex(
        """\b(?:FEE|FEES|CHARGE|CHARGES|PENALTY)\b""",
        RegexOption.IGNORE_CASE,
    )

    private val referencePattern = Regex(
        """\b(?:UTR|REF(?:ERENCE)?(?:\s{1,3}(?:NO|NUMBER|ID))?|""" +
            """TXN(?:\s{1,3}(?:ID|NO|NUMBER))?|TRANSACTION\s{1,3}(?:ID|NO|NUMBER))""" +
            """[\s:#=-]{0,8}([A-Z0-9](?:[A-Z0-9/-]{4,38}[A-Z0-9])?)""",
        RegexOption.IGNORE_CASE,
    )
    private val labelledAccountPattern = Regex(
        """\b(?:A/C|ACCT|ACCOUNT|CARD)\b""" +
            """(?:\s{1,4}(?:NO|NUMBER|ENDING(?:\s{1,3}IN)?))?""" +
            """[\s:#=-]{0,8}([X*0-9][X*0-9 -]{2,31})(?=\s|[.,;]|$)""",
        RegexOption.IGNORE_CASE,
    )
    private val endingAccountPattern = Regex(
        """\b(?:ENDING|ENDS)\s{1,3}(?:IN\s{1,3})?([0-9]{4})\b""",
        RegexOption.IGNORE_CASE,
    )
    private val maskedAccountPattern = Regex(
        """(?<![A-Z0-9])(?:X{2,12}|\*{2,12})[- ]?([0-9]{4})(?![0-9])""",
        RegexOption.IGNORE_CASE,
    )

    private const val COUNTERPARTY_VALUE = "([A-Z0-9=+@-][A-Z0-9 &.'/_=+@-]{0,62}?)"
    private const val COUNTERPARTY_END =
        "(?=\\s{1,3}(?:ON|VIA|USING|REF|REFERENCE|UTR|TXN|TRANSACTION|FROM|TO|A/C|ACCT|ACCOUNT|CARD|AVAIL|AVAILABLE|BALANCE)\\b|[.,;](?=\\s|$)|$)"
    private val debitCounterpartyPattern = Regex(
        """\b(?:TO|AT)\s{1,4}$COUNTERPARTY_VALUE$COUNTERPARTY_END""",
        RegexOption.IGNORE_CASE,
    )
    private val creditCounterpartyPattern = Regex(
        """\bFROM\s{1,4}$COUNTERPARTY_VALUE$COUNTERPARTY_END""",
        RegexOption.IGNORE_CASE,
    )
    private val accountCounterparty = Regex(
        """^(?:(?:YOUR|OWN)\s{1,3})?(?:A/C|ACCT|ACCOUNT|CARD)\b|^(?:X{1,12}|\*{1,12})[0-9]{4}\b""",
        RegexOption.IGNORE_CASE,
    )

    fun parse(rawText: String, context: ParseContext): ParseResult {
        if (rawText.isBlank()) {
            return ignored(ParseAction.MANUAL_REQUIRED, ParseIssue.EMPTY_OR_REDACTED)
        }
        if (rawText.length > TextNormalizer.MAX_PARSER_INPUT_CHARS) {
            return ignored(ParseAction.MANUAL_REQUIRED, ParseIssue.INPUT_TOO_LONG)
        }

        val normalizedText = TextNormalizer.normalize(rawText)
        if (normalizedText.isBlank()) {
            return ignored(ParseAction.MANUAL_REQUIRED, ParseIssue.EMPTY_OR_REDACTED)
        }

        val hasDebitAction = debitAction.containsMatchIn(normalizedText)
        val hasCreditAction = creditAction.containsMatchIn(normalizedText)
        val hasCompletedAction =
            hasDebitAction || hasCreditAction || genericCompletedAction.containsMatchIn(normalizedText)

        // Security-bearing messages are never eligible for capture, even when a bank mixes
        // transaction language into the same notification. This prevents an OTP from reaching
        // the encrypted review queue or any structured ledger field.
        if (otpOrSecurityMarker.containsMatchIn(normalizedText)) {
            return ignored(ParseAction.IGNORE_SECURITY, ParseIssue.OTP_OR_SECURITY_MESSAGE)
        }
        if (promotionalMarker.containsMatchIn(normalizedText) && !hasCompletedAction) {
            return ignored(ParseAction.IGNORE_PROMO, ParseIssue.PROMOTIONAL_MESSAGE)
        }
        if (negatedOrFutureAction.containsMatchIn(normalizedText)) {
            return ignored(ParseAction.IGNORE_INFO, ParseIssue.PAYMENT_NOT_COMPLETED)
        }
        val hasIncompletePayment = incompletePayment.containsMatchIn(normalizedText)

        val amounts = extractAmounts(normalizedText)
        val balanceCandidates = amounts.filter(AmountCandidate::isBalance)
        val transactionCandidates = amounts.filterNot(AmountCandidate::isBalance)

        if (
            balanceMarker.containsMatchIn(normalizedText) &&
            !hasCompletedAction &&
            transactionCandidates.isEmpty()
        ) {
            return ignored(ParseAction.IGNORE_INFO, ParseIssue.BALANCE_ONLY)
        }

        val distinctTransactionAmounts = transactionCandidates.distinctBy(AmountCandidate::minorUnits)
        val selectedAmount = distinctTransactionAmounts.firstOrNull()
        val multipleAmounts = distinctTransactionAmounts.size > 1
        val malformedAmount = currencyMarker.containsMatchIn(normalizedText) && amounts.isEmpty()
        val implausibleAmount = selectedAmount?.minorUnits?.let {
            it <= 0L || it > MAX_REASONABLE_AMOUNT_MINOR
        } ?: false

        val conflictingDirections = hasDebitAction && hasCreditAction
        val direction = when {
            conflictingDirections -> null
            hasDebitAction -> Direction.DEBIT
            hasCreditAction -> Direction.CREDIT
            else -> null
        }
        val method = extractMethod(normalizedText)
        val accountLast4 = extractAccountLast4(normalizedText)
        val referenceId = referencePattern.find(normalizedText)?.groupValues?.get(1)?.uppercase()
        val counterparty = extractCounterparty(normalizedText, direction)
        val parserRule = parserRuleFor(method)

        val evidence = linkedSetOf<ParseEvidence>()
        if (selectedAmount?.minorUnits?.let { it > 0L } == true) evidence += ParseEvidence.AMOUNT
        if (hasDebitAction || hasCreditAction) evidence += ParseEvidence.DIRECTION
        if (hasCompletedAction) evidence += ParseEvidence.COMPLETED_ACTION
        if (context.sourceIsKnown || method != TransactionMethod.OTHER) {
            evidence += ParseEvidence.KNOWN_SOURCE_OR_RULE
        }
        if (method != TransactionMethod.OTHER || accountLast4 != null) {
            evidence += ParseEvidence.METHOD_OR_ACCOUNT
        }
        if (referenceId != null) evidence += ParseEvidence.REFERENCE
        if (counterparty != null || method == TransactionMethod.ATM) {
            evidence += ParseEvidence.COUNTERPARTY_OR_ATM
        }

        var score = evidence.sumOf(::evidenceWeight)
        if (conflictingDirections) score -= 30
        if (multipleAmounts || malformedAmount || implausibleAmount) score -= 25
        score = score.coerceIn(0, 100)
        val confidence = score / 100.0

        val issues = linkedSetOf<ParseIssue>()
        if (selectedAmount == null || selectedAmount.minorUnits <= 0L) issues += ParseIssue.MISSING_AMOUNT
        if (direction == null) issues += ParseIssue.MISSING_DIRECTION
        if (conflictingDirections) issues += ParseIssue.CONFLICTING_DIRECTIONS
        if (multipleAmounts) issues += ParseIssue.MULTIPLE_TRANSACTION_AMOUNTS
        if (malformedAmount) issues += ParseIssue.MALFORMED_AMOUNT
        if (implausibleAmount) issues += ParseIssue.IMPLAUSIBLE_AMOUNT
        if (hasIncompletePayment) issues += ParseIssue.PAYMENT_NOT_COMPLETED

        val validForAutomaticSync =
            selectedAmount?.minorUnits?.let { it > 0L } == true &&
                direction != null &&
                !conflictingDirections &&
                !multipleAmounts &&
                !malformedAmount &&
                !implausibleAmount &&
                !hasIncompletePayment
        val manualPreviewRequired = context.origin != ParseOrigin.NOTIFICATION
        val incompleteFinancialCard = context.sourceIsKnown && selectedAmount != null &&
            accountLast4 != null && direction == null && !hasIncompletePayment

        val action = when {
            manualPreviewRequired && (selectedAmount != null || hasCompletedAction) -> ParseAction.REVIEW
            confidence >= 0.85 && validForAutomaticSync -> ParseAction.AUTO_SYNC
            incompleteFinancialCard -> ParseAction.REVIEW
            confidence >= 0.60 -> ParseAction.REVIEW
            else -> ParseAction.IGNORE_INFO
        }

        if (action == ParseAction.IGNORE_INFO) {
            issues += ParseIssue.INSUFFICIENT_EVIDENCE
            return ParseResult(
                action = action,
                draft = null,
                confidence = confidence,
                evidence = evidence,
                issues = issues,
            )
        }

        if (manualPreviewRequired) issues += ParseIssue.MANUAL_CONFIRMATION_REQUIRED

        val currency = if (currencyMarker.containsMatchIn(normalizedText)) {
            "INR"
        } else {
            context.normalizedCurrency
        }
        val draft = TransactionDraft(
            amountMinor = selectedAmount?.minorUnits,
            balanceMinor = balanceCandidates.firstOrNull()?.minorUnits,
            currency = currency,
            direction = direction,
            method = method,
            counterparty = counterparty,
            institution = InstitutionResolver.resolve(normalizedText, context.institution),
            accountLast4 = accountLast4,
            referenceId = referenceId,
            eventTime = context.eventTime,
            capturedTime = context.captureTime,
            sourcePackage = context.sourcePackage,
            sourceLabel = context.sourceLabel,
            confidence = confidence,
            parserRule = parserRule,
        )

        return ParseResult(
            action = action,
            draft = draft,
            confidence = confidence,
            evidence = evidence,
            issues = issues,
        )
    }

    private fun ignored(action: ParseAction, issue: ParseIssue): ParseResult = ParseResult(
        action = action,
        draft = null,
        confidence = 0.0,
        issues = setOf(issue),
    )

    private fun evidenceWeight(evidence: ParseEvidence): Int = when (evidence) {
        ParseEvidence.AMOUNT -> 25
        ParseEvidence.DIRECTION -> 20
        ParseEvidence.COMPLETED_ACTION -> 15
        ParseEvidence.KNOWN_SOURCE_OR_RULE -> 10
        ParseEvidence.METHOD_OR_ACCOUNT -> 10
        ParseEvidence.REFERENCE -> 10
        ParseEvidence.COUNTERPARTY_OR_ATM -> 10
    }

    private fun extractAmounts(text: String): List<AmountCandidate> {
        val candidatesByRange = linkedMapOf<IntRange, AmountCandidate>()

        for (pattern in amountPatterns) {
            for (match in pattern.findAll(text)) {
                val amountGroup = match.groups[1] ?: continue
                val minorUnits = parseMinorUnits(amountGroup.value) ?: continue
                val prefixStart = max(0, amountGroup.range.first - 36)
                val precedingText = text.substring(prefixStart, amountGroup.range.first)
                // A balance label only applies until a subsequent amount/action. A short SMS
                // like "Bal INR 500. INR 100 paid" must not lose the second (payment) amount.
                val lastBalance = balanceMarker.findAll(precedingText).lastOrNull()
                val afterBalance = lastBalance?.let { precedingText.substring(it.range.last + 1) }
                val isBalance = afterBalance != null &&
                    afterBalance.none(Char::isDigit) &&
                    !debitAction.containsMatchIn(afterBalance) &&
                    !creditAction.containsMatchIn(afterBalance)
                val existing = candidatesByRange[amountGroup.range]
                candidatesByRange[amountGroup.range] = AmountCandidate(
                    minorUnits = minorUnits,
                    range = amountGroup.range,
                    isBalance = isBalance || existing?.isBalance == true,
                )
            }
        }

        return candidatesByRange.values.sortedBy { it.range.first }
    }

    private fun parseMinorUnits(rawAmount: String): Long? = try {
        BigDecimal(rawAmount.replace(",", ""))
            .movePointRight(2)
            .setScale(0, RoundingMode.UNNECESSARY)
            .longValueExact()
    } catch (_: ArithmeticException) {
        null
    } catch (_: NumberFormatException) {
        null
    }

    private fun extractMethod(text: String): TransactionMethod = when {
        upiMethod.containsMatchIn(text) -> TransactionMethod.UPI
        cardMethod.containsMatchIn(text) -> TransactionMethod.CARD
        bankTransferMethod.containsMatchIn(text) -> TransactionMethod.BANK_TRANSFER
        atmMethod.containsMatchIn(text) -> TransactionMethod.ATM
        cashMethod.containsMatchIn(text) -> TransactionMethod.CASH
        feeMethod.containsMatchIn(text) -> TransactionMethod.FEE
        else -> TransactionMethod.OTHER
    }

    private fun extractAccountLast4(text: String): String? {
        val labelled = labelledAccountPattern.find(text)?.groupValues?.get(1)
        if (labelled != null) {
            val digits = labelled.filter(Char::isDigit)
            if (digits.length >= 4) return digits.takeLast(4)
        }

        endingAccountPattern.find(text)?.groupValues?.get(1)?.let { return it }
        return maskedAccountPattern.find(text)?.groupValues?.get(1)
    }

    private fun extractCounterparty(text: String, direction: Direction?): String? {
        val matches = when (direction) {
            Direction.CREDIT -> creditCounterpartyPattern.findAll(text)
            Direction.DEBIT -> debitCounterpartyPattern.findAll(text)
            null -> debitCounterpartyPattern.findAll(text) + creditCounterpartyPattern.findAll(text)
        }
        return matches.map { it.groupValues[1].trim().trimEnd('.', ',', ';') }
            .firstOrNull { it.isNotBlank() && !accountCounterparty.containsMatchIn(it) }
    }

    private fun parserRuleFor(method: TransactionMethod): String = when (method) {
        TransactionMethod.UPI -> "upi_generic@1.0.0"
        TransactionMethod.CARD, TransactionMethod.CREDIT_CARD, TransactionMethod.DEBIT_CARD -> "card_generic@1.0.0"
        TransactionMethod.BANK_TRANSFER -> "bank_transfer_generic@1.0.0"
        TransactionMethod.AUTO_DEBIT -> "auto_debit_generic@1.0.0"
        TransactionMethod.ATM -> "atm_cash@1.0.0"
        TransactionMethod.CASH -> "cash_deposit@1.0.0"
        TransactionMethod.FEE -> "fee_generic@1.0.0"
        TransactionMethod.OTHER -> "generic_transaction@1.0.0"
    }

    private data class AmountCandidate(
        val minorUnits: Long,
        val range: IntRange,
        val isBalance: Boolean,
    )
}
