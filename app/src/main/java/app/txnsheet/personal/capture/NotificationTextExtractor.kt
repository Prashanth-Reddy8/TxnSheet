package app.txnsheet.personal.capture

import android.app.Notification
import android.os.Bundle
/** Reads public extras only after the listener checks the owner's source allow-list. */
internal object NotificationTextExtractor {
    fun extract(notification: Notification): NotificationTextBatch {
        val extras = notification.extras ?: return NotificationTextBatch(emptyList())
        val messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
            .orEmpty()
            .takeLast(NotificationContentSelector.MAX_MESSAGES)
            .mapNotNull { item ->
                val bundle = item as? Bundle ?: return@mapNotNull null
                val text = bundle.getCharSequence("text")?.toString() ?: return@mapNotNull null
                NotificationMessageText(text, bundle.getLong("time").takeIf { it > 0 })
            }
        return NotificationContentSelector.select(
            title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            messages = messages,
            bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString(),
            text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
                .orEmpty().takeLast(NotificationContentSelector.MAX_MESSAGES).map(CharSequence::toString),
        )
    }
}
