package app.txnsheet.personal.domain

import app.txnsheet.personal.data.local.DebtEntity
import app.txnsheet.personal.data.local.FinanceData
import app.txnsheet.personal.data.local.GoalEntity
import app.txnsheet.personal.data.local.TransactionAnnotationEntity
import app.txnsheet.personal.data.local.TransactionEntity
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale

data class MonthlyFinance(
    val incomeMinor: Long,
    /** Net purchases and EMI repayments, less refunds; card settlements are excluded. */
    val expenseMinor: Long,
    val savingsMinor: Long,
    val essentialMinor: Long,
    val discretionaryMinor: Long,
    val unclassifiedMinor: Long,
    /** Financing paid: EMI repayments plus debit-side credit card settlements. */
    val debtPaymentsMinor: Long,
    /** Outgoing transfer legs only, so two sides of an own-account transfer count once. */
    val transferMinor: Long,
    val refundMinor: Long,
    val monthlyEmiMinor: Long,
    val outstandingDebtMinor: Long,
    val savingsRate: Double?,
    val debtToIncome: Double?,
    val emergencyTargetMinor: Long?,
    val cashBufferMonths: Double?,
    val cashBalanceMinor: Long?,
    /** Gross expense breakdowns. Refunds remain separate because their purchase may predate this month. */
    val categorySpend: Map<String, Long>,
    val merchantSpend: Map<String, Long>,
    val paymentSpend: Map<String, Long>,
    val transactions: List<TransactionEntity>,
)

data class CashFlowMonth(
    val month: YearMonth,
    val openingMinor: Long?,
    val incomeMinor: Long?,
    val essentialMinor: Long?,
    val discretionaryMinor: Long?,
    val debtMinor: Long?,
    val savingsMinor: Long?,
    val netMinor: Long?,
    val closingMinor: Long?,
    val runwayMonths: Double?,
)

data class DebtPayoff(val months: Int?, val interestMinor: Long?, val nonAmortizing: Boolean)

/** Local arithmetic only. These estimates cannot observe accounts or spending outside captured data. */
object FinanceCalculator {
    private val confirmedStatuses = setOf(
        "LOCAL", "SYNCED", "QUEUED", "RETRY", "SYNCING", "AUTH_REQUIRED", "SCHEMA_ERROR", "SHEET_REQUIRED",
    )
    private val flowTypes = setOf("INCOME", "EXPENSE", "TRANSFER", "REFUND", "CARD_PAYMENT")
    private val whitespace = Regex("\\s+")
    private val transferCategories = setOf("TRANSFER", "SELF TRANSFER", "INTERNAL TRANSFER", "OWN ACCOUNT TRANSFER")
    private val cardPaymentCategories = setOf("CREDIT CARD PAYMENT", "CARD PAYMENT", "CREDIT CARD BILL", "CARD SETTLEMENT")
    private val debtCategories = setOf("EMI", "LOAN EMI", "LOAN PAYMENT", "LOAN REPAYMENT", "DEBT", "DEBT PAYMENT", "DEBT REPAYMENT")
    private val essentialCategories = setOf(
        "RENT", "HOUSING", "GROCERIES", "GROCERY", "UTILITIES", "ELECTRICITY", "WATER", "GAS",
        "HEALTHCARE", "HEALTH", "MEDICAL", "MEDICINE", "EDUCATION", "TRANSPORT", "TRANSPORTATION", "INSURANCE",
    )
    private val discretionaryCategories = setOf(
        "DINING", "DINING OUT", "RESTAURANTS", "ENTERTAINMENT", "SHOPPING", "TRAVEL", "SUBSCRIPTIONS",
        "SUBSCRIPTION", "LEISURE", "GIFTS", "HOBBIES",
    )

