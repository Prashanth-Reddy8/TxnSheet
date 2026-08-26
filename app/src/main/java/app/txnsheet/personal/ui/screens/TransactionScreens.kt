package app.txnsheet.personal.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import app.txnsheet.personal.data.local.TransactionEntity
import app.txnsheet.personal.ui.components.BannerTone
import app.txnsheet.personal.ui.components.HealthBanner
import app.txnsheet.personal.ui.components.MoneyText
import app.txnsheet.personal.ui.components.SensitiveSourcePreview
import app.txnsheet.personal.ui.components.StickyActionDock
import app.txnsheet.personal.ui.components.TransactionStatusTag
import app.txnsheet.personal.ui.components.TxnEmptyState
import app.txnsheet.personal.ui.formatDateTime
import app.txnsheet.personal.ui.theme.TxnMono
import app.txnsheet.personal.ui.theme.TxnSpacing
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

data class ReviewSubmission(
    val amountMinor: Long,
    val direction: String,
    val method: String,
    val counterparty: String?,
    val category: String,
    val notes: String,
)

data class TransactionEdits(
    val counterparty: String?,
    val category: String,
    val notes: String,
)

data class ManualTransactionSubmission(
    val amountMinor: Long,
    val direction: String,
    val eventTimeEpochMs: Long,
    val method: String,
    val counterparty: String?,
    val category: String,
    val notes: String,
)

