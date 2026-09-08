package app.txnsheet.personal.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.txnsheet.personal.data.local.BudgetEntity
import app.txnsheet.personal.data.local.DebtEntity
import app.txnsheet.personal.data.local.FinanceData
import app.txnsheet.personal.data.local.FinancePreferencesEntity
import app.txnsheet.personal.data.local.GoalEntity
import app.txnsheet.personal.data.local.TransactionEntity
import app.txnsheet.personal.domain.FinanceCalculator
import app.txnsheet.personal.ui.formatMoney
import app.txnsheet.personal.ui.theme.LocalTxnSemanticColors
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

private val PlanMonthFormat = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())
private val PlanDateFormat = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())

/** Budget, cash flow, EMI, debt and goal views backed exclusively by the local ledger. */
@Composable
fun PlanningScreen(
    transactions: List<TransactionEntity>,
    finance: FinanceData,
    month: YearMonth,
    zoneId: String,
    currency: String,
    onSaveBudget: (BudgetEntity) -> Unit,
    onSaveDebt: (DebtEntity) -> Unit,
    onDeleteDebt: (String) -> Unit,
    onSaveGoal: (GoalEntity) -> Unit,
    onDeleteGoal: (String) -> Unit,
    onSavePreferences: (FinancePreferencesEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    var tab by rememberSaveable { mutableStateOf("Budget") }
    var selectedMonthText by rememberSaveable(month.toString()) { mutableStateOf(month.toString()) }
    val selectedMonth = YearMonth.parse(selectedMonthText)
    val today = LocalDate.now(ZoneId.of(zoneId))
    val summary = remember(transactions, finance, selectedMonth, zoneId, currency) {
        FinanceCalculator.summary(transactions, finance, selectedMonth, ZoneId.of(zoneId), currency)
    }
    val cashForecast = remember(finance, selectedMonth) { FinanceCalculator.forecast(finance, selectedMonth) }
    var editBudget by rememberSaveable { mutableStateOf(false) }
    var editPreferences by rememberSaveable { mutableStateOf(false) }
    var addDebt by rememberSaveable { mutableStateOf(false) }
    var editDebtId by rememberSaveable { mutableStateOf<String?>(null) }
    val editDebt = finance.debts.firstOrNull { it.id == editDebtId }
    var deleteDebt by remember { mutableStateOf<DebtEntity?>(null) }
    var addGoal by rememberSaveable { mutableStateOf(false) }
    var editGoalId by rememberSaveable { mutableStateOf<String?>(null) }
    val editGoal = finance.goals.firstOrNull { it.id == editGoalId }
    var deleteGoal by remember { mutableStateOf<GoalEntity?>(null) }
    var smallestFirst by rememberSaveable { mutableStateOf(false) }
    val budget = finance.budgets.firstOrNull { it.month == selectedMonth.toString() }
    val semantic = LocalTxnSemanticColors.current

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text("Your plan", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
            PlanCaption("A clear view of today. A practical plan for tomorrow.")
            Spacer(Modifier.height(16.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Budget", "Cash flow", "Debts", "Goals", "Settings").forEach { name ->
                    FilterChip(
                        selected = tab == name,
                        onClick = { tab = name },
                        label = { Text(name) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            }
        }
        if (tab in listOf("Budget", "Cash flow", "Settings")) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { selectedMonthText = selectedMonth.minusMonths(1).toString() }, enabled = selectedMonth > YearMonth.of(1900, 1)) {
                        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "Previous month")
                    }
                    Text(selectedMonth.format(PlanMonthFormat), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { selectedMonthText = selectedMonth.plusMonths(1).toString() }, enabled = selectedMonth < YearMonth.of(2200, 12)) {
                        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "Next month")
                    }
                }
            }
        }
        when (tab) {
            "Budget" -> {
                item {
                    PlanCard("Monthly budget", Icons.Outlined.Tune) {
                        PlanCaption("Compare the amounts you planned with transactions recorded this month.")
                        OutlinedButton(onClick = { editBudget = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Icon(Icons.Outlined.Edit, null, Modifier.size(18.dp))
                            Text(if (budget == null) "  Set this month's budget" else "  Edit this month's budget")
                        }
                    }
                }
                item { BudgetLine("Income", "Money received", summary.incomeMinor, budget?.incomeMinor, currency, true, semantic.credit, Icons.Outlined.ArrowDownward) }
                item { BudgetLine("Essentials", "Needs such as rent, food and utilities", summary.essentialMinor, budget?.essentialMinor, currency, false, MaterialTheme.colorScheme.primary, Icons.Outlined.CheckCircle) }
                item { BudgetLine("Discretionary", "Optional spending and lifestyle", summary.discretionaryMinor, budget?.discretionaryMinor, currency, false, MaterialTheme.colorScheme.tertiary, Icons.Outlined.ArrowUpward) }
                item { BudgetLine("Debt payments", "Loan payments and card settlements", summary.debtPaymentsMinor, budget?.debtMinor, currency, false, semantic.review, Icons.Outlined.CreditCard) }
                item { BudgetLine("Savings target", "Recorded surplus: income minus expenses", summary.savingsMinor, budget?.savingsMinor, currency, true, semantic.credit, Icons.Outlined.Savings) }
                if (summary.unclassifiedMinor > 0L) {
                    item {
                        PlanCard("Spending needs a category", Icons.Outlined.Info) {
                            PlanAmount(summary.unclassifiedMinor, currency)
                            PlanCaption("Set the category in Activity to include this spending in the right budget group.")
                        }
                    }
                }
                item { PlanCaption("Savings here means surplus, not a verified savings-account balance. Transfers between your own accounts do not count as income or spending.") }
            }
            "Cash flow" -> {
                item {
                    PlanCard("Recorded this month", Icons.Outlined.CalendarMonth) {
                        PlanValue("Income", formatMoney(summary.incomeMinor, currency))
                        PlanValue("Expenses", formatMoney(summary.expenseMinor, currency))
                        HorizontalDivider()
                        PlanValue("Surplus / shortfall", formatMoney(summary.savingsMinor, currency))
                        PlanValue("Estimated cash balance", summary.cashBalanceMinor?.let { formatMoney(it, currency) } ?: "Check cash assumptions")
                        PlanCaption("Confirmed transactions only. Set opening cash and its month in Settings. Identify credit versus debit cards in Activity to make a cash estimate possible.")
                    }
                }
                item {
                    Text("Next 12 months", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                    PlanCaption("Your latest entered budget carries forward until a newer one starts. Expected income and active debt payments fill their matching gaps; other missing amounts remain unknown.")
                }
                items(cashForecast, key = { it.month.toString() }) { forecast ->
                    var expanded by rememberSaveable(forecast.month.toString()) { mutableStateOf(false) }
                    PlanCard(forecast.month.format(PlanMonthFormat), Icons.Outlined.CalendarMonth) {
                        PlanValue("Projected income", forecast.incomeMinor?.let { formatMoney(it, currency) } ?: "Set budget")
                        PlanValue("Projected net cash", forecast.netMinor?.let { formatMoney(it, currency) } ?: "Complete budget")
                        PlanValue("Closing cash", forecast.closingMinor?.let { formatMoney(it, currency) } ?: "Opening cash / budget needed")
                        if (expanded) {
                            HorizontalDivider()
                            PlanValue("Opening cash", forecast.openingMinor?.let { formatMoney(it, currency) } ?: "Not set")
                            PlanValue("Essentials", forecast.essentialMinor?.let { formatMoney(it, currency) } ?: "Set budget")
                            PlanValue("Discretionary", forecast.discretionaryMinor?.let { formatMoney(it, currency) } ?: "Set budget")
                            PlanValue("Debt payments", forecast.debtMinor?.let { formatMoney(it, currency) } ?: "Set budget")
                            PlanValue("Savings allocation", forecast.savingsMinor?.let { formatMoney(it, currency) } ?: "Set budget")
                            PlanValue("Cash runway", forecast.runwayMonths?.let { String.format(Locale.getDefault(), "%.1f months", it) } ?: "Not enough information")
                        }
                        TextButton(onClick = { expanded = !expanded }, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(if (expanded) "Hide breakdown" else "Show breakdown")
                        }
                    }
                }
                item { PlanCaption("These are estimates from your plan. They do not read or verify your bank balance.") }
            }
            "Debts" -> {
                item {
                    PlanCard("EMI & debt planner", Icons.Outlined.CreditCard) {
                        PlanValue("Outstanding debt", formatMoney(summary.outstandingDebtMinor, currency))
                        PlanValue("Monthly EMIs", formatMoney(summary.monthlyEmiMinor, currency))
                        PlanCaption("Track balances and due dates you enter. Update the outstanding balance when a payment clears.")
                        Button(onClick = { addDebt = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Icon(Icons.Outlined.Add, null)
                            Text("  Add debt")
                        }
                    }
                }
                item {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !smallestFirst, onClick = { smallestFirst = false }, label = { Text("Highest interest first") }, modifier = Modifier.heightIn(min = 48.dp))
                        FilterChip(selected = smallestFirst, onClick = { smallestFirst = true }, label = { Text("Smallest balance first") }, modifier = Modifier.heightIn(min = 48.dp))
                    }
                    PlanCaption(if (smallestFirst) "Small balances first can make individual debts quicker to clear." else "Higher rates first can reduce interest when terms are otherwise equal.")
                }
                if (finance.debts.isEmpty()) {
                    item { PlanEmpty("No debts added", "Add a loan or credit balance to see EMI dates, repayment estimates and a payoff order.") }
                }
                val sortedDebts = if (smallestFirst) finance.debts.sortedBy { it.outstandingMinor } else finance.debts.sortedByDescending { it.annualInterestPercent }
                items(sortedDebts.sortedBy { !it.active }, key = DebtEntity::id) { debt ->
                    val payoff = remember(debt) { FinanceCalculator.payoff(debt) }
                    val due = FinanceCalculator.nextDue(debt, today)
                    PlanCard(debt.name, Icons.Outlined.CreditCard) {
                        PlanCaption("${debt.type} · ${debt.priority} priority · ${if (debt.active) "Active" else "Paused"}")
                        PlanAmount(debt.outstandingMinor, currency)
                        PlanCaption("Outstanding balance")
                        PlanValue("Monthly payment", formatMoney(debt.emiMinor, currency))
                        PlanValue("Extra each month", formatMoney(debt.extraPaymentMinor, currency))
                        PlanValue("Annual interest", "${decimalText(debt.annualInterestPercent)}%")
                        PlanValue("Next due", due?.format(PlanDateFormat) ?: "No payment due")
                        PlanValue("EMIs marked paid", "${debt.emisPaid} of ${debt.tenureMonths}")
                        PlanValue("Loan started", runCatching { LocalDate.parse(debt.startDate).format(PlanDateFormat) }.getOrDefault(debt.startDate))
                        PlanValue("Original amount", formatMoney(debt.originalMinor, currency))
                        HorizontalDivider()
                        if (payoff.nonAmortizing) {
                            Text("Payment does not cover monthly interest", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                            PlanCaption("The entered payment cannot clear this balance at this rate. Check the payment and interest values.")
                        } else {
                            PlanValue("Estimated payments left", payoff.months?.toString() ?: "Not available")
                            PlanValue("Estimated interest left", payoff.interestMinor?.let { formatMoney(it, currency) } ?: "Not available")
                        }
                        PlanCaption("Estimate assumes a fixed rate and regular monthly payments, including your extra payment. Fees and lender-specific terms are excluded.")
                        PlanEditActions(onEdit = { editDebtId = debt.id }, onDelete = { deleteDebt = debt }, itemLabel = debt.name)
                    }
                }
            }
            "Goals" -> {
                item {
                    PlanCard("Make room for what matters", Icons.Outlined.Flag) {
                        PlanCaption("Set a target and track the amount you have put aside. Goal balances are updated by you.")
                        Button(onClick = { addGoal = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Icon(Icons.Outlined.Add, null)
                            Text("  Add a goal")
                        }
                    }
                }
                if (finance.goals.isEmpty()) {
                    item { PlanEmpty("Your next milestone", "Add an emergency fund, a purchase or another goal to see the gap and monthly contribution needed.") }
                }
                items(finance.goals, key = GoalEntity::id) { goal ->
                    val targetDate = runCatching { LocalDate.parse(goal.targetDate) }.getOrNull()
                    val gap = (goal.targetMinor - goal.currentMinor).coerceAtLeast(0L)
                    val done = gap == 0L
                    val overdue = targetDate?.isBefore(today) == true && !done
                    val progress = if (goal.targetMinor > 0) (goal.currentMinor.toDouble() / goal.targetMinor).coerceIn(0.0, 1.0).toFloat() else 0f
                    PlanCard(goal.name, if (done) Icons.Outlined.CheckCircle else Icons.Outlined.Flag) {
                        PlanCaption("${goal.priority} priority · ${when { done -> "Target reached"; overdue -> "Target date passed"; else -> "In progress" }}")
                        PlanAmount(goal.currentMinor, currency)
                        PlanCaption("of ${formatMoney(goal.targetMinor, currency)} saved · ${(progress * 100).toInt()}%")
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(), color = if (done) semantic.credit else MaterialTheme.colorScheme.primary)
                        PlanValue("Still needed", formatMoney(gap, currency))
                        PlanValue("Target date", targetDate?.format(PlanDateFormat) ?: "Set a valid date")
                        if (!done && !overdue) PlanValue("Needed each month", formatMoney(FinanceCalculator.goalMonthlyRequired(goal, today), currency))
                        if (overdue) Text("Update your target date or saved amount to refresh the monthly plan.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                        PlanEditActions(onEdit = { editGoalId = goal.id }, onDelete = { deleteGoal = goal }, itemLabel = goal.name)
                    }
                }
            }
            "Settings" -> {
                item {
                    PlanCard("Planning assumptions", Icons.Outlined.Tune) {
                        PlanCaption("These values power your dashboard and cash-flow estimates. They never change a recorded transaction.")
                        PlanValue("Opening cash", finance.preferences.openingCashMinor?.let { formatMoney(it, currency) } ?: "Not set")
                        PlanValue("Opening balance month", finance.preferences.openingMonth.ifBlank { "Not set" })
                        PlanValue("Expected monthly income", finance.preferences.monthlyIncomeMinor?.let { formatMoney(it, currency) } ?: "Not set")
                        PlanValue("Emergency fund", "${finance.preferences.emergencyMonths} months of essentials")
                        PlanValue("Savings target", "${finance.preferences.savingsTargetPercent}% of income")
                        PlanValue("Essentials limit", "${finance.preferences.essentialCapPercent}% of income")
                        OutlinedButton(onClick = { editPreferences = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Icon(Icons.Outlined.Edit, null, Modifier.size(18.dp))
                            Text("  Edit assumptions")
                        }
                    }
                }
                item {
                    PlanCard("How the numbers fit together", Icons.Outlined.Info) {
                        PlanCaption("Budget: planned amounts compared with this month's confirmed activity.")
                        PlanCaption("Cash flow: opening cash plus planned income, less spending, debt payments and savings allocations.")
                        PlanCaption("Debts: balances and repayment terms you enter. EMI alerts are not proof of a payment.")
                        PlanCaption("Goals: amounts you mark as saved; the same money is not deducted again from your transactions.")
                    }
                }
            }
        }
    }

    if (editBudget) BudgetEditor(budget ?: BudgetEntity(month = selectedMonth.toString()), currency, onDismiss = { editBudget = false }, onSave = { onSaveBudget(it); editBudget = false })
    if (editPreferences) PreferencesEditor(finance.preferences, selectedMonth, currency, onDismiss = { editPreferences = false }, onSave = { onSavePreferences(it); editPreferences = false })
    if (addDebt || editDebt != null) DebtEditor(editDebt, today, currency, onDismiss = { addDebt = false; editDebtId = null }, onSave = { onSaveDebt(it); addDebt = false; editDebtId = null })
    if (addGoal || editGoal != null) GoalEditor(editGoal, today, currency, onDismiss = { addGoal = false; editGoalId = null }, onSave = { onSaveGoal(it); addGoal = false; editGoalId = null })
    deleteDebt?.let { debt ->
        PlanDeleteDialog(debt.name, "This removes the debt plan. Your recorded transactions remain in Activity.", onDismiss = { deleteDebt = null }, onConfirm = { onDeleteDebt(debt.id); deleteDebt = null })
    }
    deleteGoal?.let { goal ->
        PlanDeleteDialog(goal.name, "This removes the goal and its saved progress. Your recorded transactions remain in Activity.", onDismiss = { deleteGoal = null }, onConfirm = { onDeleteGoal(goal.id); deleteGoal = null })
    }
}

@Composable
private fun PlanCard(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).semantics { heading() })
            }
            content()
        }
    }
}

