package app.txnsheet.personal.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.AutoAwesomeMotion
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.txnsheet.personal.data.local.TransactionEntity
import app.txnsheet.personal.ui.ActivityFilter
import app.txnsheet.personal.ui.TxnSheetUiState
import app.txnsheet.personal.ui.components.BannerTone
import app.txnsheet.personal.ui.components.HealthBanner
import app.txnsheet.personal.ui.components.MoneyText
import app.txnsheet.personal.ui.components.SectionLabel
import app.txnsheet.personal.ui.components.TransactionRow
import app.txnsheet.personal.ui.components.TxnEmptyState
import app.txnsheet.personal.ui.formatDay
import app.txnsheet.personal.ui.theme.LocalTxnSemanticColors
import app.txnsheet.personal.ui.theme.TxnMono
import app.txnsheet.personal.ui.theme.TxnSpacing
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

@Composable
fun HomeScreen(
    state: TxnSheetUiState,
    onOpenActivity: () -> Unit,
    onOpenReview: () -> Unit,
    onOpenTransaction: (String) -> Unit,
    onOpenNotificationAccess: () -> Unit,
    onOpenGoogleSetup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val zoneId = state.config.timezone
    val monthStart = remember(zoneId) {
        ZonedDateTime.now(ZoneId.of(zoneId)).withDayOfMonth(1).toLocalDate().atStartOfDay(ZoneId.of(zoneId)).toInstant().toEpochMilli()
    }
    val monthTransactions = remember(state.transactions, monthStart) {
        state.transactions.filter {
            it.eventTimeEpochMs >= monthStart && it.status !in setOf("IGNORED", "REVIEW")
        }
    }
    val spent = monthTransactions.filter { it.direction == "DEBIT" }.sumOf { it.amountMinor ?: 0L }
    val received = monthTransactions.filter { it.direction == "CREDIT" }.sumOf { it.amountMinor ?: 0L }
    val recent = state.transactions.take(5)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = TxnSpacing.screen,
            top = TxnSpacing.md,
            end = TxnSpacing.screen,
            bottom = 112.dp,
        ),
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { heading() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text("TxnSheet", style = MaterialTheme.typography.headlineLarge)
                    Text(
                        "Your ledger, quietly kept.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        Text(
                            "TS",
                            style = MaterialTheme.typography.labelLarge.copy(fontFamily = TxnMono),
                        )
                    }
                }
            }
            Spacer(Modifier.height(TxnSpacing.lg))
            MonthSummary(
                spent = spent,
                received = received,
                currency = state.config.currency,
                transactionCount = monthTransactions.size,
            )
            Spacer(Modifier.height(TxnSpacing.md))
            HomeHealthBanner(
                state = state,
                onOpenReview = onOpenReview,
                onOpenNotificationAccess = onOpenNotificationAccess,
                onOpenGoogleSetup = onOpenGoogleSetup,
            )
            Spacer(Modifier.height(TxnSpacing.section))
            SectionLabel(
                text = "Recent activity",
                action = {
                    AssistChip(onClick = onOpenActivity, label = { Text("See all") })
                },
            )
        }

        if (recent.isEmpty()) {
            item {
                TxnEmptyState(
                    title = "No transactions yet",
                    body = "Paste or share a transaction alert to start your private ledger.",
                )
            }
        } else {
            items(recent, key = TransactionEntity::transactionId) { transaction ->
                TransactionRow(
                    transaction = transaction,
                    zoneId = zoneId,
                    onClick = { onOpenTransaction(transaction.transactionId) },
                )
            }
        }
    }
}

