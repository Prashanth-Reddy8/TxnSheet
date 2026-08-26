package app.txnsheet.personal

import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationManagerCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import app.txnsheet.personal.ui.TxnSheetRoot
import app.txnsheet.personal.ui.TxnSheetViewModel
import app.txnsheet.personal.ui.theme.TxnSheetTheme
import app.txnsheet.personal.capture.TransactionNotificationListener

class MainActivity : ComponentActivity() {
    private val viewModel: TxnSheetViewModel by viewModels()
    private var sharedText by mutableStateOf<String?>(null)

    private val googleAuthorizationLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        viewModel.completeGoogleAuthorization(result.data)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ),
            navigationBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ),
        )
        sharedText = intent.sharedPlainText()
        setContent {
            TxnSheetTheme(useDynamicColor = false) {
                TxnSheetRoot(
                    viewModel = viewModel,
                    sharedText = sharedText,
                    onSharedTextConsumed = { sharedText = null },
                    onLaunchGoogleAuthorization = ::launchGoogleAuthorization,
                    onOpenNotificationSettings = ::openNotificationSettings,
                    onOpenSpreadsheet = ::openSpreadsheet,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        sharedText = intent.sharedPlainText()
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshSystemReadiness(
            NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName),
        )
    }

    private fun launchGoogleAuthorization(pendingIntent: PendingIntent) {
        googleAuthorizationLauncher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
    }

    private fun openNotificationSettings() {
        val detailIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
                Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                ComponentName(this, TransactionNotificationListener::class.java).flattenToString(),
            )
        } else {
            null
        }
        try {
            startActivity(detailIntent ?: Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        } catch (_: ActivityNotFoundException) {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
    }

    private fun openSpreadsheet(spreadsheetId: String) {
        val safeId = spreadsheetId.takeIf { it.matches(Regex("[A-Za-z0-9_-]{10,200}")) } ?: return
        startActivity(
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://docs.google.com/spreadsheets/d/$safeId/edit"),
            ),
        )
    }

    private fun Intent.sharedPlainText(): String? =
        takeIf { action == Intent.ACTION_SEND && type == "text/plain" }
            ?.getStringExtra(Intent.EXTRA_TEXT)
            ?.trim()
            ?.take(8_000)
            ?.takeIf(String::isNotBlank)
}
