package app.txnsheet.personal

import android.content.Context
import app.txnsheet.personal.capture.SourceAppRegistry
import app.txnsheet.personal.data.local.AppConfigEntity
import app.txnsheet.personal.data.local.TxnSheetDatabase
import app.txnsheet.personal.data.repository.CategoryRuleResolver
import app.txnsheet.personal.data.repository.LocalDataEraser
import app.txnsheet.personal.data.repository.TransactionIngestor
import app.txnsheet.personal.diagnostics.PrivacySafeDiagnostics
import app.txnsheet.personal.security.ReviewPayloadCrypto

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database: TxnSheetDatabase = TxnSheetDatabase.create(appContext)
    val diagnostics = PrivacySafeDiagnostics(database.diagnosticDao())
    val reviewPayloadCrypto = ReviewPayloadCrypto()
    val sourceAppRegistry = SourceAppRegistry(database.sourceAppDao())

    val transactionIngestor = TransactionIngestor(
        database = database,
        reviewCrypto = reviewPayloadCrypto,
        diagnostics = diagnostics,
        categoryRules = CategoryRuleResolver(database.categoryRuleDao()),
    )
    val localDataEraser = LocalDataEraser(database, reviewPayloadCrypto)

    suspend fun initializeLocalState() {
        val existingConfig = database.configDao().get()
        if (existingConfig == null) {
            database.configDao().upsert(AppConfigEntity(appVersion = BuildConfig.VERSION_NAME))
        } else if (existingConfig.appVersion != BuildConfig.VERSION_NAME) {
            database.configDao().upsert(existingConfig.copy(appVersion = BuildConfig.VERSION_NAME))
        }
        transactionIngestor.purgeExpiredReviewPayloads()
        transactionIngestor.validateReviewEncryptionState()
        database.diagnosticDao().purgeBefore(
            System.currentTimeMillis() - DIAGNOSTIC_RETENTION_MILLIS,
        )
        database.configDao().migrateConfirmedTransactionsToLocal()
        database.syncJobDao().deleteAll()
    }

    private companion object {
        const val DIAGNOSTIC_RETENTION_MILLIS = 30L * 24 * 60 * 60 * 1_000
    }
}
