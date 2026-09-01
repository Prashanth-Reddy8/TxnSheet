package app.txnsheet.personal.parsing

import app.txnsheet.personal.domain.Direction
import app.txnsheet.personal.domain.TransactionMethod
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionParserTest {
    private val captureTime = Instant.parse("2026-08-19T08:00:00Z")
    private val notificationContext = ParseContext(
        sourcePackage = "com.example.messaging",
        captureTime = captureTime,
        sourceLabel = "EXAMPLEBK",
        institution = "Example Bank",
        sourceIsKnown = true,
    )

    @Test
    fun `known UPI debit extracts normalized fields and auto syncs`() {
        val result = TransactionParser.parse(
            "INR 1,249.00 debited from A/c XX1234 via UPI to ABC STORE " +
                "on 19-Aug-26. Ref 123456789012.",
            notificationContext,
        )

        assertEquals(ParseAction.AUTO_SYNC, result.action)
        assertEquals(1.0, result.confidence, 0.0)
        with(requireNotNull(result.draft)) {
            assertEquals(124_900L, amountMinor)
            assertEquals("INR", currency)
            assertEquals(Direction.DEBIT, direction)
            assertEquals(TransactionMethod.UPI, method)
            assertEquals("ABC STORE", counterparty)
            assertEquals("1234", accountLast4)
            assertEquals("123456789012", referenceId)
            assertEquals("upi_generic@1.0.0", parserRule)
        }
    }

    @Test
    fun `Kotak sent SMS from Google Messages auto syncs`() {
        val result = TransactionParser.parse(
            "VM-KOTAKB-S Sent Rs.1.00 from Kotak Bank A/c X9899 to Mr PALVAI PRASHANTH " +
                "on 01-09-26. UPI Ref 624400604307. Not done by you? Tap fraud link.",
            notificationContext.copy(
                sourcePackage = "com.google.android.apps.messaging",
                sourceLabel = "Messages",
                institution = "Messages",
            ),
        )

        assertEquals(ParseAction.AUTO_SYNC, result.action)
        with(requireNotNull(result.draft)) {
            assertEquals(100L, amountMinor)
            assertEquals(Direction.DEBIT, direction)
            assertEquals(TransactionMethod.UPI, method)
            assertEquals("9899", accountLast4)
            assertEquals("624400604307", referenceId)
            assertEquals("Mr PALVAI PRASHANTH", counterparty)
        }
    }

    @Test
    fun `known credit extracts bank transfer reference and only final account digits`() {
        val result = TransactionParser.parse(
            "INR 2,500 credited to account 123456789012 via IMPS from RAVI KUMAR. " +
                "UTR ABCD123456.",
            notificationContext,
        )

        assertEquals(ParseAction.AUTO_SYNC, result.action)
        with(requireNotNull(result.draft)) {
            assertEquals(250_000L, amountMinor)
            assertEquals(Direction.CREDIT, direction)
            assertEquals(TransactionMethod.BANK_TRANSFER, method)
            assertEquals("RAVI KUMAR", counterparty)
            assertEquals("9012", accountLast4)
            assertFalse(toString().contains("123456789012"))
            assertEquals("ABCD123456", referenceId)
        }
    }

    @Test
    fun `Indian grouped amount is converted to integer paise`() {
        val result = TransactionParser.parse(
            "₹1,23,456.78 paid via UPI to ACME TRADERS. UTR 1234567890.",
            notificationContext,
        )

        assertEquals(ParseAction.AUTO_SYNC, result.action)
        assertEquals(12_345_678L, result.draft?.amountMinor)
    }

    @Test
    fun `one decimal place is represented exactly in minor units`() {
        val result = TransactionParser.parse(
            "Rs. 799.5 paid using card ending in 4455 at NETFLIX. Ref ID CARDR12345.",
            notificationContext,
        )

        assertEquals(ParseAction.AUTO_SYNC, result.action)
        with(requireNotNull(result.draft)) {
            assertEquals(79_950L, amountMinor)
            assertEquals(TransactionMethod.CARD, method)
            assertEquals("4455", accountLast4)
            assertEquals("CARDR12345", referenceId)
        }
    }

    @Test
    fun `OTP with an amount is rejected before a draft is created`() {
        val result = TransactionParser.parse(
            "Your OTP 123456 is valid for transaction of INR 1,249.00. Do not share.",
            notificationContext,
        )

        assertEquals(ParseAction.IGNORE_SECURITY, result.action)
        assertNull(result.draft)
        assertEquals(0.0, result.confidence, 0.0)
        assertTrue(ParseIssue.OTP_OR_SECURITY_MESSAGE in result.issues)
    }

    @Test
    fun `security text is rejected even when transaction language is present`() {
        val result = TransactionParser.parse(
            "INR 1,249.00 debited via UPI. OTP 123456 is valid for this transaction.",
            notificationContext,
        )

        assertEquals(ParseAction.IGNORE_SECURITY, result.action)
        assertNull(result.draft)
    }

    @Test
    fun `promotional cashback offer is rejected`() {
        val result = TransactionParser.parse(
            "Pre-approved cashback offer: get INR 500. Apply now.",
            notificationContext,
        )

        assertEquals(ParseAction.IGNORE_PROMO, result.action)
        assertNull(result.draft)
    }

    @Test
    fun `completed cashback credit is not mistaken for a promotion`() {
        val result = TransactionParser.parse(
            "INR 500 cashback credited via UPI from ACME. Ref CB123456.",
            notificationContext,
        )

        assertEquals(ParseAction.AUTO_SYNC, result.action)
        assertEquals(Direction.CREDIT, result.draft?.direction)
    }

    @Test
    fun `labelled available balance is separated from transaction amount`() {
        val result = TransactionParser.parse(
            "INR 100.00 debited via UPI to SHOP. Available balance INR 5,000.00. " +
                "Ref 1234567890.",
            notificationContext,
        )

        assertEquals(ParseAction.AUTO_SYNC, result.action)
        assertEquals(10_000L, result.draft?.amountMinor)
        assertEquals(500_000L, result.draft?.balanceMinor)
        assertFalse(ParseIssue.MULTIPLE_TRANSACTION_AMOUNTS in result.issues)
    }

    @Test
    fun `two plausible transaction amounts require review`() {
        val result = TransactionParser.parse(
            "INR 100 debited via UPI to SHOP and INR 200 debited via UPI. Ref 1234567890.",
            notificationContext,
        )

        assertEquals(ParseAction.REVIEW, result.action)
        assertTrue(ParseIssue.MULTIPLE_TRANSACTION_AMOUNTS in result.issues)
        assertTrue(result.confidence in 0.60..<0.85)
    }

    @Test
    fun `conflicting debit and credit language requires review with no guessed direction`() {
        val result = TransactionParser.parse(
            "INR 100 debited and credited via UPI from BANK. Ref CONFLICT123.",
            notificationContext,
        )

        assertEquals(ParseAction.REVIEW, result.action)
        assertNull(result.draft?.direction)
        assertTrue(ParseIssue.CONFLICTING_DIRECTIONS in result.issues)
    }

    @Test
    fun `manual import always remains in preview even at high confidence`() {
        val result = TransactionParser.parse(
            "INR 1,249 debited from A/c XX1234 via UPI to ABC STORE. Ref 123456789012.",
            notificationContext.copy(origin = ParseOrigin.SHARE),
        )

        assertEquals(ParseAction.REVIEW, result.action)
        assertTrue(ParseIssue.MANUAL_CONFIRMATION_REQUIRED in result.issues)
    }

    @Test
    fun `balance-only message and weak informational text are ignored`() {
        val balanceResult = TransactionParser.parse(
            "Available balance INR 5,000.00.",
            notificationContext,
        )
        val weakResult = TransactionParser.parse(
            "INR 100 statement is ready.",
            notificationContext,
        )

        assertEquals(ParseAction.IGNORE_INFO, balanceResult.action)
        assertTrue(ParseIssue.BALANCE_ONLY in balanceResult.issues)
        assertEquals(ParseAction.IGNORE_INFO, weakResult.action)
        assertTrue(ParseIssue.INSUFFICIENT_EVIDENCE in weakResult.issues)
    }

    @Test
    fun `empty redacted and oversized bodies use manual fallback`() {
        val empty = TransactionParser.parse("   ", notificationContext)
        val oversized = TransactionParser.parse(
            "x".repeat(TextNormalizer.MAX_PARSER_INPUT_CHARS + 1),
            notificationContext,
        )

        assertEquals(ParseAction.MANUAL_REQUIRED, empty.action)
        assertTrue(ParseIssue.EMPTY_OR_REDACTED in empty.issues)
        assertEquals(ParseAction.MANUAL_REQUIRED, oversized.action)
        assertTrue(ParseIssue.INPUT_TOO_LONG in oversized.issues)
    }

    @Test
    fun `formula-looking merchant remains literal parser data`() {
        val result = TransactionParser.parse(
            "INR 10 paid via UPI to =HYPERLINK on 19-Aug. Ref FORMULA123.",
            notificationContext,
        )

        assertEquals("=HYPERLINK", result.draft?.counterparty)
    }

    @Test
    fun `normalizer canonicalizes Unicode and whitespace without dropping reference punctuation`() {
        assertEquals(
            "INR 1,249.00 Ref AB-12/34",
            TextNormalizer.normalize("ＩＮＲ\u00A0 1,249.00\nRef\u200B AB-12/34"),
        )
    }
}
