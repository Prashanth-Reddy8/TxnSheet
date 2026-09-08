package app.txnsheet.personal.capture

import app.txnsheet.personal.parsing.TextNormalizer

internal data class NotificationMessageText(val text: String, val timestampEpochMs: Long? = null)

internal data class NotificationTextBatch(
    val messages: List<NotificationMessageText>,
    val oversizedCount: Int = 0,
)

/** Pure selection policy so bundled SMS, summaries, and oversized entries can be regression tested. */
internal object NotificationContentSelector {
    const val MAX_MESSAGES = 50

    fun select(
        title: String?,
        messages: List<NotificationMessageText>,
        bigText: String?,
        text: String?,
        lines: List<String>,
    ): NotificationTextBatch {
        val structured = messages.takeLast(MAX_MESSAGES).filter { it.text.isNotBlank() }
        val candidates = if (structured.isNotEmpty()) {
            // MessagingStyle includes previous SMS. Never join those into a single transaction:
            // two valid messages can otherwise become conflicting amounts or directions.
            structured
        } else {
            val body = sequenceOf(bigText, text, lines.joinToString(" ").takeIf(String::isNotBlank))
                .mapNotNull { it?.trim()?.takeIf(String::isNotBlank) }
                .maxByOrNull(String::length)
            listOf(NotificationMessageText(body.orEmpty()))
        }
        var oversizedCount = 0
        val safeTitle = title?.trim()?.takeIf(String::isNotBlank)
        val result = candidates.mapNotNull { message ->
            val body = message.text.trim()
            val combined = when {
                safeTitle == null -> body
                body.contains(safeTitle, ignoreCase = true) -> body
                body.isEmpty() -> safeTitle
                else -> "$safeTitle $body"
            }
            when {
                combined.isBlank() -> null
                combined.length > TextNormalizer.MAX_PARSER_INPUT_CHARS -> {
                    oversizedCount++
                    null
                }
                else -> message.copy(text = combined)
            }
        }.distinctBy { it.timestampEpochMs to it.text }
        return NotificationTextBatch(result, oversizedCount)
    }
}
