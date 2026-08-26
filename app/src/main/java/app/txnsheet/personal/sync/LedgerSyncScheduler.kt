package app.txnsheet.personal.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/** Keeps every ledger mutation in one serialized WorkManager chain. */
class LedgerSyncScheduler(
    context: Context,
    private val workManager: WorkManager = WorkManager.getInstance(context.applicationContext),
) {
    /** Wakes the queue without creating parallel writers. Safe to call after every local insert. */
    fun enqueue() {
        ensureReconciliation()
        workManager.enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request(delayMillis = 0),
        )
    }

    fun cancel() {
        workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
        workManager.cancelUniqueWork(RECONCILIATION_WORK_NAME)
    }

    /** Covers the narrow insert-after-final-query race and resumes durable jobs after reboot. */
    fun ensureReconciliation() {
        val request = PeriodicWorkRequestBuilder<SyncReconciliationWorker>(
            RECONCILIATION_INTERVAL_MINUTES,
            TimeUnit.MINUTES,
        ).addTag(WORK_TAG).build()
        workManager.enqueueUniquePeriodicWork(
            RECONCILIATION_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    internal fun enqueueContinuation(delayMillis: Long = 0) {
        workManager.enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request(delayMillis.coerceAtLeast(0)),
        )
    }

    private fun request(delayMillis: Long): OneTimeWorkRequest =
        OneTimeWorkRequestBuilder<LedgerSyncWorker>()
            .setConstraints(NETWORK_CONSTRAINTS)
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS,
            )
            .addTag(WORK_TAG)
            .build()

    companion object {
        const val UNIQUE_WORK_NAME = "txnsheet-ledger-sync"
        const val RECONCILIATION_WORK_NAME = "txnsheet-sync-reconciliation"
        const val WORK_TAG = "txnsheet-sync"
        private const val MIN_BACKOFF_MILLIS = 30_000L
        private const val RECONCILIATION_INTERVAL_MINUTES = 15L
        private val NETWORK_CONSTRAINTS = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
    }
}
