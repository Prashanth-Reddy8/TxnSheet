package app.txnsheet.personal.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PieChartOutline
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Badge
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.txnsheet.personal.ui.screens.ActivityScreen
import app.txnsheet.personal.ui.screens.DiagnosticsScreen
import app.txnsheet.personal.ui.screens.HomeScreen
import app.txnsheet.personal.ui.screens.FinanceHomeScreen
import app.txnsheet.personal.ui.screens.PlanningScreen
import app.txnsheet.personal.ui.screens.WorkbookScreen
import app.txnsheet.personal.ui.screens.WorkbookImportDialog
import java.time.YearMonth
import app.txnsheet.personal.ui.screens.ManualImportScreen
import app.txnsheet.personal.ui.screens.OnboardingScreen
import app.txnsheet.personal.ui.screens.PrivacyScreen
import app.txnsheet.personal.ui.screens.ReviewDetailScreen
import app.txnsheet.personal.ui.screens.ReviewQueueScreen
import app.txnsheet.personal.ui.screens.RulesScreen
import app.txnsheet.personal.ui.screens.SettingsScreen
import app.txnsheet.personal.ui.screens.SourcesScreen
import app.txnsheet.personal.ui.screens.TransactionDetailScreen
import app.txnsheet.personal.ui.theme.TxnMotion

@Composable
fun TxnSheetRoot(
    viewModel: TxnSheetViewModel,
    sharedText: String?,
    onSharedTextConsumed: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    var showNotificationDisclosure by rememberSaveable { mutableStateOf(false) }
    var pendingManualText by rememberSaveable { mutableStateOf(sharedText.orEmpty()) }
    val workbookPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::previewWorkbook)
    }

    LaunchedEffect(viewModel, navController) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is UiEffect.NavigateToTransaction -> {
                    pendingManualText = ""
                    navController.navigate(
                        if (effect.review) Routes.reviewDetail(effect.transactionId)
                        else Routes.detail(effect.transactionId),
                    ) { launchSingleTop = true }
                }
                is UiEffect.Message -> snackbarHostState.showSnackbar(effect.text)
                UiEffect.CloseCurrent -> navController.popBackStack()
            }
        }
    }

    LaunchedEffect(sharedText, state.loading, state.config.onboardingComplete) {
        if (!sharedText.isNullOrBlank() && !state.loading && state.config.onboardingComplete) {
            pendingManualText = sharedText
            navController.navigate(Routes.MANUAL) { launchSingleTop = true }
            onSharedTextConsumed()
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when {
            state.loading -> Box(Modifier.fillMaxSize())
            !state.config.onboardingComplete -> OnboardingScreen(
                notificationAccessGranted = state.notificationAccessGranted,
                onOpenNotificationAccess = { showNotificationDisclosure = true },
                onFinish = viewModel::completeOnboarding,
                modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
            )
            else -> MainNavigation(
                state = state,
                viewModel = viewModel,
                navController = navController,
                snackbarHostState = snackbarHostState,
                manualInitialText = pendingManualText,
                onClearManualText = { pendingManualText = "" },
                onRequestNotificationAccess = { showNotificationDisclosure = true },
                onImportWorkbook = { workbookPicker.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) },
            )
        }
    }

    if (showNotificationDisclosure) {
        NotificationAccessDisclosure(
            onDismiss = { showNotificationDisclosure = false },
            onContinue = {
                showNotificationDisclosure = false
                onOpenNotificationSettings()
            },
        )
    }
    state.workbookPreview?.let { preview ->
        WorkbookImportDialog(preview, state.actionInProgress, viewModel::importWorkbook, viewModel::dismissWorkbookPreview)
    }
}

