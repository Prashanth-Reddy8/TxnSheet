package app.txnsheet.personal.capture

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentNotificationCacheTest {
    @Test
    fun repeatedContentWithinWindowIsSuppressed() {
        val cache = RecentNotificationCache(capacity = 8, ttlMillis = 1_000)

        assertTrue(cache.shouldProcess("bank.app", "Paid INR 10", 100))
        assertFalse(cache.shouldProcess("bank.app", "Paid INR 10", 200))
        assertTrue(cache.shouldProcess("other.app", "Paid INR 10", 200))
    }

    @Test
    fun contentCanBeProcessedAfterWindowExpires() {
        val cache = RecentNotificationCache(capacity = 8, ttlMillis = 1_000)

        assertTrue(cache.shouldProcess("bank.app", "Paid INR 10", 100))
        assertTrue(cache.shouldProcess("bank.app", "Paid INR 10", 1_101))
    }

    @Test
    fun cacheRemainsBoundedAndEvictsOldestDigest() {
        val cache = RecentNotificationCache(capacity = 2, ttlMillis = 10_000)

        assertTrue(cache.shouldProcess("bank.app", "one", 100))
        assertTrue(cache.shouldProcess("bank.app", "two", 101))
        assertTrue(cache.shouldProcess("bank.app", "three", 102))
        assertTrue(cache.shouldProcess("bank.app", "one", 103))
    }
}

