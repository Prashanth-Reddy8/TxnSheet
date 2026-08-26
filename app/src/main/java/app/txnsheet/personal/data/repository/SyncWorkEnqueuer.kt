package app.txnsheet.personal.data.repository

/** Small boundary that keeps local ingestion independent of the Sheets/WorkManager implementation. */
fun interface SyncWorkEnqueuer {
    fun enqueue()

    companion object {
        val NONE = SyncWorkEnqueuer { }
    }
}