@Composable
private fun PlanCaption(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun PlanAmount(amount: Long, currency: String) {
    Text(formatMoney(amount, currency), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
}

@Composable
private fun PlanValue(label: String, value: String) {
    // Keep large text and long currency amounts fully visible instead of truncating them.
    if (LocalDensity.current.fontScale >= 1.2f || value.length > 22) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        }
    } else {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, Modifier.weight(1.15f), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun BudgetLine(title: String, subtitle: String, actual: Long, planned: Long?, currency: String, target: Boolean, color: Color, icon: ImageVector) {
    PlanCard(title, icon) {
        PlanCaption(subtitle)
        PlanCaption(if (title == "Savings target") "Recorded surplus" else "Recorded this month")
        PlanAmount(actual, currency)
        PlanValue("Planned", planned?.let { formatMoney(it, currency) } ?: "No plan set")
        if (planned != null) {
            val fraction = if (planned > 0L) (actual.toDouble() / planned).coerceIn(0.0, 1.0).toFloat() else if (actual > 0) 1f else 0f
            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth(), color = if (!target && actual > planned) MaterialTheme.colorScheme.error else color)
            val difference = actual - planned
            val label = when {
                target && difference >= 0 -> "Target reached"
                target -> "${formatMoney(-difference, currency)} to target"
                difference > 0 -> "${formatMoney(difference, currency)} over plan"
                else -> "${formatMoney(-difference, currency)} left in plan"
            }
            Text(label, style = MaterialTheme.typography.labelLarge, color = if (!target && difference > 0) MaterialTheme.colorScheme.error else color)
        }
    }
}

@Composable
private fun PlanEmpty(title: String, body: String) {
    PlanCard(title, Icons.Outlined.Info) { PlanCaption(body) }
}

@Composable
private fun PlanEditActions(onEdit: () -> Unit, onDelete: () -> Unit, itemLabel: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
            Icon(Icons.Outlined.Edit, null, Modifier.size(18.dp))
            Text("  Edit")
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Outlined.DeleteOutline, "Delete $itemLabel", tint = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun PlanDeleteDialog(name: String, body: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Delete $name?") }, text = { Text(body) }, confirmButton = { TextButton(onClick = onConfirm) { Text("Delete", color = MaterialTheme.colorScheme.error) } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

private data class PlanField(
    val key: String,
    val label: String,
    val initial: String,
    val keyboard: KeyboardType = KeyboardType.Text,
    val hint: String? = null,
    val choices: List<String> = emptyList(),
)

@Composable
private fun PlanEditor(title: String, explanation: String, fields: List<PlanField>, onDismiss: () -> Unit, onSave: (Map<String, String>) -> Unit) {
    var values by rememberSaveable(title, fields) { mutableStateOf(fields.associate { it.key to it.initial }) }
    var error by remember { mutableStateOf<String?>(null) }
    val height = LocalConfiguration.current.screenHeightDp.dp * 0.88f
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.imePadding().padding(16.dp).widthIn(max = 560.dp).fillMaxWidth().heightIn(max = height), shape = MaterialTheme.shapes.extraLarge) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, "Close editor") }
                }
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PlanCaption(explanation)
                    fields.forEach { field ->
                        if (field.choices.isNotEmpty()) {
                            Text(field.label, style = MaterialTheme.typography.labelLarge)
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                (field.choices + listOf(field.initial).filter { it.isNotBlank() && it !in field.choices }).forEach { choice ->
                                    FilterChip(selected = values[field.key] == choice, onClick = { values = values + (field.key to choice); error = null }, label = { Text(choice) }, modifier = Modifier.heightIn(min = 48.dp))
                                }
                            }
                        } else {
                            OutlinedTextField(value = values[field.key].orEmpty(), onValueChange = { values = values + (field.key to it.take(160)); error = null }, label = { Text(field.label) }, supportingText = field.hint?.let { hint -> { Text(hint) } }, keyboardOptions = KeyboardOptions(keyboardType = field.keyboard), singleLine = true, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                Button(onClick = { try { onSave(values.toMap()) } catch (failure: IllegalArgumentException) { error = failure.message ?: "Check the values and try again." } catch (_: ArithmeticException) { error = "Enter amounts with no more than two decimal places." } }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("Save")
                }
            }
        }
    }
}