    fun flowType(transaction: TransactionEntity, annotation: TransactionAnnotationEntity? = null): String {
        annotation?.flowType?.trim()?.uppercase(Locale.ROOT)?.takeIf { it in flowTypes }?.let { return it }
        // Cash withdrawn from an ATM moves money from bank to wallet, without a purchase yet.
        if (transaction.method.normalized() == "ATM") return "TRANSFER"
        return when (transaction.category.normalized()) {
            in transferCategories -> "TRANSFER"
            in cardPaymentCategories -> "CARD_PAYMENT"
            "REFUND", "REFUNDS", "REVERSAL" -> "REFUND"
            else -> if (transaction.direction.normalized() == "CREDIT") "INCOME" else "EXPENSE"
        }
    }

    fun isEssential(transaction: TransactionEntity, annotation: TransactionAnnotationEntity? = null): Boolean? =
        annotation?.essential ?: when (transaction.category.normalized()) {
            in essentialCategories -> true
            in discretionaryCategories -> false
            else -> null
        }

    fun summary(
        transactions: List<TransactionEntity>,
        finance: FinanceData,
        month: YearMonth,
        zoneId: ZoneId,
        currency: String,
    ): MonthlyFinance {
        val annotations = finance.annotations.associateBy { it.transactionId }
        val confirmed = transactions.filter { transaction ->
            transaction.status.trim().uppercase(Locale.ROOT) in confirmedStatuses &&
                transaction.amountMinor?.let { it > 0 } == true &&
                transaction.currency.equals(currency, ignoreCase = true) &&
                (transaction.direction.normalized() in setOf("DEBIT", "CREDIT") ||
                    annotations[transaction.transactionId]?.flowType?.trim()?.uppercase(Locale.ROOT) in flowTypes)
        }.distinctBy { it.transactionId }
        val selected = confirmed.filter { transaction ->
            YearMonth.from(Instant.ofEpochMilli(transaction.eventTimeEpochMs).atZone(zoneId)) == month
        }
        var income = BigInteger.ZERO
        var expense = BigInteger.ZERO
        var essential = BigInteger.ZERO
        var discretionary = BigInteger.ZERO
        var unclassified = BigInteger.ZERO
        var financing = BigInteger.ZERO
        var transfers = BigInteger.ZERO
        var refunds = BigInteger.ZERO
        var hasUnclassifiedExpense = false
        val categoryTotals = mutableMapOf<String, BigInteger>()
        val merchantTotals = mutableMapOf<String, BigInteger>()
        val paymentTotals = mutableMapOf<String, BigInteger>()
        for (transaction in selected) {
            val amount = BigInteger.valueOf(requireNotNull(transaction.amountMinor))
            val annotation = annotations[transaction.transactionId]
            when (flowType(transaction, annotation)) {
                "INCOME" -> income += amount
                "TRANSFER" -> if (transaction.direction.normalized() != "CREDIT") transfers += amount
                "CARD_PAYMENT" -> if (transaction.direction.normalized() != "CREDIT") financing += amount
                "REFUND" -> {
                    refunds += amount
                    expense -= amount
                    when (isEssential(transaction, annotation)) {
                        true -> essential -= amount
                        false -> discretionary -= amount
                        null -> unclassified -= amount
                    }
                }
                "EXPENSE" -> {
                    expense += amount
                    if (transaction.category.normalized() in debtCategories) {
                        financing += amount
                    } else {
                        when (isEssential(transaction, annotation)) {
                            true -> essential += amount
                            false -> discretionary += amount
                            null -> {
                                unclassified += amount
                                hasUnclassifiedExpense = true
                            }
                        }
                    }
                    categoryTotals.add(transaction.category.ifBlank { "Uncategorized" }, amount)
                    merchantTotals.add(transaction.counterparty?.trim()?.takeIf(String::isNotEmpty) ?: "Unknown payee", amount)
                    paymentTotals.add(transaction.method.ifBlank { "UNKNOWN" }, amount)
                }
            }
        }
        val activeDebts = finance.debts.filter { it.active && it.outstandingMinor > 0 }
        val monthlyEmi = activeDebts.fold(BigInteger.ZERO) { total, debt -> total + debt.emiMinor.nonNegativeBig() }
        val outstanding = activeDebts.fold(BigInteger.ZERO) { total, debt -> total + debt.outstandingMinor.nonNegativeBig() }
        val savings = income - expense
        val cashBalance = cashBalance(confirmed, finance, month, zoneId, annotations)
        val monthlyEssentials = essential.coerceAtLeast(BigInteger.ZERO) + monthlyEmi
        // Unknown spending prevents a deceptively low emergency fund estimate.
        val emergencyTarget = if (hasUnclassifiedExpense || monthlyEssentials == BigInteger.ZERO) null
            else (monthlyEssentials * finance.preferences.emergencyMonths.coerceIn(1, 120).toBigInteger()).clampedLong()
        return MonthlyFinance(
            incomeMinor = income.clampedLong(),
            expenseMinor = expense.clampedLong(),
            savingsMinor = savings.clampedLong(),
            essentialMinor = essential.clampedLong(),
            discretionaryMinor = discretionary.clampedLong(),
            unclassifiedMinor = unclassified.clampedLong(),
            debtPaymentsMinor = financing.clampedLong(),
            transferMinor = transfers.clampedLong(),
            refundMinor = refunds.clampedLong(),
            monthlyEmiMinor = monthlyEmi.clampedLong(),
            outstandingDebtMinor = outstanding.clampedLong(),
            savingsRate = if (income > BigInteger.ZERO) savings.toDouble() / income.toDouble() else null,
            debtToIncome = if (income > BigInteger.ZERO) monthlyEmi.toDouble() / income.toDouble() else null,
            emergencyTargetMinor = emergencyTarget,
            cashBufferMonths = if (cashBalance != null && monthlyEssentials > BigInteger.ZERO && !hasUnclassifiedExpense)
                cashBalance.coerceAtLeast(0L).toDouble() / monthlyEssentials.toDouble() else null,
            cashBalanceMinor = cashBalance,
            categorySpend = categoryTotals.sortedAmounts(),
            merchantSpend = merchantTotals.sortedAmounts(),
            paymentSpend = paymentTotals.sortedAmounts(),
            transactions = selected.sortedByDescending { it.eventTimeEpochMs },
        )
    }

