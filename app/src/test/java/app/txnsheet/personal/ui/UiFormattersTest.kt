package app.txnsheet.personal.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UiFormattersTest {
    @Test
    fun `review preview hides security text and long identifiers`() {
        val redacted = redactSourceText(
            "Your OTP 123456 is valid. A/c 123456789012 debited INR 10.00. Ref 998877665544.",
        )

        assertFalse(redacted.contains("123456"))
        assertFalse(redacted.contains("123456789012"))
        assertFalse(redacted.contains("998877665544"))
        assertTrue(redacted.contains("••••9012"))
        assertTrue(redacted.contains("••••5544"))
    }

    @Test
    fun `review preview has a strict display cap`() {
        assertTrue(redactSourceText("x".repeat(2_000)).length <= 1_200)
    }
}