@Composable
fun ManualImportScreen(
    initialText: String,
    currency: String,
    zoneId: String,
    working: Boolean,
    onBack: () -> Unit,
    onImport: (String) -> Unit,
    onCreateManual: (ManualTransactionSubmission) -> Unit,
    modifier: Modifier = Modifier,
) {
    var sourceText by rememberSaveable(initialText) { mutableStateOf(initialText.take(MAX_MANUAL_CHARS)) }
    var structured by rememberSaveable { mutableStateOf(initialText.isBlank()) }
    val now = remember(zoneId) { ZonedDateTime.now(ZoneId.of(zoneId)) }
    var amount by rememberSaveable { mutableStateOf("") }
    var direction by rememberSaveable { mutableStateOf("") }
    var method by rememberSaveable { mutableStateOf("OTHER") }
    var counterparty by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("Uncategorized") }
    var notes by rememberSaveable { mutableStateOf("") }
    var eventDate by rememberSaveable { mutableStateOf(now.toLocalDate().toString()) }
    var eventTime by rememberSaveable { mutableStateOf(now.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))) }
    val amountMinor = remember(amount) { amount.toMinorUnitsOrNull() }
    val eventEpochMs = remember(eventDate, eventTime, zoneId) { parseManualInstant(eventDate, eventTime, zoneId) }
    val structuredValid = amountMinor != null && amountMinor > 0 && direction in setOf("DEBIT", "CREDIT") && eventEpochMs != null
    val pasteValid = sourceText.trim().length >= 8

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { DetailTopBar("Add transaction", onBack) },
        bottomBar = {
            StickyActionDock(
                primaryLabel = if (structured) "Save transaction" else "Parse on this phone",
                onPrimary = {
                    if (structured) {
                        onCreateManual(
                            ManualTransactionSubmission(
                                amountMinor = requireNotNull(amountMinor),
                                direction = direction,
                                eventTimeEpochMs = requireNotNull(eventEpochMs),
                                method = method,
                                counterparty = counterparty.trim().ifBlank { null },
                                category = category.trim().ifBlank { "Uncategorized" },
                                notes = notes.trim(),
                            ),
                        )
                    } else {
                        onImport(sourceText.trim())
                    }
                },
                primaryEnabled = if (structured) structuredValid else pasteValid,
                working = working,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(horizontal = TxnSpacing.screen, vertical = 12.dp),
        ) {
            item {
                HealthBanner(
                    title = "Private by design",
                    body = "Parsing happens locally. Only confirmed ledger fields can be sent to Google Sheets.",
                    tone = BannerTone.Good,
                )
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !structured, onClick = { structured = false }, label = { Text("Paste alert") })
                    FilterChip(selected = structured, onClick = { structured = true }, label = { Text("Enter details") })
                }
                Spacer(Modifier.height(16.dp))
                if (structured) {
                    StructuredEntryForm(
                        amount = amount,
                        onAmountChange = { value ->
                            if (value.length <= 15 && value.matches(Regex("[0-9]*([.][0-9]{0,2})?"))) amount = value
                        },
                        currency = currency,
                        direction = direction,
                        onDirectionChange = { direction = it },
                        method = method,
                        onMethodChange = { method = it },
                        eventDate = eventDate,
                        onEventDateChange = { eventDate = it.take(10) },
                        eventTime = eventTime,
                        onEventTimeChange = { eventTime = it.take(5) },
                        eventTimeValid = eventEpochMs != null,
                        counterparty = counterparty,
                        onCounterpartyChange = { counterparty = it.take(100) },
                        category = category,
                        onCategoryChange = { category = it.take(60) },
                        notes = notes,
                        onNotesChange = { notes = it.take(240) },
                    )
                } else {
                    OutlinedTextField(
                        value = sourceText,
                        onValueChange = { sourceText = it.take(MAX_MANUAL_CHARS) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp),
                        label = { Text("Paste a transaction alert") },
                        placeholder = { Text("Example: INR 450 debited via UPI at... ") },
                        supportingText = {
                            Text("${sourceText.length}/$MAX_MANUAL_CHARS · OTP and promotional text is ignored")
                        },
                        leadingIcon = { Icon(Icons.Outlined.ContentPaste, contentDescription = null) },
                    )
                    Spacer(Modifier.height(20.dp))
                    ManualHint(
                        icon = Icons.Outlined.Share,
                        title = "Share from another app",
                        body = "Use Android's Share action and choose TxnSheet to fill this screen automatically.",
                    )
                    ManualHint(
                        icon = Icons.Outlined.VerifiedUser,
                        title = "Conservative capture",
                        body = "Uncertain amounts or directions go to Review instead of your Sheet.",
                    )
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun StructuredEntryForm(
    amount: String,
    onAmountChange: (String) -> Unit,
    currency: String,
    direction: String,
    onDirectionChange: (String) -> Unit,
    method: String,
    onMethodChange: (String) -> Unit,
    eventDate: String,
    onEventDateChange: (String) -> Unit,
    eventTime: String,
    onEventTimeChange: (String) -> Unit,
    eventTimeValid: Boolean,
    counterparty: String,
    onCounterpartyChange: (String) -> Unit,
    category: String,
    onCategoryChange: (String) -> Unit,
    notes: String,
    onNotesChange: (String) -> Unit,
) {
    Text("REQUIRED", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = amount,
        onValueChange = onAmountChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text("Amount ($currency)") },
        keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
    )
    Spacer(Modifier.height(10.dp))
    Text("Direction", style = MaterialTheme.typography.labelLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = direction == "DEBIT", onClick = { onDirectionChange("DEBIT") }, label = { Text("Money out") })
        FilterChip(selected = direction == "CREDIT", onClick = { onDirectionChange("CREDIT") }, label = { Text("Money in") })
    }
    Spacer(Modifier.height(14.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = eventDate,
            onValueChange = onEventDateChange,
            modifier = Modifier.weight(1.25f),
            singleLine = true,
            label = { Text("Date") },
            placeholder = { Text("YYYY-MM-DD") },
            isError = !eventTimeValid,
        )
        OutlinedTextField(
            value = eventTime,
            onValueChange = onEventTimeChange,
            modifier = Modifier.weight(.75f),
            singleLine = true,
            label = { Text("Time") },
            placeholder = { Text("HH:MM") },
            isError = !eventTimeValid,
        )
    }
    Spacer(Modifier.height(18.dp))
    Text("METHOD", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        METHODS.forEach { candidate ->
            FilterChip(
                selected = method == candidate,
                onClick = { onMethodChange(candidate) },
                label = { Text(candidate.displayValue()) },
            )
        }
    }
    Spacer(Modifier.height(18.dp))
    Text("OPTIONAL", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = counterparty,
        onValueChange = onCounterpartyChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text("Counterparty") },
    )
    Spacer(Modifier.height(10.dp))
    OutlinedTextField(
        value = category,
        onValueChange = onCategoryChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text("Category") },
    )
    Spacer(Modifier.height(10.dp))
    OutlinedTextField(
        value = notes,
        onValueChange = onNotesChange,
        modifier = Modifier.fillMaxWidth(),
        minLines = 2,
        label = { Text("Notes") },
    )
}

