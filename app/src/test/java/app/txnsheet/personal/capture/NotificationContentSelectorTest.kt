package app.txnsheet.personal.capture

import app.txnsheet.personal.parsing.ParseAction
import app.txnsheet.personal.parsing.ParseContext
import app.txnsheet.personal.parsing.TextNormalizer
import app.txnsheet.personal.parsing.TransactionParser
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationContentSelectorTest {
    @Test
    fun `bundled messages remain separate parseable transactions with their own event time`() {
        val result = select(messages = listOf(
            NotificationMessageText("INR 100 paid via UPI to SHOP. Ref ABC123456.", 1000),
            NotificationMessageText("INR 200 credited via UPI from RAVI. Ref DEF123456.", 2000),
        ))
        assertEquals(listOf(1000L, 2000L), result.messages.map { it.timestampEpochMs })
        val context = ParseContext("bank.app", Instant.EPOCH, sourceIsKnown = true)
        val parsed = result.messages.map { TransactionParser.parse(it.text, context) }
        assertTrue(parsed.all { it.action == ParseAction.AUTO_SYNC })
        assertEquals(listOf(10_000L, 20_000L), parsed.map { it.draft?.amountMinor })
    }

    @Test
    fun `one oversized historical message cannot hide a valid latest payment`() {
        val result = select(messages = listOf(
            NotificationMessageText("x".repeat(TextNormalizer.MAX_PARSER_INPUT_CHARS + 1), 1000),
            NotificationMessageText("INR 20 paid", 2000),
        ))
        assertEquals(1, result.oversizedCount)
        assertEquals(listOf(NotificationMessageText("BANK INR 20 paid", 2000)), result.messages)
    }

    @Test
    fun `blank structured entries fall back to rich visible text`() {
        val result = select(
            messages = listOf(NotificationMessageText(" ")),
            bigText = "INR 300 debited via UPI to GROCER. Ref ABC123456.",
            text = "Payment update",
        )
        assertTrue(result.messages.single().text.contains("GROCER"))
    }

    @Test
    fun `same body at separate timestamps is preserved and exact repeats are collapsed`() {
        val result = select(messages = listOf(
            NotificationMessageText("INR 20 paid", 1000),
            NotificationMessageText("INR 20 paid", 1000),
            NotificationMessageText("INR 20 paid", 2000),
        ))
        assertEquals(2, result.messages.size)
    }

    @Test
    fun `known title is not duplicated and empty input stays empty`() {
        assertEquals("BANK INR 20 paid", select(text = "BANK INR 20 paid").messages.single().text)
        assertTrue(NotificationContentSelector.select(null, emptyList(), null, null, emptyList()).messages.isEmpty())
    }

    private fun select(
        messages: List<NotificationMessageText> = emptyList(),
        bigText: String? = null,
        text: String? = null,
    ) = NotificationContentSelector.select("BANK", messages, bigText, text, emptyList())
}