@Composable
private fun MainNavigation(
    state: TxnSheetUiState,
    viewModel: TxnSheetViewModel,
    navController: NavHostController,
    snackbarHostState: SnackbarHostState,
    manualInitialText: String,
    onClearManualText: () -> Unit,
    onRequestNotificationAccess: () -> Unit,
    onImportWorkbook: () -> Unit,
) {
    var merchantQuery by rememberSaveable { mutableStateOf("") }
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val topLevel = TopDestination.entries.firstOrNull { it.route == route }
    val showChrome = topLevel != null

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
        ),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            AnimatedVisibility(visible = showChrome) {
                NavigationBar(windowInsets = WindowInsets.navigationBars) {
                    TopDestination.entries.forEach { destination ->
                        NavigationBarItem(
                            selected = route == destination.route,
                            onClick = { navController.navigateTopLevel(destination.route) },
                            icon = {
                                val count = when (destination) {
                                    TopDestination.REVIEW -> state.reviewTransactions.size
                                    else -> 0
                                }
                                BadgedBox(
                                    badge = {
                                        if (count > 0) Badge { Text(if (count > 99) "99+" else count.toString()) }
                                    },
                                ) {
                                    Icon(destination.icon, contentDescription = null)
                                }
                            },
                            label = { Text(destination.label) },
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            AnimatedVisibility(visible = route == Routes.HOME || route == Routes.ACTIVITY) {
                FloatingActionButton(onClick = { navController.navigate(Routes.MANUAL) }) {
                    Icon(Icons.Rounded.Add, contentDescription = "Add transaction")
                }
            }
        },
    ) { scaffoldPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(
                top = scaffoldPadding.calculateTopPadding(),
                bottom = if (showChrome) scaffoldPadding.calculateBottomPadding() else 0.dp,
            ),
            enterTransition = {
                fadeIn(tween(TxnMotion.short)) + slideInHorizontally(tween(TxnMotion.navigation)) { it / 12 }
            },
            exitTransition = { fadeOut(tween(TxnMotion.micro)) },
            popEnterTransition = { fadeIn(tween(TxnMotion.short)) },
            popExitTransition = {
                fadeOut(tween(TxnMotion.micro)) + slideOutHorizontally(tween(TxnMotion.short)) { it / 12 }
            },
        ) {
            composable(Routes.HOME) {
                FinanceHomeScreen(
                    state = state,
                    onMonth = viewModel::selectMonth,
                    onActivity = { merchantQuery = ""; navController.navigateTopLevel(Routes.ACTIVITY) },
                    onMerchant = { merchant -> merchantQuery = merchant; navController.navigateTopLevel(Routes.ACTIVITY) },
                    onPlan = { navController.navigateTopLevel(Routes.PLAN) },
                    onReview = { navController.navigateTopLevel(Routes.REVIEW) },
                    onSettings = { navController.navigateTopLevel(Routes.SETTINGS) },
                    onTransaction = { id -> openTransaction(navController, state, id) },
                )
            }
            composable(Routes.ACTIVITY) {
                ActivityScreen(
                    state = state,
                    onOpenTransaction = { id -> openTransaction(navController, state, id) },
                    initialQuery = merchantQuery,
                    onMonth = viewModel::selectMonth,
                )
            }
            composable(Routes.PLAN) {
                PlanningScreen(
                    transactions = state.transactions, finance = state.finance,
                    month = YearMonth.parse(state.selectedMonth), zoneId = state.config.timezone, currency = state.config.currency,
                    onSaveBudget = viewModel::saveBudget, onSaveDebt = viewModel::saveDebt, onDeleteDebt = viewModel::deleteDebt,
                    onSaveGoal = viewModel::saveGoal, onDeleteGoal = viewModel::deleteGoal, onSavePreferences = viewModel::savePreferences,
                )
            }
            composable(Routes.REVIEW) {
                ReviewQueueScreen(
                    state = state,
                    onOpenReview = { id -> navController.navigate(Routes.reviewDetail(id)) },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    state = state,
                    onOpenSources = { navController.navigate(Routes.SOURCES) },
                    onOpenRules = { navController.navigate(Routes.RULES) },
                    onOpenPrivacy = { navController.navigate(Routes.PRIVACY) },
                    onOpenDiagnostics = { navController.navigate(Routes.DIAGNOSTICS) },
                    onImportWorkbook = onImportWorkbook,
                    onOpenWorkbook = { navController.navigate(Routes.WORKBOOK) },
                )
            }
            composable(Routes.MANUAL) {
                ManualImportScreen(
                    initialText = manualInitialText,
                    currency = state.config.currency,
                    zoneId = state.config.timezone,
                    working = state.actionInProgress,
                    onBack = {
                        onClearManualText()
                        navController.popBackStack()
                    },
                    onImport = { text -> viewModel.ingestManual(text, manualInitialText.isNotBlank()) },
                    onCreateManual = viewModel::createManualTransaction,
                )
            }
            composable(
                route = Routes.DETAIL,
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { entry ->
                val id = entry.arguments?.getString("id").orEmpty()
                TransactionDetailScreen(
                    transaction = state.transactions.firstOrNull { it.transactionId == id },
                    zoneId = state.config.timezone,
                    working = state.actionInProgress,
                    onBack = { navController.popBackStack() },
                    onSave = { edits -> viewModel.saveTransactionEdits(id, edits) },
                    onDelete = { viewModel.deleteLocalTransaction(id) },
                    annotation = state.finance.annotations.firstOrNull { it.transactionId == id },
                    onSaveAnnotation = viewModel::saveAnnotation,
                    onRememberMerchant = { viewModel.rememberMerchant(id) },
                )
            }
            composable(
                route = Routes.REVIEW_DETAIL,
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { entry ->
                val id = entry.arguments?.getString("id").orEmpty()
                LaunchedEffect(id) { viewModel.loadReviewSource(id) }
                ReviewDetailScreen(
                    transaction = state.transactions.firstOrNull { it.transactionId == id },
                    sourceText = state.reviewSourceById[id],
                    working = state.actionInProgress,
                    onBack = {
                        viewModel.clearReviewSource(id)
                        navController.popBackStack()
                    },
                    onApprove = { submission -> viewModel.acceptReview(id, submission) },
                    onDiscard = { viewModel.discardReview(id) },
                )
            }
            composable(Routes.SOURCES) {
                SourcesScreen(
                    sources = state.sources,
                    notificationAccessGranted = state.notificationAccessGranted,
                    onBack = { navController.popBackStack() },
                    onOpenNotificationAccess = onRequestNotificationAccess,
                    onToggle = { source, enabled -> viewModel.setSourceEnabled(source.packageName, enabled) },
                )
            }
            composable(Routes.RULES) {
                RulesScreen(
                    rules = state.categoryRules,
                    onBack = { navController.popBackStack() },
                    onAddRule = viewModel::addCategoryRule,
                    onDeleteRule = viewModel::deleteCategoryRule,
                )
            }
            composable(Routes.PRIVACY) {
                PrivacyScreen(
                    onBack = { navController.popBackStack() },
                    onEraseLocalData = viewModel::eraseLocalData,
                )
            }
            composable(Routes.DIAGNOSTICS) {
                DiagnosticsScreen(
                    diagnostics = state.diagnostics,
                    zoneId = state.config.timezone,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.WORKBOOK) {
                WorkbookScreen(state.finance.workbook, onBack = { navController.popBackStack() }, onImport = onImportWorkbook)
            }
        }
    }
}

@Composable
private fun NotificationAccessDisclosure(onDismiss: () -> Unit, onContinue: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Before you enable notification access") },
        text = {
            Text(
                "Android will allow TxnSheet to see notification content. TxnSheet processes only sources you explicitly enable, on this phone, to detect completed transactions. OTP, verification, promotional and unrelated notifications are rejected. Nothing is uploaded; encrypted source text is kept only for uncertain review items and removed within seven days. You can revoke access in Android settings or erase all local data in TxnSheet at any time.",
            )
        },
        confirmButton = { TextButton(onClick = onContinue) { Text("Continue to Android settings") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } },
    )
}

private fun NavHostController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun openTransaction(navController: NavHostController, state: TxnSheetUiState, id: String) {
    val transaction = state.transactions.firstOrNull { it.transactionId == id }
    navController.navigate(if (transaction?.status == "REVIEW") Routes.reviewDetail(id) else Routes.detail(id))
}

private enum class TopDestination(val route: String, val label: String, val icon: ImageVector) {
    HOME(Routes.HOME, "Home", Icons.Outlined.Home),
    ACTIVITY(Routes.ACTIVITY, "Activity", Icons.Outlined.ReceiptLong),
    PLAN(Routes.PLAN, "Plan", Icons.Outlined.PieChartOutline),
    REVIEW(Routes.REVIEW, "Review", Icons.Outlined.Visibility),
    SETTINGS(Routes.SETTINGS, "Settings", Icons.Outlined.Settings),
}

private object Routes {
    const val HOME = "home"
    const val ACTIVITY = "activity"
    const val PLAN = "plan"
    const val WORKBOOK = "workbook"
    const val REVIEW = "review"
    const val SETTINGS = "settings"
    const val MANUAL = "manual"
    const val DETAIL = "transaction/{id}"
    const val REVIEW_DETAIL = "review/{id}"
    const val SOURCES = "settings/sources"
    const val RULES = "settings/rules"
    const val PRIVACY = "settings/privacy"
    const val DIAGNOSTICS = "settings/diagnostics"

    fun detail(id: String) = "transaction/$id"
    fun reviewDetail(id: String) = "review/$id"
}