@Composable
private fun MonthSummary(
    spent: Long,
    received: Long,
    currency: String,
    transactionCount: Int,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                "THIS MONTH",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            MoneyText(spent, currency, "DEBIT", prominent = true)
            Text(
                "$transactionCount captured transaction${if (transactionCount == 1) "" else "s"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SummaryMetric(
                    label = "Spent",
                    value = spent,
                    currency = currency,
                    direction = "DEBIT",
                    icon = Icons.Outlined.ArrowUpward,
                    modifier = Modifier.weight(1f),
                )
                SummaryMetric(
                    label = "Received",
                    value = received,
                    currency = currency,
                    direction = "CREDIT",
                    icon = Icons.Outlined.ArrowDownward,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SummaryMetric(
    label: String,
    value: Long,
    currency: String,
    direction: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    val semantic = LocalTxnSemanticColors.current
    val tint = if (direction == "DEBIT") semantic.debit else semantic.credit
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(5.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(6.dp))
        MoneyText(value, currency, direction)
    }
}

@Composable
private fun HomeHealthBanner(
    state: TxnSheetUiState,
    onOpenReview: () -> Unit,
    onOpenNotificationAccess: () -> Unit,
    onOpenGoogleSetup: () -> Unit,
) {
    when {
        !state.notificationAccessGranted -> HealthBanner(
            title = "Automatic capture is off",
            body = "Grant notification access when you want alerts captured automatically. Manual import already works.",
            tone = BannerTone.Warning,
            actionLabel = "Open notification access",
            onAction = onOpenNotificationAccess,
        )
        state.reviewTransactions.isNotEmpty() -> HealthBanner(
            title = "${state.reviewTransactions.size} ${if (state.reviewTransactions.size == 1) "item needs" else "items need"} a quick check",
            body = "Nothing is sent until ambiguous details are confirmed.",
            tone = BannerTone.Warning,
            actionLabel = "Review now",
            onAction = onOpenReview,
        )
        !state.sheetConnected -> HealthBanner(
            title = "Ledger is only on this phone",
            body = "Connect a private Google Sheet to keep an owner-controlled copy.",
            tone = BannerTone.Info,
            actionLabel = "Connect Google Sheet",
            onAction = onOpenGoogleSetup,
        )
        state.authRequiredCount > 0 -> HealthBanner(
            title = "Google needs to reconnect",
            body = "${state.authRequiredCount} confirmed transaction${if (state.authRequiredCount == 1) " is" else "s are"} safe on this phone.",
            tone = BannerTone.Warning,
            actionLabel = "Reconnect Google",
            onAction = onOpenGoogleSetup,
        )
        state.syncIssueCount > 0 -> HealthBanner(
            title = "Sync needs attention",
            body = "${state.syncIssueCount} confirmed transaction${if (state.syncIssueCount == 1) " is" else "s are"} safe on this phone but sync is paused.",
            tone = BannerTone.Error,
            actionLabel = "Check Google Sheet",
            onAction = onOpenGoogleSetup,
        )
        state.pendingCount > 0 -> HealthBanner(
            title = "${state.pendingCount} waiting to sync",
            body = "They are safe on this phone and will retry with a network connection.",
            tone = BannerTone.Info,
        )
        else -> HealthBanner(
            title = "Everything is up to date",
            body = "Automatic capture and your private Google ledger are ready.",
            tone = BannerTone.Good,
        )
    }
}

@Composable
fun ActivityScreen(
    state: TxnSheetUiState,
    onOpenTransaction: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(ActivityFilter.ALL) }
    var searchVisible by remember { mutableStateOf(false) }
    val filtered = remember(state.transactions, query, filter) {
        state.transactions.filter { transaction ->
            val matchesFilter = when (filter) {
                ActivityFilter.ALL -> true
                ActivityFilter.DEBITS -> transaction.direction == "DEBIT"
                ActivityFilter.CREDITS -> transaction.direction == "CREDIT"
                ActivityFilter.NEEDS_REVIEW -> transaction.status == "REVIEW"
                ActivityFilter.NOT_SYNCED -> transaction.status != "SYNCED"
            }
            val haystack = listOfNotNull(
                transaction.counterparty,
                transaction.institution,
                transaction.category,
                transaction.method,
                transaction.notes,
            ).joinToString(" ")
            matchesFilter && (query.isBlank() || haystack.contains(query.trim(), ignoreCase = true))
        }
    }
    val grouped = remember(filtered, state.config.timezone) {
        filtered.groupBy { formatDay(it.eventTimeEpochMs, state.config.timezone) }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 112.dp),
    ) {
        item {
            Column(Modifier.padding(horizontal = TxnSpacing.screen)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Activity", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
                    IconButton(onClick = { searchVisible = !searchVisible }) {
                        Icon(Icons.Outlined.Search, contentDescription = if (searchVisible) "Hide search" else "Search transactions")
                    }
                }
                AnimatedVisibility(searchVisible) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it.take(80) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Search activity") },
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = TxnSpacing.screen),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ActivityFilter.entries.forEach { candidate ->
                    FilterChip(
                        selected = filter == candidate,
                        onClick = { filter = candidate },
                        label = { Text(candidate.label) },
                        leadingIcon = if (candidate == ActivityFilter.ALL) {
                            { Icon(Icons.Outlined.FilterList, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        } else null,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        if (filtered.isEmpty()) {
            item {
                TxnEmptyState(
                    title = if (state.transactions.isEmpty()) "No activity yet" else "No matches",
                    body = if (state.transactions.isEmpty()) {
                        "Imported and captured transactions will appear here."
                    } else {
                        "Try another search or filter."
                    },
                    icon = Icons.Outlined.ReceiptLong,
                )
            }
        } else {
            grouped.forEach { (day, transactions) ->
                item(key = "day-$day") {
                    Text(
                        day.uppercase(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.background)
                            .padding(horizontal = TxnSpacing.screen, vertical = 10.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(transactions, key = TransactionEntity::transactionId) { transaction ->
                    TransactionRow(
                        transaction = transaction,
                        zoneId = state.config.timezone,
                        onClick = { onOpenTransaction(transaction.transactionId) },
                        modifier = Modifier.padding(horizontal = TxnSpacing.screen),
                    )
                }
            }
        }
    }
}

@Composable
fun ReviewQueueScreen(
    state: TxnSheetUiState,
    onOpenReview: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = TxnSpacing.screen, end = TxnSpacing.screen, bottom = 112.dp),
    ) {
        item {
            Text("Review", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(6.dp))
            Text(
                "Only uncertain details stop here. Nothing is uploaded until you approve it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(TxnSpacing.lg))
        }
        if (state.reviewTransactions.isEmpty()) {
            item {
                TxnEmptyState(
                    title = "All clear",
                    body = "There are no uncertain transactions waiting for you.",
                    icon = Icons.Outlined.AutoAwesomeMotion,
                )
            }
        } else {
            item {
                HealthBanner(
                    title = "${state.reviewTransactions.size} waiting",
                    body = "Check the amount and direction first. Source text is automatically removed within seven days.",
                    tone = BannerTone.Warning,
                )
                Spacer(Modifier.height(16.dp))
            }
            items(state.reviewTransactions, key = TransactionEntity::transactionId) { transaction ->
                TransactionRow(
                    transaction = transaction,
                    zoneId = state.config.timezone,
                    onClick = { onOpenReview(transaction.transactionId) },
                )
            }
        }
    }
}

@Composable
fun OnboardingScreen(
    notificationAccessGranted: Boolean,
    sheetConnected: Boolean,
    onOpenNotificationAccess: () -> Unit,
    onConnectSheet: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 28.dp),
    ) {
        item {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                    Text("TS", style = MaterialTheme.typography.titleLarge.copy(fontFamily = TxnMono))
                }
            }
            Spacer(Modifier.height(28.dp))
            Text("A transaction ledger that stays yours.", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(12.dp))
            Text(
                "TxnSheet reads only the notification sources you choose, extracts ledger fields on this phone, and writes them to a Google Sheet you own.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(32.dp))
            OnboardingPrinciple(
                number = "01",
                title = "Default-deny capture",
                body = "New apps are off until you explicitly allow them.",
                icon = Icons.Outlined.NotificationsNone,
            )
            OnboardingPrinciple(
                number = "02",
                title = "On-device parsing",
                body = "OTP and promotional messages are rejected before storage or sync.",
                icon = Icons.Outlined.Tune,
            )
            OnboardingPrinciple(
                number = "03",
                title = "Your private ledger",
                body = "Only normalized transaction columns are sent to your Sheet.",
                icon = Icons.Outlined.AccountBalanceWallet,
            )
            Spacer(Modifier.height(24.dp))
            Text("SET UP AUTOMATION", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            SetupAction(
                title = "Notification access",
                body = if (notificationAccessGranted) "Ready for selected sources" else "Needed for automatic capture",
                complete = notificationAccessGranted,
                onClick = onOpenNotificationAccess,
            )
            SetupAction(
                title = "Google Sheet",
                body = if (sheetConnected) "Private ledger connected" else "Optional now; connect any time",
                complete = sheetConnected,
                onClick = onConnectSheet,
            )
            Spacer(Modifier.height(28.dp))
            androidx.compose.material3.Button(
                onClick = onFinish,
                modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
            ) {
                Text("Start using TxnSheet")
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Manual paste and share work even without notification or Google access.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun OnboardingPrinciple(number: String, title: String, body: String, icon: ImageVector) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.Top) {
        Text(
            number,
            style = MaterialTheme.typography.labelMedium.copy(fontFamily = TxnMono),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(34.dp),
        )
        Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SetupAction(title: String, body: String, complete: Boolean, onClick: () -> Unit) {
    val semantic = LocalTxnSemanticColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .heightIn(min = 72.dp)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = CircleShape,
            color = if (complete) semantic.creditContainer else MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                Text(if (complete) "✓" else "·", fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Outlined.ChevronRight, contentDescription = null)
    }
}
