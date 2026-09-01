package app.txnsheet.personal.capture

import android.app.Notification
import app.txnsheet.personal.parsing.TextNormalizer

internal sealed interface NotificationTextExtraction {
    data class Captured(val text: String) : NotificationTextExtraction
    data object Empty : NotificationTextExtraction
    data object TooLong : NotificationTextExtraction
}

/** Converts supported public Notification extras to plain text without retaining spans/bundles. */
internal object NotificationTextExtractor {
    fun extract(notification: Notification): NotificationTextExtraction {
        val extras = notification.extras ?: return NotificationTextExtraction.Empty
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()
        val body = sequenceOf(
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString(),
            extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
                ?.joinToString(" ") { it.toString() },
        )
            .mapNotNull { it?.trim()?.takeIf(String::isNotBlank) }
            // Some apps expose a compact summary in EXTRA_TEXT and the complete bank SMS in
            // MessagingStyle or EXTRA_BIG_TEXT. Prefer the richest visible representation.
            .maxByOrNull(String::length)

        val pieces = buildList {
            if (!title.isNullOrBlank() && body?.contains(title, ignoreCase = true) != true) add(title)
            if (!body.isNullOrBlank()) add(body)
        }
        if (pieces.isEmpty()) return NotificationTextExtraction.Empty
        val text = pieces.joinToString(" ")
        return if (text.length > TextNormalizer.MAX_PARSER_INPUT_CHARS) {
            NotificationTextExtraction.TooLong
        } else {
            NotificationTextExtraction.Captured(text)
        }
    }
}
