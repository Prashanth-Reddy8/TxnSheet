package app.txnsheet.personal.data.repository

import app.txnsheet.personal.data.local.TxnSheetDatabase
import app.txnsheet.personal.security.ReviewPayloadCrypto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Deletes every piece of data owned by TxnSheet from this device. */
class LocalDataEraser(
    private val database: TxnSheetDatabase,
    private val reviewCrypto: ReviewPayloadCrypto,
) {
    suspend fun erase() = withContext(Dispatchers.IO) {
        database.clearAllTables()
        reviewCrypto.deleteKey()
    }
}
