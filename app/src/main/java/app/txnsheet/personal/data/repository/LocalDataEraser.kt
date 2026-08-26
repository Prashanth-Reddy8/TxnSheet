package app.txnsheet.personal.data.repository

import android.content.Context
import androidx.work.Operation
import androidx.work.WorkManager
import app.txnsheet.personal.data.local.TxnSheetDatabase
import app.txnsheet.personal.security.ReviewPayloadCrypto
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/** Deletes only TxnSheet's local state. It deliberately performs no Google Sheets operation. */
class LocalDataEraser(
    context: Context,
    private val database: TxnSheetDatabase,
    private val reviewCrypto: ReviewPayloadCrypto,
    private val workManager: WorkManager = WorkManager.getInstance(context.applicationContext),
) {
    suspend fun erase() = withContext(Dispatchers.IO) {
        workManager.cancelAllWork().awaitCompletion()
        database.clearAllTables()
        reviewCrypto.deleteKey()
        workManager.pruneWork().awaitCompletion()
    }

    private suspend fun Operation.awaitCompletion() = suspendCancellableCoroutine { continuation ->
        val future = result
        future.addListener(
            {
                try {
                    future.get()
                    continuation.resume(Unit)
                } catch (error: Exception) {
                    continuation.resumeWithException(error)
                }
            },
            DIRECT_EXECUTOR,
        )
        continuation.invokeOnCancellation { future.cancel(true) }
    }

    private companion object {
        val DIRECT_EXECUTOR = Executor(Runnable::run)
    }
}