@Composable
private fun BudgetEditor(budget: BudgetEntity, currency: String, onDismiss: () -> Unit, onSave: (BudgetEntity) -> Unit) {
    val fields = listOf(
        moneyField("income", "Planned income ($currency)", budget.incomeMinor),
        moneyField("essential", "Essentials ($currency)", budget.essentialMinor),
        moneyField("discretionary", "Discretionary ($currency)", budget.discretionaryMinor),
        moneyField("debt", "Debt payments ($currency)", budget.debtMinor),
        moneyField("savings", "Savings allocation ($currency)", budget.savingsMinor),
    )
    PlanEditor("Budget · ${YearMonth.parse(budget.month).format(PlanMonthFormat)}", "Leave a value blank if it is not planned yet. Enter 0 only when you intend no income or spending in that group.", fields, onDismiss) { values ->
        onSave(budget.copy(incomeMinor = moneyValue(values, "income", "Income"), essentialMinor = moneyValue(values, "essential", "Essentials"), discretionaryMinor = moneyValue(values, "discretionary", "Discretionary"), debtMinor = moneyValue(values, "debt", "Debt payments"), savingsMinor = moneyValue(values, "savings", "Savings")))
    }
}

@Composable
private fun PreferencesEditor(preferences: FinancePreferencesEntity, month: YearMonth, currency: String, onDismiss: () -> Unit, onSave: (FinancePreferencesEntity) -> Unit) {
    val fields = listOf(
        moneyField("opening", "Opening cash ($currency)", preferences.openingCashMinor),
        PlanField("month", "Opening cash month", preferences.openingMonth.ifBlank { month.toString() }, hint = "YYYY-MM · balance at the start of this month"),
        moneyField("income", "Expected monthly income ($currency)", preferences.monthlyIncomeMinor),
        PlanField("emergency", "Emergency fund months", preferences.emergencyMonths.toString(), KeyboardType.Number, "1–60 months of essential spending"),
        PlanField("savings", "Savings target (%)", preferences.savingsTargetPercent.toString(), KeyboardType.Number, "0–100 percent of income"),
        PlanField("essential", "Essentials limit (%)", preferences.essentialCapPercent.toString(), KeyboardType.Number, "0–100 percent of income"),
    )
    PlanEditor("Planning assumptions", "Use the cash you had before this month's transactions. Leave opening cash blank if you do not know it.", fields, onDismiss) { values ->
        val opening = moneyValue(values, "opening", "Opening cash", allowNegative = true)
        val openingMonth = values["month"].orEmpty().trim()
        require(opening == null || validMonth(openingMonth)) { "Enter the opening cash month as YYYY-MM." }
        require(openingMonth.isBlank() || validMonth(openingMonth)) { "Opening cash month must be YYYY-MM." }
        onSave(preferences.copy(openingCashMinor = opening, openingMonth = if (opening == null) "" else openingMonth, monthlyIncomeMinor = moneyValue(values, "income", "Expected income"), emergencyMonths = intValue(values, "emergency", "Emergency months", 1..60), savingsTargetPercent = intValue(values, "savings", "Savings target", 0..100), essentialCapPercent = intValue(values, "essential", "Essentials limit", 0..100)))
    }
}

