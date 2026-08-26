package app.txnsheet.personal.parsing

import app.txnsheet.personal.domain.Direction
import app.txnsheet.personal.domain.TransactionDraft
import app.txnsheet.personal.domain.TransactionMethod
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FingerprintFactoryTest {
    private val baseTime = Instant.parse("2026-08-19T08:00:10Z")

    @Test
    fun `reference fingerprint is stable across source text and capture time`() {
        val draft = draft(referenceId = "UTR-123456")

        val first = FingerprintFactory.create(
            draft = draft,
            sourcePackage = "com.example.sms",
            normalizedText = "first body",
            captureTime = baseTime,
        )
        val retried = FingerprintFactory.create(
            draft = draft,
            sourcePackage = "com.changed.source",
            normalizedText = "different body",
            captureTime = baseTime.plusSeconds(600),
        )

        assertEquals(first, retried)
        assertTrue(first.matches(Regex("[0-9a-f]{64}")))
    }

    @Test
    fun `fallback fingerprint canonicalizes text and collapses events within capture minute`() {
        val draft = draft(referenceId = null)

        val first = FingerprintFactory.create(
            draft,
            "com.example.sms",
            "INR 100   debited\nvia UPI",
            baseTime,
        )
        val normalizedDuplicate = FingerprintFactory.create(
            draft,
            "com.example.sms",
            "ＩＮＲ 100 debited via UPI",
            baseTime.plusSeconds(40),
        )

        assertEquals(first, normalizedDuplicate)
    }

    @Test
    fun `fallback fingerprint changes for another minute source or normalized body`() {
        val draft = draft(referenceId = null)
        val original = FingerprintFactory.create(
            draft,
            "com.example.sms",
            "INR 100 debited",
            baseTime,
        )

        assertNotEquals(
            original,
            FingerprintFactory.create(draft, "com.example.sms", "INR 100 debited", baseTime.plusSeconds(60)),
        )
        assertNotEquals(
            original,
            FingerprintFactory.create(draft, "com.example.bank", "INR 100 debited", baseTime),
        )
        assertNotEquals(
            original,
            FingerprintFactory.create(draft, "com.example.sms", "INR 101 debited", baseTime),
        )
    }

    @Test
    fun `incomplete review draft cannot be fingerprinted`() {
        assertThrows(IllegalArgumentException::class.java) {
            FingerprintFactory.create(
                draft(referenceId = null).copy(direction = null),
                "com.example.sms",
                "INR 100",
                baseTime,
            )
        }
    }

    private fun draft(referenceId: String?): TransactionDraft = TransactionDraft(
        amountMinor = 10_000L,
        currency = "INR",
        direction = Direction.DEBIT,
        method = TransactionMethod.UPI,
        institution = "Example Bank",
        accountLast4 = "1234",
        referenceId = referenceId,
        eventTime = baseTime,
        capturedTime = baseTime,
        sourcePackage = "com.example.sms",
        confidence = 1.0,
        parserRule = "upi_generic@1.0.0",
    )
}
