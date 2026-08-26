package app.txnsheet.personal.capture

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/** Bounded, memory-only replay suppression. Only SHA-256 digests are retained. */
internal class RecentNotificationCache(
    private val capacity: Int = 128,
    private val ttlMillis: Long = 120_000L,
) {
    private val recent = object : LinkedHashMap<String, Long>(capacity, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean =
            size > capacity
    }

    @Synchronized
    fun shouldProcess(packageName: String, text: String, nowElapsedMillis: Long): Boolean {
        recent.entries.removeAll { (_, seenAt) -> nowElapsedMillis - seenAt > ttlMillis }
        val digest = sha256("$packageName\u0000$text")
        if (recent.containsKey(digest)) return false
        recent[digest] = nowElapsedMillis
        return true
    }

    private fun sha256(value: String): String = MessageDigest
        .getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
}

