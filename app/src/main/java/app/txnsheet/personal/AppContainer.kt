package app.txnsheet.personal

import android.content.Context
import app.txnsheet.personal.capture.SourceAppRegistry
import app.txnsheet.personal.data.local.AppConfigEntity
import app.txnsheet.personal.data.local.TxnSheetDatabase
import app.txnsheet.personal.data.remote.GoogleSheetsHttpGateway
import app.txnsheet.personal.data.remote.PlayServicesGoogleAuthorizationGateway
import app.txnsheet.personal.data.repository.CategoryRuleResolver
import app.txnsheet.personal.data.repository.LocalDataEraser
import app.txnsheet.personal.data.repository.SyncWorkEnqueuer
import app.txnsheet.personal.data.repository.TransactionIngestor
import app.txnsheet.personal.diagnostics.PrivacySafeDiagnostics
import app.txnsheet.personal.security.ReviewPayloadCrypto
import app.txnsheet.personal.sync.LedgerSyncScheduler
import app.txnsheet.personal.sync.WorkbookSetupCoordinator

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database: TxnSheetDatabase = TxnSheetDatabase.create(appContext)
    val diagnostics = PrivacySafeDiagnostics(database.diagnosticDao())
    val reviewPayloadCrypto = ReviewPayloadCrypto()
    val sourceAppRegistry = SourceAppRegistry(database.sourceAppDao())

    val authorizationGateway = PlayServicesGoogleAuthorizationGateway(appContext)
    val sheetsGateway = GoogleSheetsHttpGateway()
    val syncScheduler = LedgerSyncScheduler(appContext)
    val workbookSetupCoordinator = WorkbookSetupCoordinator(
        authorization = authorizationGateway,
        sheets = sheetsGateway,
        configDao = database.configDao(),
        scheduler = syncScheduler,
    )

    val transactionIngestor = TransactionIngestor(
        database = database,
        reviewCrypto = reviewPayloadCrypto,
        diagnostics = diagnostics,
        categoryRules = CategoryRuleResolver(database.categoryRuleDao()),
        syncWorkEnqueuer = SyncWorkEnqueuer(syncScheduler::enqueue),
    )
    val localDataEraser = LocalDataEraser(appContext, database, reviewPayloadCrypto)

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
        val config = database.configDao().get()
        if (!config?.spreadsheetId.isNullOrBlank() &&
            database.syncJobDao().ready(System.currentTimeMillis(), 1).isNotEmpty()
        ) {
            syncScheduler.enqueue()
        }
    }

    private companion object {
        const val DIAGNOSTIC_RETENTION_MILLIS = 30L * 24 * 60 * 60 * 1_000
    }
}