@Composable
private fun DebtEditor(debt: DebtEntity?, today: LocalDate, currency: String, onDismiss: () -> Unit, onSave: (DebtEntity) -> Unit) {
    val fields = listOf(
        PlanField("name", "Debt name", debt?.name.orEmpty(), hint = "For example: home loan or a credit card"),
        PlanField("type", "Debt type", debt?.type ?: "Other", choices = listOf("Personal loan", "Home loan", "Vehicle loan", "Credit card", "Education", "Other")),
        moneyField("original", "Original amount ($currency)", debt?.originalMinor),
        moneyField("outstanding", "Outstanding balance ($currency)", debt?.outstandingMinor),
        PlanField("apr", "Annual interest (%)", debt?.annualInterestPercent?.let(::decimalText).orEmpty(), KeyboardType.Decimal, "Annual rate, not the monthly rate"),
        moneyField("emi", "Monthly EMI / payment ($currency)", debt?.emiMinor),
        moneyField("extra", "Extra monthly payment ($currency)", debt?.extraPaymentMinor, "Optional · additional payment each month"),
        PlanField("tenure", "Original term (months)", debt?.tenureMonths?.toString().orEmpty(), KeyboardType.Number, "1–600 months"),
        PlanField("paid", "EMIs already paid", debt?.emisPaid?.toString() ?: "0", KeyboardType.Number),
        PlanField("due", "Payment day of month", debt?.dueDay?.toString().orEmpty(), KeyboardType.Number, "1–31 · shorter months use their last day"),
        PlanField("start", "Loan start date", debt?.startDate ?: today.toString(), hint = "YYYY-MM-DD"),
        PlanField("priority", "Priority", debt?.priority ?: "Medium", choices = listOf("High", "Medium", "Low")),
        PlanField("active", "Include in the plan", if (debt?.active != false) "Active" else "Paused", choices = listOf("Active", "Paused")),
    )
    PlanEditor(if (debt == null) "Add debt" else "Edit debt", "Enter your current loan details. Repayment estimates use the outstanding amount, interest rate and monthly payment.", fields, onDismiss) { values ->
        val name = requiredName(values, "Debt name")
        val original = requiredMoney(values, "original", "Original amount", positive = true)
        val outstanding = requiredMoney(values, "outstanding", "Outstanding balance")
        val emi = requiredMoney(values, "emi", "Monthly payment", positive = true)
        val apr = values["apr"].orEmpty().trim().toDoubleOrNull()
        require(apr != null && apr.isFinite() && apr in 0.0..1000.0) { "Annual interest must be between 0 and 1000 percent." }
        val tenure = intValue(values, "tenure", "Term", 1..600)
        val paid = intValue(values, "paid", "EMIs paid", 0..tenure)
        val start = dateValue(values["start"].orEmpty(), "Start date")
        onSave(DebtEntity(id = debt?.id ?: UUID.randomUUID().toString(), name = name, type = values["type"].orEmpty(), originalMinor = original, outstandingMinor = outstanding, annualInterestPercent = apr, emiMinor = emi, tenureMonths = tenure, emisPaid = paid, dueDay = intValue(values, "due", "Payment day", 1..31), startDate = start.toString(), extraPaymentMinor = moneyValue(values, "extra", "Extra payment") ?: 0L, priority = values["priority"].orEmpty(), active = values["active"] == "Active"))
    }
}