@Composable
private fun ManualHint(icon: ImageVector, title: String, body: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun TransactionDetailScreen(
    transaction: TransactionEntity?,
    zoneId: String,
    working: Boolean,
    onBack: () -> Unit,
    onSave: (TransactionEdits) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (transaction == null) {
        Scaffold(topBar = { DetailTopBar("Transaction", onBack) }) { padding ->
            TxnEmptyState(
                title = "Transaction unavailable",
                body = "It may have been removed from this phone.",
                modifier = Modifier.padding(padding),
            )
        }
        return
    }

    var editing by rememberSaveable(transaction.transactionId) { mutableStateOf(false) }
    var counterparty by rememberSaveable(transaction.transactionId) { mutableStateOf(transaction.counterparty.orEmpty()) }
    var category by rememberSaveable(transaction.transactionId) { mutableStateOf(transaction.category) }
    var notes by rememberSaveable(transaction.transactionId) { mutableStateOf(transaction.notes) }
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            DetailTopBar(
                title = "Transaction",
                onBack = onBack,
                action = if (!editing) {
                    {
                        IconButton(onClick = { editing = true }) {
                            Icon(Icons.Outlined.Edit, contentDescription = "Edit transaction")
                        }
                    }
                } else null,
            )
        },
        bottomBar = if (editing) {
            {
                StickyActionDock(
                    primaryLabel = "Save changes",
                    onPrimary = {
                        onSave(
                            TransactionEdits(
                                counterparty = counterparty.trim().ifBlank { null },
                                category = category.trim().ifBlank { "Uncategorized" },
                                notes = notes.trim(),
                            ),
                        )
                        editing = false
                    },
                    secondaryLabel = "Cancel",
                    onSecondary = {
                        counterparty = transaction.counterparty.orEmpty()
                        category = transaction.category
                        notes = transaction.notes
                        editing = false
                    },
                    working = working,
                )
            }
        } else {
            {}
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(start = TxnSpacing.screen, end = TxnSpacing.screen, bottom = 32.dp),
        ) {
            item {
                Spacer(Modifier.height(8.dp))
                transaction.amountMinor?.let {
                    MoneyText(
                        amountMinor = it,
                        currency = transaction.currency,
                        direction = transaction.direction,
                        prominent = true,
                    )
                } ?: Text("Amount needs review", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TransactionStatusTag(transaction.status)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        formatDateTime(transaction.eventTimeEpochMs, zoneId),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(28.dp))

                if (editing) {
                    Text("EDIT LEDGER FIELDS", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = counterparty,
                        onValueChange = { counterparty = it.take(100) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Counterparty") },
                        singleLine = true,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = category,
                        onValueChange = { category = it.take(60) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Category") },
                        singleLine = true,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it.take(240) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Notes") },
                        minLines = 3,
                    )
                } else {
                    DetailSection("Overview") {
                        DetailValue("Direction", transaction.direction?.displayValue() ?: "Needs review")
                        DetailValue("Method", transaction.method.displayValue())
                        DetailValue("Counterparty", transaction.counterparty ?: "Not detected")
                        DetailValue("Category", transaction.category)
                        transaction.balanceMinor?.let { DetailValue("Balance after", app.txnsheet.personal.ui.formatMoney(it, transaction.currency)) }
                    }
                    DetailSection("Source") {
                        DetailValue("Institution", transaction.institution ?: "Not detected")
                        DetailValue("Account", transaction.accountLast4?.let { "••$it" } ?: "Not detected")
                        DetailValue("Reference", transaction.referenceId ?: "Not detected")
                        DetailValue("Captured from", transaction.sourceLabel ?: transaction.sourceApp)
                        DetailValue("Parser confidence", "${(transaction.confidence * 100).toInt()}%")
                    }
                    DetailSection("Ledger record") {
                        DetailValue("Status", app.txnsheet.personal.ui.statusLabel(transaction.status))
                        DetailValue("Transaction ID", transaction.transactionId, monospace = true)
                        transaction.remoteRange?.let { DetailValue("Sheet row", it, monospace = true) }
                        if (transaction.notes.isNotBlank()) DetailValue("Notes", transaction.notes)
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { confirmDelete = true },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                    ) {
                        Icon(Icons.Outlined.DeleteOutline, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Remove local record", color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(Modifier.height(24.dp))
                    Text(
                        "Removing this local record does not delete an already-synced row from your Google Sheet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Remove this local record?") },
            text = { Text("The remote Sheet is never deleted or modified by this action.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
fun ReviewDetailScreen(
    transaction: TransactionEntity?,
    sourceText: String?,
    working: Boolean,
    onBack: () -> Unit,
    onApprove: (ReviewSubmission) -> Unit,
    onDiscard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (transaction == null) {
        Scaffold(topBar = { DetailTopBar("Review", onBack) }) { padding ->
            TxnEmptyState("Review item unavailable", "It may already have been handled.", Modifier.padding(padding))
        }
        return
    }

    var amount by rememberSaveable(transaction.transactionId) {
        mutableStateOf(transaction.amountMinor?.let { BigDecimal.valueOf(it, 2).toPlainString() }.orEmpty())
    }
    var direction by rememberSaveable(transaction.transactionId) { mutableStateOf(transaction.direction.orEmpty()) }
    var method by rememberSaveable(transaction.transactionId) { mutableStateOf(transaction.method) }
    var counterparty by rememberSaveable(transaction.transactionId) { mutableStateOf(transaction.counterparty.orEmpty()) }
    var category by rememberSaveable(transaction.transactionId) { mutableStateOf(transaction.category) }
    var notes by rememberSaveable(transaction.transactionId) { mutableStateOf(transaction.notes) }
    var showDiscard by remember { mutableStateOf(false) }
    val amountMinor = remember(amount) { amount.toMinorUnitsOrNull() }
    val valid = amountMinor != null && amountMinor > 0 && direction in setOf("DEBIT", "CREDIT")

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { DetailTopBar("Check transaction", onBack) },
        bottomBar = {
            StickyActionDock(
                primaryLabel = "Approve and queue",
                onPrimary = {
                    onApprove(
                        ReviewSubmission(
                            amountMinor = requireNotNull(amountMinor),
                            direction = direction,
                            method = method,
                            counterparty = counterparty.trim().ifBlank { null },
                            category = category.trim().ifBlank { "Uncategorized" },
                            notes = notes.trim(),
                        ),
                    )
                },
                primaryEnabled = valid,
                destructiveLabel = "Discard",
                onDestructive = { showDiscard = true },
                working = working,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(horizontal = TxnSpacing.screen, vertical = 8.dp),
        ) {
            item {
                HealthBanner(
                    title = "Why this stopped here",
                    body = reviewReason(transaction),
                    tone = BannerTone.Warning,
                )
                Spacer(Modifier.height(20.dp))
                SensitiveSourcePreview(sourceText)
                Spacer(Modifier.height(24.dp))
                Text("REQUIRED", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = amount,
                    onValueChange = { value ->
                        if (value.length <= 15 && value.matches(Regex("[0-9]*([.][0-9]{0,2})?"))) amount = value
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Amount (${transaction.currency})") },
                    singleLine = true,
                    isError = amount.isNotBlank() && (amountMinor == null || amountMinor <= 0),
                    keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                )
                Spacer(Modifier.height(12.dp))
                Text("Direction", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = direction == "DEBIT", onClick = { direction = "DEBIT" }, label = { Text("Money out") })
                    FilterChip(selected = direction == "CREDIT", onClick = { direction = "CREDIT" }, label = { Text("Money in") })
                }
                Spacer(Modifier.height(20.dp))
                Text("METHOD", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    METHODS.forEach { candidate ->
                        FilterChip(
                            selected = method == candidate,
                            onClick = { method = candidate },
                            label = { Text(candidate.displayValue()) },
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
                Text("OPTIONAL DETAILS", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = counterparty,
                    onValueChange = { counterparty = it.take(100) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Counterparty") },
                    singleLine = true,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it.take(60) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Category") },
                    singleLine = true,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it.take(240) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Notes") },
                    minLines = 2,
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (showDiscard) {
        AlertDialog(
            onDismissRequest = { showDiscard = false },
            title = { Text("Discard this transaction?") },
            text = { Text("It will be removed from this phone and will not be sent to Google Sheets.") },
            confirmButton = {
                TextButton(onClick = { showDiscard = false; onDiscard() }) {
                    Text("Discard", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showDiscard = false }) { Text("Keep reviewing") } },
        )
    }
}

@Composable
fun DetailTopBar(
    title: String,
    onBack: () -> Unit,
    action: (@Composable () -> Unit)? = null,
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, contentDescription = "Back") }
            Text(
                title,
                modifier = Modifier.weight(1f).semantics { heading() },
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            action?.invoke()
        }
    }
}

@Composable
private fun DetailSection(title: String, content: @Composable () -> Unit) {
    Text(title.uppercase(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) { content() }
    }
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun DetailValue(label: String, value: String, monospace: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(label, modifier = Modifier.weight(.42f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            modifier = Modifier.weight(.58f),
            style = if (monospace) MaterialTheme.typography.bodySmall.copy(fontFamily = TxnMono) else MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

private fun String.toMinorUnitsOrNull(): Long? = runCatching {
    BigDecimal(trim()).setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact()
}.getOrNull()

private fun parseManualInstant(date: String, time: String, zoneId: String): Long? = runCatching {
    LocalDate.parse(date.trim(), DateTimeFormatter.ISO_LOCAL_DATE)
        .atTime(LocalTime.parse(time.trim(), DateTimeFormatter.ofPattern("HH:mm")))
        .atZone(ZoneId.of(zoneId))
        .toInstant()
        .toEpochMilli()
}.getOrNull()

private fun String.displayValue(): String = lowercase().replace('_', ' ').replaceFirstChar(Char::titlecase)

private fun reviewReason(transaction: TransactionEntity): String = when {
    transaction.amountMinor == null && transaction.direction == null -> "Amount and direction could not be confirmed."
    transaction.amountMinor == null -> "The amount could not be confirmed."
    transaction.direction == null -> "Money-in versus money-out could not be confirmed."
    else -> "The alert did not contain enough consistent evidence for automatic sync."
}

private const val MAX_MANUAL_CHARS = 8_000
private val METHODS = listOf("UPI", "CARD", "BANK_TRANSFER", "ATM", "CASH", "FEE", "OTHER")
