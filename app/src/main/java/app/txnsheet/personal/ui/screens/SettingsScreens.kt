package app.txnsheet.personal.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DataObject
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.txnsheet.personal.BuildConfig
import app.txnsheet.personal.data.local.CategoryRuleEntity
import app.txnsheet.personal.data.local.DiagnosticEventEntity
import app.txnsheet.personal.data.local.SourceAppEntity
import app.txnsheet.personal.ui.TxnSheetUiState
import app.txnsheet.personal.ui.components.BannerTone
import app.txnsheet.personal.ui.components.HealthBanner
import app.txnsheet.personal.ui.components.ReadinessRow
import app.txnsheet.personal.ui.components.SectionLabel
import app.txnsheet.personal.ui.components.SettingRow
import app.txnsheet.personal.ui.components.StickyActionDock
import app.txnsheet.personal.ui.components.TxnEmptyState
import app.txnsheet.personal.ui.formatDateTime
import app.txnsheet.personal.ui.theme.LocalTxnSemanticColors
import app.txnsheet.personal.ui.theme.TxnMono
import app.txnsheet.personal.ui.theme.TxnSpacing

@Composable
fun SettingsScreen(
    state: TxnSheetUiState,
    onOpenSources: () -> Unit,
    onOpenRules: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = TxnSpacing.screen, end = TxnSpacing.screen, bottom = 112.dp),
    ) {
        item {
            Text("Settings", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(6.dp))
            Text(
                "Capture, ledger, and privacy controls.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(TxnSpacing.lg))

            ReadinessCard(state)
            Spacer(Modifier.height(TxnSpacing.section))
            SectionLabel("Capture")
            SettingRow(
                title = "Notification sources",
                supportingText = if (state.enabledSourceCount == 0) "No apps allowed" else "${state.enabledSourceCount} allowed",
                icon = Icons.Outlined.Notifications,
                onClick = onOpenSources,
            )
            SettingRow(
                title = "Category rules",
                supportingText = if (state.categoryRules.isEmpty()) "No custom rules" else "${state.categoryRules.size} local rules",
                icon = Icons.Outlined.FilterAlt,
                onClick = onOpenRules,
            )
            Spacer(Modifier.height(TxnSpacing.lg))
            SectionLabel("Trust")
            SettingRow(
                title = "Privacy and local data",
                supportingText = "See storage, retention, and erase controls",
                icon = Icons.Outlined.PrivacyTip,
                onClick = onOpenPrivacy,
            )
            SettingRow(
                title = "Diagnostics",
                supportingText = "Privacy-safe capture events",
                icon = Icons.Outlined.BugReport,
                onClick = onOpenDiagnostics,
            )
            Spacer(Modifier.height(TxnSpacing.lg))
            Text(
                "TxnSheet ${BuildConfig.VERSION_NAME} · Schema ${state.config.schemaVersion}",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = TxnMono),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ReadinessCard(state: TxnSheetUiState) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text("SYSTEM READINESS", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            ReadinessRow(
                title = "Automatic capture",
                supportingText = if (state.notificationAccessGranted) "Notification access is on" else "Notification access is off",
                ready = state.notificationAccessGranted,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            ReadinessRow(
                title = "Allowed sources",
                supportingText = if (state.enabledSourceCount > 0) "${state.enabledSourceCount} selected" else "Default-deny: none selected",
                ready = state.enabledSourceCount > 0,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            ReadinessRow(
                title = "Private dashboard",
                supportingText = "Stored only on this phone",
                ready = true,
            )
        }
    }
}

@Composable
fun SourcesScreen(
    sources: List<SourceAppEntity>,
    notificationAccessGranted: Boolean,
    onBack: () -> Unit,
    onOpenNotificationAccess: () -> Unit,
    onToggle: (SourceAppEntity, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { DetailTopBar("Notification sources", onBack) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(start = TxnSpacing.screen, end = TxnSpacing.screen, bottom = 32.dp),
        ) {
            item {
                HealthBanner(
                    title = if (notificationAccessGranted) "Access is on" else "Notification access is off",
                    body = if (notificationAccessGranted) {
                        "Only enabled apps below can be parsed. New apps remain off by default."
                    } else {
                        "Turn access on to discover transaction senders. Manual import still works."
                    },
                    tone = if (notificationAccessGranted) BannerTone.Good else BannerTone.Warning,
                    actionLabel = if (notificationAccessGranted) "Manage system access" else "Open notification access",
                    onAction = onOpenNotificationAccess,
                )
                Spacer(Modifier.height(24.dp))
                SectionLabel("Discovered apps")
            }
            if (sources.isEmpty()) {
                item {
                    TxnEmptyState(
                        title = "No sources discovered",
                        body = "After notification access is granted, apps appear here when they post a notification. They remain disabled until you allow them.",
                        icon = Icons.Outlined.Notifications,
                        actionLabel = "Open notification access",
                        onAction = onOpenNotificationAccess,
                    )
                }
            } else {
                items(sources, key = SourceAppEntity::packageName) { source ->
                    SourceToggleRow(source, onToggle)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun SourceToggleRow(source: SourceAppEntity, onToggle: (SourceAppEntity, Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceVariant) {
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                Text(source.label.take(1).uppercase(), fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(source.label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                source.packageName,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = TxnMono),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Switch(
            checked = source.enabled,
            onCheckedChange = { enabled -> onToggle(source, enabled) },
        )
    }
}

@Composable
fun RulesScreen(
    rules: List<CategoryRuleEntity>,
    onBack: () -> Unit,
    onAddRule: (term: String, category: String) -> Unit,
    onDeleteRule: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAdd by remember { mutableStateOf(false) }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            DetailTopBar(
                title = "Category rules",
                onBack = onBack,
                action = {
                    IconButton(onClick = { showAdd = true }) {
                        Icon(Icons.Outlined.Add, contentDescription = "Add category rule")
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(start = TxnSpacing.screen, end = TxnSpacing.screen, bottom = 32.dp),
        ) {
            item {
                HealthBanner(
                    title = "Local and explainable",
                    body = "Rules match normalized counterparty text on this phone. Higher rules win; no raw alerts leave the device.",
                    tone = BannerTone.Info,
                )
                Spacer(Modifier.height(24.dp))
            }
            if (rules.isEmpty()) {
                item {
                    TxnEmptyState(
                        title = "No custom rules",
                        body = "Add a simple term-to-category rule for recurring counterparties.",
                        icon = Icons.Outlined.FilterAlt,
                        actionLabel = "Add rule",
                        onAction = { showAdd = true },
                    )
                }
            } else {
                items(rules, key = CategoryRuleEntity::ruleId) { rule ->
                    RuleRow(rule, onDeleteRule)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }

    if (showAdd) {
        AddRuleDialog(
            onDismiss = { showAdd = false },
            onAdd = { term, category -> showAdd = false; onAddRule(term, category) },
        )
    }
}

@Composable
private fun RuleRow(rule: CategoryRuleEntity, onDelete: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceVariant) {
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.FilterAlt, contentDescription = null, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(rule.category, style = MaterialTheme.typography.titleMedium)
            Text(
                "Contains “${rule.normalizedTerm}”",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = { onDelete(rule.ruleId) }) {
            Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete ${rule.category} rule")
        }
    }
}

@Composable
private fun AddRuleDialog(onDismiss: () -> Unit, onAdd: (String, String) -> Unit) {
    var term by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add category rule") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = term,
                    onValueChange = { term = it.take(80) },
                    label = { Text("Counterparty contains") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it.take(60) },
                    label = { Text("Set category") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(term.trim(), category.trim()) },
                enabled = term.trim().length >= 2 && category.isNotBlank(),
            ) { Text("Add rule") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun PrivacyScreen(
    onBack: () -> Unit,
    onEraseLocalData: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmErase by remember { mutableStateOf(false) }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { DetailTopBar("Privacy and local data", onBack) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(start = TxnSpacing.screen, end = TxnSpacing.screen, bottom = 40.dp),
        ) {
            item {
                Text("What goes where", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    "The boundary is simple: transaction alerts, normalized records and dashboard insights stay on this phone.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
                PrivacyBoundary(
                    icon = Icons.Outlined.PhoneAndroid,
                    title = "Stored on this phone",
                    items = listOf(
                        "Normalized local transactions and dashboard totals",
                        "Encrypted source text for review items only",
                        "Allowed sources and category rules",
                        "Privacy-safe diagnostics without message text",
                    ),
                )
                PrivacyBoundary(
                    icon = Icons.Outlined.CloudDone,
                    title = "Leaves this phone",
                    items = listOf(
                        "Confirmed amount, direction, method and dates",
                        "Counterparty, category and normalized references",
                        "Parser confidence, rule name and your notes",
                        "A stable UUID that prevents duplicate rows",
                    ),
                )
                PrivacyBoundary(
                    icon = Icons.Outlined.CloudOff,
                    title = "Never uploaded",
                    items = listOf(
                        "Raw notification or pasted text",
                        "OTP, verification and promotional messages",
                        "Unapproved review items",
                        "Logs containing amounts, accounts or counterparties",
                    ),
                )
                HealthBanner(
                    title = "Seven-day review retention",
                    body = "Encrypted source text exists only to help you check uncertain items and expires automatically after at most seven days.",
                    tone = BannerTone.Info,
                )
                Spacer(Modifier.height(32.dp))
                Text("ERASE", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { confirmErase = true },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                ) {
                    Icon(Icons.Outlined.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.size(8.dp))
                    Text("Erase all local TxnSheet data", color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "This permanently removes the local dashboard and its settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (confirmErase) {
        AlertDialog(
            onDismissRequest = { confirmErase = false },
            icon = { Icon(Icons.Outlined.WarningAmber, contentDescription = null) },
            title = { Text("Erase all local data?") },
            text = {
                Text("Transactions, review sources, source choices, rules and diagnostics will be permanently removed from this phone.")
            },
            confirmButton = {
                TextButton(onClick = { confirmErase = false; onEraseLocalData() }) {
                    Text("Erase local data", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmErase = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun PrivacyBoundary(icon: ImageVector, title: String, items: List<String>) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
                Spacer(Modifier.size(10.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(12.dp))
            items.forEach { value ->
                Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
                    Text("•", modifier = Modifier.padding(end = 8.dp), color = MaterialTheme.colorScheme.primary)
                    Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun DiagnosticsScreen(
    diagnostics: List<DiagnosticEventEntity>,
    zoneId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { DetailTopBar("Diagnostics", onBack) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(start = TxnSpacing.screen, end = TxnSpacing.screen, bottom = 32.dp),
        ) {
            item {
                HealthBanner(
                    title = "Safe to inspect",
                    body = "Events use fixed error codes and redacted context. Transaction amounts, account numbers and message bodies are not logged.",
                    tone = BannerTone.Good,
                )
                Spacer(Modifier.height(24.dp))
            }
            if (diagnostics.isEmpty()) {
                item {
                    TxnEmptyState(
                        title = "No diagnostic events",
                        body = "Capture health events will appear here when useful.",
                        icon = Icons.Outlined.CheckCircle,
                    )
                }
            } else {
                items(diagnostics, key = DiagnosticEventEntity::id) { event ->
                    DiagnosticRow(event, zoneId)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun DiagnosticRow(event: DiagnosticEventEntity, zoneId: String) {
    val semantic = LocalTxnSemanticColors.current
    val tint = when (event.severity.uppercase()) {
        "ERROR" -> MaterialTheme.colorScheme.error
        "WARNING", "WARN" -> semantic.review
        else -> semantic.queued
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.Top) {
        Icon(
            if (event.severity.equals("ERROR", true)) Icons.Outlined.ErrorOutline else Icons.Outlined.Code,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = tint,
        )
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(event.code, style = MaterialTheme.typography.titleSmall.copy(fontFamily = TxnMono))
            Text(
                "${event.component} · ${formatDateTime(event.createdAtEpochMs, zoneId)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            event.redactedContext?.takeIf(String::isNotBlank)?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = MaterialTheme.typography.bodySmall.copy(fontFamily = TxnMono))
            }
        }
    }
}

private fun extractSpreadsheetId(value: String): String {
    val trimmed = value.trim()
    val marker = "/spreadsheets/d/"
    if (marker !in trimmed) return trimmed.filterNot(Char::isWhitespace)
    return trimmed.substringAfter(marker).substringBefore('/').substringBefore('?')
}