@Composable
private fun GoalEditor(goal: GoalEntity?, today: LocalDate, currency: String, onDismiss: () -> Unit, onSave: (GoalEntity) -> Unit) {
    val fields = listOf(
        PlanField("name", "Goal name", goal?.name.orEmpty()),
        moneyField("target", "Target amount ($currency)", goal?.targetMinor),
        moneyField("current", "Already saved ($currency)", goal?.currentMinor),
        PlanField("date", "Target date", goal?.targetDate ?: today.plusYears(1).toString(), hint = "YYYY-MM-DD"),
        PlanField("priority", "Priority", goal?.priority ?: "Medium", choices = listOf("High", "Medium", "Low")),
    )
    PlanEditor(if (goal == null) "Add goal" else "Edit goal", "Enter the amount you have actually put aside. The app calculates what remains and the monthly amount needed.", fields, onDismiss) { values ->
        onSave(GoalEntity(id = goal?.id ?: UUID.randomUUID().toString(), name = requiredName(values, "Goal name"), targetMinor = requiredMoney(values, "target", "Target amount", positive = true), currentMinor = moneyValue(values, "current", "Already saved") ?: 0L, targetDate = dateValue(values["date"].orEmpty(), "Target date").toString(), priority = values["priority"].orEmpty()))
    }
}