    /**
     * Opening balance is anchored to preferences.openingMonth, never rebased when browsing months.
     * Explicit CREDIT_CARD purchases/refunds/cashback alter debt, not cash. CARD/OTHER/UNKNOWN
     * cannot establish account type, so cash is unavailable until those methods are clarified. Transfer legs
     * are excluded from the combined cash balance. The result still depends on capture completeness.
     */
    private fun cashBalance(
        confirmed: List<TransactionEntity>,
        finance: FinanceData,
        month: YearMonth,
        zoneId: ZoneId,
        annotations: Map<String, TransactionAnnotationEntity>,
    ): Long? {
        val opening = finance.preferences.openingCashMinor ?: return null
        val openingMonth = parseMonth(finance.preferences.openingMonth) ?: return null
        if (month < openingMonth) return null
        var balance = BigInteger.valueOf(opening)
        for (transaction in confirmed) {
            val eventMonth = YearMonth.from(Instant.ofEpochMilli(transaction.eventTimeEpochMs).atZone(zoneId))
            if (eventMonth < openingMonth || eventMonth > month) continue
            val flow = flowType(transaction, annotations[transaction.transactionId])
            val method = transaction.method.normalized()
            if (flow == "TRANSFER") continue
            if (flow != "CARD_PAYMENT" && method in setOf("CARD", "OTHER", "UNKNOWN", "")) return null
            if (flow != "CARD_PAYMENT" && method == "CREDIT CARD") continue
            val amount = BigInteger.valueOf(requireNotNull(transaction.amountMinor))
            when (flow) {
                "INCOME", "REFUND" -> balance += amount
                "EXPENSE" -> balance -= amount
                "CARD_PAYMENT" -> if (transaction.direction.normalized() != "CREDIT") balance -= amount
            }
        }
        return balance.clampedLong()
    }

