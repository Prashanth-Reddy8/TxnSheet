package app.txnsheet.personal.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.txnsheet.personal.domain.FinanceCalculator
import app.txnsheet.personal.ui.TxnSheetUiState
import app.txnsheet.personal.ui.components.TransactionRow
import app.txnsheet.personal.ui.formatMoney
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

private val IncomeGreen = Color(0xFF168765)
private val ExpenseCoral = Color(0xFFD95759)
private val ChartColors = listOf(Color(0xFF7887FF), Color(0xFF2EA58C), Color(0xFFE9A34F), Color(0xFFCE79AA), Color(0xFF579AC8), Color(0xFF969AA6))

@Composable
fun FinanceHomeScreen(
    state: TxnSheetUiState,
    onMonth: (String) -> Unit,
    onActivity: () -> Unit,
    onMerchant: (String) -> Unit,
    onPlan: () -> Unit,
    onReview: () -> Unit,
    onSettings: () -> Unit,
    onTransaction: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val month = YearMonth.parse(state.selectedMonth)
    val zone = ZoneId.of(state.config.timezone)
    val summary = remember(state.transactions, state.finance, month, zone, state.config.currency) {
        FinanceCalculator.summary(state.transactions, state.finance, month, zone, state.config.currency)
    }
    var section by rememberSaveable { mutableStateOf("Overview") }
    val money: (Long) -> String = { formatMoney(it, state.config.currency) }
    val history = remember(state.transactions, state.finance, month) {
        (5 downTo 0).map { offset -> val period = month.minusMonths(offset.toLong()); period to FinanceCalculator.summary(state.transactions, state.finance, period, zone, state.config.currency) }
    }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp, 12.dp, 20.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("YOUR MONEY, AT A GLANCE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Text("My finances", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, "Settings and import") }
            }
            Spacer(Modifier.height(12.dp))
            MonthControl(month, { onMonth(it.toString()) })
        }
        item {
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.Savings, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Net savings this month", style = MaterialTheme.typography.labelLarge) }
                    Text(money(summary.savingsMinor), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    Text("Income minus spending · not your bank balance", style = MaterialTheme.typography.bodySmall)
                    HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .12f))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Metric("Income", money(summary.incomeMinor), Icons.Outlined.SouthWest, IncomeGreen, Modifier.weight(1f))
                        Metric("Spending", money(summary.expenseMinor), Icons.Outlined.NorthEast, ExpenseCoral, Modifier.weight(1f))
                    }
                }
            }
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Overview", "Spending", "Accounts", "Actions").forEach { name -> FilterChip(section == name, { section = name }, label = { Text(name) }) }
            }
        }
        if (section == "Overview") {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard("Savings rate", summary.savingsRate?.let { "${(it * 100).toInt()}%" } ?: "No income yet", "Target ${state.finance.preferences.savingsTargetPercent}%", Icons.Outlined.TrendingUp, Modifier.weight(1f))
                    StatCard("Monthly EMI", money(summary.monthlyEmiMinor), "${state.finance.debts.count { it.active }} active debts", Icons.Outlined.CalendarMonth, Modifier.weight(1f), onPlan)
                }
            }
            item {
                val allowed = state.enabledSourceCount
                val lastEvent = state.diagnostics.firstOrNull { it.component == "ingestion" || it.component == "capture" }
                val title = when { !state.notificationAccessGranted -> "Turn on automatic capture"; allowed == 0 -> "Choose your notification sources"; state.reviewTransactions.isNotEmpty() -> "${state.reviewTransactions.size} alerts need your review"; else -> "Capture access is enabled" }
                InfoCard(title, when { !state.notificationAccessGranted -> "Enable notification access in Settings. You can always add a transaction manually."; allowed == 0 -> "Only the apps you choose can add transactions."; state.reviewTransactions.isNotEmpty() -> "Uncertain details are excluded from totals until you confirm them."; lastEvent == null -> "$allowed sources allowed. Waiting for the first alert."; else -> "$allowed sources allowed. See recent capture results in Diagnostics." }, Icons.Outlined.Notifications, if (state.reviewTransactions.isNotEmpty()) onReview else onSettings)
            }
            item {
                Panel("Six-month cash movement", "Recorded income and spending; blank months mean no records.") {
                    val max = history.maxOf { maxOf(it.second.incomeMinor, it.second.expenseMinor) }.coerceAtLeast(1)
                    val description = history.joinToString { "${it.first}: income ${money(it.second.incomeMinor)}, spending ${money(it.second.expenseMinor)}" }
                    Canvas(Modifier.fillMaxWidth().height(116.dp).semantics { contentDescription = description }) {
                        val group = size.width / 6
                        history.forEachIndexed { index, (_, data) ->
                            val x = group * index + group * .23f
                            val a = (data.incomeMinor.toFloat() / max * size.height).coerceAtLeast(1f)
                            val b = (data.expenseMinor.coerceAtLeast(0).toFloat() / max * size.height).coerceAtLeast(1f)
                            drawRoundRect(IncomeGreen, Offset(x, size.height - a), Size(group * .21f, a), androidx.compose.ui.geometry.CornerRadius(5f))
                            drawRoundRect(ExpenseCoral, Offset(x + group * .27f, size.height - b), Size(group * .21f, b), androidx.compose.ui.geometry.CornerRadius(5f))
                        }
                    }
                    Row(Modifier.fillMaxWidth()) { history.forEach { Text(it.first.month.name.take(3), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall) } }
                    Text("● Income    ↗ Spending", style = MaterialTheme.typography.bodySmall)
                }
            }
            item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("Outstanding debt", money(summary.outstandingDebtMinor), "Open your payoff plan", Icons.Outlined.AccountBalance, Modifier.weight(1f), onPlan)
                StatCard("Cash buffer", summary.cashBufferMonths?.let { String.format(Locale.ROOT, "%.1f months", it) } ?: "Not set", "Set opening cash in Plan", Icons.Outlined.Shield, Modifier.weight(1f), onPlan)
            } }
            item { SectionTitle("Recent activity", "See all", onActivity) }
            if (summary.transactions.isEmpty()) item { InfoCard("A fresh start", "Add a transaction or import your workbook from Settings. Your numbers update on this phone.", Icons.Outlined.AddCircleOutline, onActivity) }
            items(summary.transactions.sortedByDescending { it.eventTimeEpochMs }.take(5), key = { it.transactionId }) { transaction -> TransactionRow(transaction, state.config.timezone, { onTransaction(transaction.transactionId) }) }
        }
        if (section == "Spending") {
            item { Panel("Where your money goes", "Spending before refunds. Tap a merchant below to see its payments.") {
                val positive = summary.categorySpend.filterValues { it > 0 }.toList().sortedByDescending { it.second }
                if (positive.isEmpty()) Text("No spending recorded for this month.", style = MaterialTheme.typography.bodyMedium)
                else {
                    val sum = positive.sumOf { it.second }.toFloat()
                    val chart = positive.take(5) + listOfNotNull(positive.drop(5).sumOf { it.second }.takeIf { it > 0 }?.let { "Other" to it })
                    Box(Modifier.fillMaxWidth().height(166.dp), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.size(156.dp).semantics { contentDescription = positive.joinToString { "${it.first}: ${money(it.second)}" } }) {
                            var start = -90f
                            chart.forEachIndexed { index, pair -> val angle = pair.second / sum * 360f; drawArc(ChartColors[index % ChartColors.size], start, (angle - 3).coerceAtLeast(0f), false, style = Stroke(24.dp.toPx(), cap = StrokeCap.Round)); start += angle }
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("EXPENSE MIX", style = MaterialTheme.typography.labelSmall); Text("${positive.size} categories", style = MaterialTheme.typography.labelMedium) }
                    }
                    positive.forEachIndexed { index, (name, value) -> BreakdownRow(name, money(value), value / sum, ChartColors[index % ChartColors.size]) }
                }
            } }
            item { Panel("Essentials and choices", "Unclassified spending stays visible until you decide.") {
                BreakdownRow("Essential", money(summary.essentialMinor), 0f, IncomeGreen)
                BreakdownRow("Discretionary", money(summary.discretionaryMinor), 0f, ChartColors[0])
                BreakdownRow("Not classified", money(summary.unclassifiedMinor), 0f, ChartColors[2])
                TextButton(onClick = onActivity) { Text("Classify payments") }
            } }
            item { Panel("Top merchants", "Names come from alerts or your corrections. No location is inferred.") {
                if (summary.merchantSpend.isEmpty()) Text("No merchant payments yet.")
                summary.merchantSpend.toList().sortedByDescending { it.second }.take(12).forEach { (merchant, amount) ->
                    Row(Modifier.fillMaxWidth().clickable { onMerchant(merchant) }.heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Storefront, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(12.dp))
                        Text(merchant, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Text(money(amount), style = MaterialTheme.typography.labelLarge)
                    }
                }
            } }
            item { Panel("Payment methods") { summary.paymentSpend.forEach { (method, amount) -> BreakdownRow(method.replace('_', ' '), money(amount), 0f, ChartColors[0]) } } }
        }
        if (section == "Accounts") {
            item { Panel("Cash position", "An estimate from your opening cash and recorded payments; not a live bank balance.") {
                Text(summary.cashBalanceMinor?.let(money) ?: "Cash estimate unavailable", style = MaterialTheme.typography.headlineSmall)
                Text("Set opening cash in Plan and identify unknown payment methods in Activity. Missing records can change this estimate.", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = onPlan) { Text("Update cash assumptions") }
            } }
            item { Panel("Accounts used this month") {
                val accounts = summary.transactions.groupBy { listOfNotNull(it.institution, it.accountLast4?.let { suffix -> "••$suffix" }).joinToString(" ").ifBlank { "Account not available" } }
                if (accounts.isEmpty()) Text("Accounts appear when an alert contains account information.")
                accounts.forEach { (name, transactions) ->
                    Text(name, style = MaterialTheme.typography.titleSmall)
                    Text("${transactions.size} records · ${transactions.count { it.direction == "DEBIT" }} outgoing · ${transactions.count { it.direction == "CREDIT" }} incoming", style = MaterialTheme.typography.bodySmall)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            } }
            item { Panel("Money movement", "These amounts are shown separately to make totals easier to check.") {
                BreakdownRow("Self-transfers", money(summary.transferMinor), 0f, ChartColors[0])
                BreakdownRow("Refunds received", money(summary.refundMinor), 0f, IncomeGreen)
                BreakdownRow("EMI + card settlements", money(summary.debtPaymentsMinor), 0f, ChartColors[2])
                Text("Transfers are excluded from income and spending. Card settlements are excluded from spending; purchases count when they occur.", style = MaterialTheme.typography.bodySmall)
            } }
        }
        if (section == "Actions") {
            item { Text("Your next steps", style = MaterialTheme.typography.titleLarge) }
            if (summary.incomeMinor == 0L) item { InfoCard("Complete this month's income", "Add any missing income before comparing savings or debt ratios.", Icons.Outlined.AddCircleOutline, onActivity) }
            if (summary.unclassifiedMinor > 0) item { InfoCard("Classify ${money(summary.unclassifiedMinor)} of spending", "Mark payments as essential or discretionary in transaction details.", Icons.Outlined.Label, onActivity) }
            if (summary.savingsRate != null) item { InfoCard("Savings: ${(summary.savingsRate!! * 100).toInt()}% of income", "Your chosen target is ${state.finance.preferences.savingsTargetPercent}%. Adjust the monthly budget to compare your plan with actual spending.", Icons.Outlined.Savings, onPlan) }
            item { InfoCard("Emergency reserve", summary.emergencyTargetMinor?.let { "Your ${state.finance.preferences.emergencyMonths}-month target is ${money(it)} based on recorded essential spending and debt commitments." } ?: "Classify the month's spending and record essentials to estimate a reserve target. Cash buffer also needs opening cash in Plan.", Icons.Outlined.Shield, onPlan) }
            if (summary.monthlyEmiMinor > 0) item { InfoCard("Debt commitments", "Monthly EMI ${money(summary.monthlyEmiMinor)} · ${summary.debtToIncome?.let { "${(it * 100).toInt()}% of recorded income" } ?: "add income to see the ratio"}. Compare payoff options in Plan.", Icons.Outlined.AccountBalance, onPlan) }
            item { InfoCard("Check upcoming payments and goals", "Keep due dates, outstanding balances, and goal contributions current in your plan.", Icons.Outlined.EventAvailable, onPlan) }
            item { Text("These checks reflect the workbook's thresholds and the records you have entered. Missing alerts or inputs can change the picture.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
fun MonthControl(month: YearMonth, onMonth: (YearMonth) -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(onClick = { onMonth(month.minusMonths(1)) }, enabled = month > YearMonth.of(1900, 1)) { Icon(Icons.Outlined.ChevronLeft, "Previous month") }
            Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy")), style = MaterialTheme.typography.titleSmall)
            IconButton(onClick = { onMonth(month.plusMonths(1)) }, enabled = month < YearMonth.of(2200, 12)) { Icon(Icons.Outlined.ChevronRight, "Next month") }
        }
    }
}

@Composable
private fun Metric(label: String, amount: String, icon: ImageVector, tint: Color, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, Modifier.size(16.dp), tint); Spacer(Modifier.width(5.dp)); Text(label, style = MaterialTheme.typography.labelMedium) }
        Text(amount, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}
@Composable
private fun StatCard(title: String, value: String, subtitle: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Surface(modifier = if (onClick == null) modifier else modifier.clickable(onClick = onClick), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
@Composable
private fun Panel(title: String, subtitle: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            content()
        }
    }
}
@Composable
private fun InfoCard(title: String, body: String, icon: ImageVector, onClick: () -> Unit) {
    Surface(Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.padding(18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) { Text(title, style = MaterialTheme.typography.titleSmall); Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.size(18.dp))
        }
    }
}
@Composable
private fun BreakdownRow(label: String, amount: String, ratio: Float, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(color, CircleShape)); Spacer(Modifier.width(8.dp))
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text(amount, style = MaterialTheme.typography.labelLarge)
        }
        if (ratio > 0f) LinearProgressIndicator(progress = { ratio.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(5.dp), color = color)
    }
}
@Composable
private fun SectionTitle(title: String, action: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) { Text(title, style = MaterialTheme.typography.titleMedium); TextButton(onClick = onClick) { Text(action) } }
}