private fun moneyField(key: String, label: String, amount: Long?, hint: String? = null) = PlanField(key, label, amount?.let { BigDecimal.valueOf(it).movePointLeft(2).stripTrailingZeros().toPlainString() }.orEmpty(), KeyboardType.Decimal, hint)

private fun moneyValue(values: Map<String, String>, key: String, label: String, allowNegative: Boolean = false): Long? {
    val input = values[key].orEmpty().trim()
    if (input.isBlank()) return null
    require(input.matches(Regex(if (allowNegative) "-?\\d+(?:\\.\\d{1,2})?" else "\\d+(?:\\.\\d{1,2})?"))) { "$label must be an amount with up to two decimal places, without commas." }
    val minor = input.toBigDecimalOrNull()?.movePointRight(2)?.let { runCatching { it.longValueExact() }.getOrNull() }
    require(minor != null && minor in (if (allowNegative) -100_000_000_000_000L else 0L)..100_000_000_000_000L) { "$label is outside the supported amount range." }
    return minor
}

private fun requiredMoney(values: Map<String, String>, key: String, label: String, positive: Boolean = false): Long {
    val value = moneyValue(values, key, label)
    require(value != null && (!positive || value > 0L)) { "$label ${if (positive) "must be greater than zero" else "is required"}." }
    return value
}

private fun intValue(values: Map<String, String>, key: String, label: String, range: IntRange): Int {
    val value = values[key].orEmpty().trim().toIntOrNull()
    require(value != null && value in range) { "$label must be a whole number from ${range.first} to ${range.last}." }
    return value
}

private fun requiredName(values: Map<String, String>, label: String): String {
    val value = values["name"].orEmpty().trim()
    require(value.isNotBlank() && value.length <= 80) { "$label is required and must be 80 characters or fewer." }
    return value
}

private fun dateValue(input: String, label: String): LocalDate {
    val value = runCatching { LocalDate.parse(input.trim()) }.getOrNull()
    require(value != null && value.year in 1900..2200) { "$label must be a real date in YYYY-MM-DD format (1900–2200)." }
    return value
}

private fun validMonth(input: String): Boolean = runCatching { YearMonth.parse(input).year in 1900..2200 }.getOrDefault(false)

private fun decimalText(value: Double): String = BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
