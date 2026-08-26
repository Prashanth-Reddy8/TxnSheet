package app.txnsheet.personal.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/** Periodic trigger only; all network writes remain in the serialized ledger work chain. */
class SyncReconciliationWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        LedgerSyncScheduler(applicationContext).enqueue()
        return Result.success()
    }
}
