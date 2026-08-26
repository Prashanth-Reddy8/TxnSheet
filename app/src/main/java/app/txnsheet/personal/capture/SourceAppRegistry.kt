package app.txnsheet.personal.capture

import app.txnsheet.personal.data.local.SourceAppDao

/** Records source metadata while preserving a default-deny, owner-controlled allow-list. */
class SourceAppRegistry(private val dao: SourceAppDao) {
    suspend fun recordSeenAndCheckEnabled(
        packageName: String,
        label: String,
        seenAtEpochMs: Long,
    ): Boolean = dao.recordSeen(
        packageName = packageName,
        label = label.take(MAX_LABEL_LENGTH),
        seenAtEpochMs = seenAtEpochMs,
    )

    private companion object {
        const val MAX_LABEL_LENGTH = 128
    }
}