    /**
     * Twelve-month plan, not a prediction of uncaptured spending. The most recent entered budget
     * carries forward; missing inputs remain unknown. Planned savings are cash set aside and are
     * deducted once here. An entered openingMonth is needed to seed the projected cash balance.
     */
    fun forecast(finance: FinanceData, startMonth: YearMonth): List<CashFlowMonth> {
        val budgets = finance.budgets.mapNotNull { budget -> parseMonth(budget.month)?.let { it to budget } }
            .sortedBy { it.first }
        val openingMonth = parseMonth(finance.preferences.openingMonth)
        val initialMonth = openingMonth?.takeIf { it < startMonth } ?: startMonth
        val monthsToStart = ChronoUnit.MONTHS.between(initialMonth, startMonth)
        // Reject implausibly long history rather than looping over a corrupt imported date.
        val calculationStart = if (monthsToStart <= 1_200) initialMonth else startMonth
        var balance: Long? = null
        val result = mutableListOf<CashFlowMonth>()
        val end = startMonth.plusMonths(12)
        var current = calculationStart
        while (current < end) {
            if (current == openingMonth) balance = finance.preferences.openingCashMinor
            val budget = budgets.lastOrNull { it.first <= current }?.second
            val income = budget?.incomeMinor.validAmount() ?: finance.preferences.monthlyIncomeMinor.validAmount()
            val essential = budget?.essentialMinor.validAmount()
            val discretionary = budget?.discretionaryMinor.validAmount()
            val debt = budget?.debtMinor.validAmount() ?: plannedDebt(finance, current, startMonth)
            val savings = budget?.savingsMinor.validAmount()
            val outflow = completeSum(essential, discretionary, debt, savings)
            val net = if (income != null && outflow != null) {
                (income.toBigInteger() - requireNotNull(essential).toBigInteger() - requireNotNull(discretionary).toBigInteger() -
                    debt.toBigInteger() - requireNotNull(savings).toBigInteger()).clampedLong()
            } else null
            val closing = if (balance != null && net != null) (balance.toBigInteger() + net.toBigInteger()).clampedLong() else null
            val essentialOutflow = completeSum(essential, debt)
            if (current >= startMonth) result += CashFlowMonth(
                current, balance, income, essential, discretionary, debt, savings, net, closing,
                if (closing != null && essentialOutflow != null && essentialOutflow > 0) closing.coerceAtLeast(0L).toDouble() / essentialOutflow else null,
            )
            balance = closing
            current = current.plusMonths(1)
        }
        return result
    }

    private fun plannedDebt(finance: FinanceData, month: YearMonth, forecastStart: YearMonth): Long {
        return finance.debts.filter { debt ->
            debt.active && debt.outstandingMinor > 0 &&
                (parseDate(debt.startDate)?.let { YearMonth.from(it) <= month } != false)
        }.fold(BigInteger.ZERO) { total, debt ->
            total + projectedDebtPayment(debt, month, forecastStart).toBigInteger()
        }.clampedLong()
    }

    private fun projectedDebtPayment(debt: DebtEntity, month: YearMonth, forecastStart: YearMonth): Long {
        val payment = debt.emiMinor.nonNegativeBig() + debt.extraPaymentMinor.nonNegativeBig()
        if (!debt.annualInterestPercent.isFinite() || debt.annualInterestPercent !in 0.0..1_000.0) return payment.clampedLong()
        val firstPaymentMonth = maxOf(forecastStart, parseDate(debt.startDate)?.let(YearMonth::from) ?: forecastStart)
        val offset = ChronoUnit.MONTHS.between(firstPaymentMonth, month).coerceAtLeast(0)
        if (offset > 1_200) return payment.clampedLong()
        val monthlyRate = BigDecimal.valueOf(debt.annualInterestPercent).divide(BigDecimal(1_200), 18, RoundingMode.HALF_UP)
        var balance = debt.outstandingMinor.toBigDecimal()
        for (index in 0..offset) {
            if (balance <= BigDecimal.ZERO) return 0
            val interest = (balance * monthlyRate).setScale(0, RoundingMode.HALF_UP)
            val due = minOf(payment.toBigDecimal(), balance + interest)
            if (index == offset) return due.toBigInteger().clampedLong()
            balance += interest - due
        }
        return 0
    }

