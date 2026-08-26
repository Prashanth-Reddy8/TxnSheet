package app.txnsheet.personal.capture

import android.app.Notification
import android.content.pm.PackageManager
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import app.txnsheet.personal.TxnSheetApplication
import app.txnsheet.personal.parsing.ParseContext
import app.txnsheet.personal.parsing.ParseOrigin
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class TransactionNotificationListener : NotificationListenerService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val replayCache = RecentNotificationCache()

    private val appContainer
        get() = (application as TxnSheetApplication).container

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val notification = sbn.notification
        val packageName = sbn.packageName ?: return
        if (packageName == applicationContext.packageName) return
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        if (notification.flags and Notification.FLAG_ONGOING_EVENT != 0) return
        if (notification.category == Notification.CATEGORY_TRANSPORT) return

        // The callback only copies non-message metadata. The extras are read later, and only after
        // the package passes the owner's local allow-list.
        val postedAtEpochMs = sbn.postTime
        serviceScope.launch {
            captureAllowedNotification(packageName, postedAtEpochMs, notification)
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        serviceScope.launch { appContainer.transactionIngestor.purgeExpiredReviewPayloads() }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private suspend fun captureAllowedNotification(
        packageName: String,
        postedAtEpochMs: Long,
        notification: Notification,
    ) {
        try {
            val now = System.currentTimeMillis()
            val label = applicationLabel(packageName)
            val enabled = appContainer.sourceAppRegistry.recordSeenAndCheckEnabled(
                packageName = packageName,
                label = label,
                seenAtEpochMs = now,
            )
            if (!enabled) return

            when (val extracted = NotificationTextExtractor.extract(notification)) {
                NotificationTextExtraction.Empty -> recordSafely("NOTIFICATION_TEXT_EMPTY")
                NotificationTextExtraction.TooLong -> recordSafely("NOTIFICATION_TEXT_TOO_LONG")
                is NotificationTextExtraction.Captured -> {
                    if (!replayCache.shouldProcess(
                            packageName,
                            extracted.text,
                            SystemClock.elapsedRealtime(),
                        )
                    ) return
                    val config = appContainer.database.configDao().get()
                    val capturedAt = Instant.ofEpochMilli(now)
                    val eventAt = postedAtEpochMs
                        .takeIf { it in 1..now + MAX_FUTURE_SKEW_MILLIS }
                        ?.let(Instant::ofEpochMilli)
                        ?: capturedAt
                    appContainer.transactionIngestor.ingest(
                        rawText = extracted.text,
                        context = ParseContext(
                            sourcePackage = packageName,
                            sourceLabel = label,
                            institution = label,
                            captureTime = capturedAt,
                            eventTime = eventAt,
                            origin = ParseOrigin.NOTIFICATION,
                            sourceIsKnown = true,
                            defaultCurrency = config?.currency ?: "INR",
                        ),
                    )
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: RuntimeException) {
            recordSafely("NOTIFICATION_CAPTURE_FAILED")
        }
    }

    private fun applicationLabel(packageName: String): String = try {
        val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
        packageManager.getApplicationLabel(applicationInfo).toString().trim().take(128)
            .ifBlank { packageName }
    } catch (_: PackageManager.NameNotFoundException) {
        packageName
    }

    private suspend fun recordSafely(code: String) {
        try {
            appContainer.diagnostics.record(
                code = code,
                component = "capture",
                severity = "INFO",
            )
        } catch (_: RuntimeException) {
            // Capture remains fail-safe even when diagnostics storage is unavailable.
        }
    }

    private companion object {
        const val MAX_FUTURE_SKEW_MILLIS = 5 * 60_000L
    }
}

