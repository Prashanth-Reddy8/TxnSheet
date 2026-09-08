package app.txnsheet.personal.capture

import android.app.Notification
import android.content.pm.PackageManager
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import app.txnsheet.personal.TxnSheetApplication
import app.txnsheet.personal.data.repository.IngestionResult
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
    private val serviceStartedAtEpochMs = System.currentTimeMillis()

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
        serviceScope.launch {
            captureAllowedNotification(packageName, sbn.key, sbn.postTime, notification)
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        serviceScope.launch {
            discoverActiveNotificationSources()
            appContainer.transactionIngestor.purgeExpiredReviewPayloads()
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private suspend fun captureAllowedNotification(
        packageName: String,
        notificationKey: String,
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

            val extracted = NotificationTextExtractor.extract(notification)
            if (extracted.oversizedCount > 0) recordSafely("NOTIFICATION_TEXT_TOO_LONG")
            if (extracted.messages.isEmpty() && extracted.oversizedCount == 0) {
                recordSafely("NOTIFICATION_TEXT_EMPTY")
            }
            val config = appContainer.database.configDao().get()
            for (message in extracted.messages) {
                val eventMillis = sequenceOf(message.timestampEpochMs, notification.`when`, postedAtEpochMs)
                    .filterNotNull()
                    .firstOrNull { it in 1..now + MAX_FUTURE_SKEW_MILLIS }
                    ?: now
                val identity = "$notificationKey|$eventMillis"
                if (!replayCache.shouldProcess(
                        packageName, message.text, SystemClock.elapsedRealtime(), identity,
                    )
                ) continue
                try {
                    val result = appContainer.transactionIngestor.ingest(
                        rawText = message.text,
                        context = ParseContext(
                            sourcePackage = packageName,
                            sourceLabel = label,
                            // Messages/Truecaller identifies the display source, not the bank.
                            institution = null,
                            captureTime = Instant.ofEpochMilli(now),
                            eventTime = Instant.ofEpochMilli(eventMillis),
                            origin = ParseOrigin.NOTIFICATION,
                            sourceIsKnown = true,
                            defaultCurrency = config?.currency ?: "INR",
                            notificationIdentity = identity,
                        ),
                    )
                    if (result == IngestionResult.FailedSafely) {
                        replayCache.forget(packageName, message.text, identity)
                    }
                } catch (cancellation: CancellationException) {
                    replayCache.forget(packageName, message.text, identity)
                    throw cancellation
                } catch (_: RuntimeException) {
                    replayCache.forget(packageName, message.text, identity)
                    recordSafely("NOTIFICATION_CAPTURE_FAILED")
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: RuntimeException) {
            recordSafely("NOTIFICATION_CAPTURE_FAILED")
        }
    }

    /**
     * Populate the owner's source picker as soon as Android connects the listener. Only package
     * metadata is inspected here; notification text remains unread until that source is enabled.
     * This also makes already-visible bank/payment notifications discoverable after installation.
     */
    private suspend fun discoverActiveNotificationSources() {
        try {
            val notifications = activeNotifications.orEmpty()
            notifications
                .asSequence()
                .mapNotNull(StatusBarNotification::getPackageName)
                .filterNot { it == applicationContext.packageName }
                .distinct()
                .forEach { packageName ->
                    appContainer.sourceAppRegistry.recordSeenAndCheckEnabled(
                        packageName = packageName,
                        label = applicationLabel(packageName),
                        seenAtEpochMs = System.currentTimeMillis(),
                    )
                }
            // Recover alerts posted during a temporary listener disconnect. Older visible alerts
            // are only discovered, avoiding a one-time reimport of pre-upgrade legacy fingerprints.
            notifications.filter { it.postTime >= serviceStartedAtEpochMs }.forEach(::onNotificationPosted)
        } catch (_: RuntimeException) {
            recordSafely("NOTIFICATION_SOURCE_DISCOVERY_FAILED")
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