    /** Fixed rate, monthly interest, no fees. Null means payoff cannot be established safely. */
    fun payoff(debt: DebtEntity): DebtPayoff {
        if (debt.outstandingMinor <= 0) return DebtPayoff(0, 0, false)
        if (!debt.annualInterestPercent.isFinite() || debt.annualInterestPercent !in 0.0..1_000.0 ||
            debt.emiMinor < 0 || debt.extraPaymentMinor < 0) return DebtPayoff(null, null, false)
        val payment = debt.emiMinor.toBigDecimal() + debt.extraPaymentMinor.toBigDecimal()
        if (payment <= BigDecimal.ZERO) return DebtPayoff(null, null, true)
        val rate = BigDecimal.valueOf(debt.annualInterestPercent).divide(BigDecimal(1_200), 18, RoundingMode.HALF_UP)
        var balance = debt.outstandingMinor.toBigDecimal()
        var totalInterest = BigDecimal.ZERO
        for (months in 1..1_200) {
            val interest = balance.multiply(rate).setScale(0, RoundingMode.HALF_UP)
            if (payment <= interest) return DebtPayoff(null, null, true)
            totalInterest += interest
            balance += interest - payment
            if (balance <= BigDecimal.ZERO) return DebtPayoff(months, totalInterest.toBigInteger().clampedLong(), false)
        }
        return DebtPayoff(null, null, false)
    }

    /** Due-day clamps to the month's last day, including February and leap years. */
    fun nextDue(debt: DebtEntity, today: LocalDate): LocalDate? {
        if (!debt.active || debt.outstandingMinor <= 0 || debt.dueDay !in 1..31) return null
        val start = if (debt.startDate.isBlank()) today else parseDate(debt.startDate) ?: return null
        val earliest = maxOf(today, start)
        var month = YearMonth.from(earliest)
        var due = month.atDay(minOf(debt.dueDay, month.lengthOfMonth()))
        if (due < earliest) {
            month = month.plusMonths(1)
            due = month.atDay(minOf(debt.dueDay, month.lengthOfMonth()))
        }
        return due
    }

    /** Equal monthly contributions, including this month. Overdue goals require the remainder now. */
    fun goalMonthlyRequired(goal: GoalEntity, today: LocalDate): Long {
        val remainder = (goal.targetMinor.nonNegativeBig() - goal.currentMinor.nonNegativeBig()).coerceAtLeast(BigInteger.ZERO)
        val target = parseDate(goal.targetDate) ?: return remainder.clampedLong()
        val months = (ChronoUnit.MONTHS.between(YearMonth.from(today), YearMonth.from(target)) + 1).coerceAtLeast(1)
        val divisor = months.toBigInteger()
        return ((remainder + divisor - BigInteger.ONE) / divisor).clampedLong()
    }

    private fun String?.normalized(): String = this?.trim()?.uppercase(Locale.ROOT)
        ?.replace('_', ' ')?.replace('-', ' ')?.replace(whitespace, " ").orEmpty()
    private fun Long.nonNegativeBig(): BigInteger = coerceAtLeast(0L).toBigInteger()
    private fun Long?.validAmount(): Long? = this?.takeIf { it >= 0 }
    private fun parseMonth(value: String): YearMonth? = runCatching { YearMonth.parse(value) }.getOrNull()
    private fun parseDate(value: String): LocalDate? = runCatching { LocalDate.parse(value) }.getOrNull()
    private fun BigInteger.clampedLong(): Long = coerceIn(LONG_MIN, LONG_MAX).toLong()
    private fun MutableMap<String, BigInteger>.add(key: String, amount: BigInteger) { this[key] = (this[key] ?: BigInteger.ZERO) + amount }
    private fun Map<String, BigInteger>.sortedAmounts(): Map<String, Long> = entries.sortedByDescending { it.value }
        .associate { it.key to it.value.clampedLong() }
    private fun completeSum(vararg amounts: Long?): Long? = if (amounts.any { it == null }) null
        else amounts.fold(BigInteger.ZERO) { total, amount -> total + requireNotNull(amount).toBigInteger() }.clampedLong()
    private val LONG_MIN = BigInteger.valueOf(Long.MIN_VALUE)
    private val LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE)
}
