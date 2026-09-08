package app.txnsheet.personal.domain

import app.txnsheet.personal.data.local.BudgetEntity
import app.txnsheet.personal.data.local.DebtEntity
import app.txnsheet.personal.data.local.FinanceData
import app.txnsheet.personal.data.local.FinancePreferencesEntity
import app.txnsheet.personal.data.local.GoalEntity
import app.txnsheet.personal.data.local.TransactionAnnotationEntity
import app.txnsheet.personal.data.local.TransactionEntity
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FinanceCalculatorTest {
    private val month = YearMonth.of(2026, 9)
    private val zone = ZoneId.of("Asia/Kolkata")

    @Test
    fun `income expense and cash keep transfer legs refunds and card settlements distinct`() {
        val transactions = listOf(
            transaction("salary", 100_000, "CREDIT", "Salary"),
            transaction("food", 10_000, category = "Groceries"),
            transaction("shop", 5_000, category = "Shopping", method = "CREDIT_CARD"),
            transaction("emi", 8_000, category = "EMI"),
            transaction("card-debit", 5_000, category = "Uncategorized"),
            transaction("card-credit", 5_000, "CREDIT", "Credit Card Payment"),
            transaction("refund", 1_000, "CREDIT", "Shopping", method = "CREDIT_CARD"),
            transaction("own-debit", 10_000, category = "Self Transfer"),
            transaction("own-credit", 10_000, "CREDIT", "Self Transfer"),
        )
        val data = FinanceData(
            preferences = FinancePreferencesEntity(openingCashMinor = 100_000, openingMonth = "2026-09"),
            debts = listOf(debt(emi = 8_000)),
            annotations = listOf(
                TransactionAnnotationEntity("card-debit", flowType = "CARD_PAYMENT"),
                TransactionAnnotationEntity("refund", flowType = "REFUND"),
            ),
        )
        val result = summary(transactions, data)
        assertEquals(100_000L, result.incomeMinor)
        assertEquals(22_000L, result.expenseMinor)
        assertEquals(78_000L, result.savingsMinor)
        assertEquals(10_000L, result.essentialMinor)
        assertEquals(4_000L, result.discretionaryMinor)
        assertEquals(0L, result.unclassifiedMinor)
        assertEquals(13_000L, result.debtPaymentsMinor)
        assertEquals(10_000L, result.transferMinor)
        assertEquals(1_000L, result.refundMinor)
        assertEquals(177_000L, result.cashBalanceMinor)
        assertEquals(0.78, requireNotNull(result.savingsRate), 0.00001)
        assertEquals(108_000L, result.emergencyTargetMinor)
        assertEquals(23_000L, result.categorySpend.values.sum())
    }

    @Test
    fun `only confirmed positive values in selected local calendar month and currency count`() {
        val result = summary(listOf(
            transaction("local-midnight", 100, timestamp = "2026-08-31T18:30:00Z"),
            transaction("before", 200, timestamp = "2026-08-31T18:29:59Z"),
            transaction("october", 300, timestamp = "2026-09-30T18:30:00Z"),
            transaction("review", 400, status = "REVIEW"),
            transaction("ignored", 500, status = "IGNORED"),
            transaction("failed", 600, status = "FAILED"),
            transaction("negative", -700),
            transaction("zero", 0),
            transaction("foreign", 800, currency = "USD"),
            transaction("legacy-confirmed", 900, status = "AUTH_REQUIRED"),
            transaction("missing-direction", 1_000).copy(direction = null),
        ))
        assertEquals(1_000L, result.expenseMinor)
        assertEquals(setOf("local-midnight", "legacy-confirmed"), result.transactions.map { it.transactionId }.toSet())
    }

    @Test
    fun `unknown essential classification remains explicit and prevents misleading emergency target`() {
        val transaction = transaction("uncertain", 100, category = "Other").copy(counterparty = "Supermarket maybe")
        val result = summary(listOf(transaction))
        assertEquals(100L, result.unclassifiedMinor)
        assertEquals(0L, result.essentialMinor)
        assertNull(result.emergencyTargetMinor)
        assertNull(result.cashBufferMonths)
        val classified = summary(listOf(transaction), FinanceData(annotations = listOf(TransactionAnnotationEntity("uncertain", essential = true))))
        assertEquals(100L, classified.essentialMinor)
        assertEquals(600L, classified.emergencyTargetMinor)
    }

    @Test
    fun `cash needs both opening amount and date and is not inferred from notification balance`() {
        val row = transaction("row", 100).copy(balanceMinor = 99_999)
        assertNull(summary(listOf(row)).cashBalanceMinor)
        assertNull(summary(listOf(row), FinanceData(preferences = FinancePreferencesEntity(openingCashMinor = 1_000))).cashBalanceMinor)
        assertNull(summary(listOf(row), FinanceData(preferences = FinancePreferencesEntity(openingCashMinor = 1_000, openingMonth = "invalid"))).cashBalanceMinor)
    }

    @Test
    fun `cash carries captured activity from opening month and never rebases while browsing`() {
        val data = FinanceData(preferences = FinancePreferencesEntity(openingCashMinor = 1_000, openingMonth = "2026-08"))
        val result = summary(listOf(
            transaction("aug", 200, timestamp = "2026-08-15T12:00:00Z"),
            transaction("sep", 100),
            transaction("future", 500, timestamp = "2026-10-01T12:00:00Z"),
        ), data)
        assertEquals(700L, result.cashBalanceMinor)
        assertEquals(100L, result.expenseMinor)
        assertNull(FinanceCalculator.summary(emptyList(), data, YearMonth.of(2026, 7), zone, "INR").cashBalanceMinor)
    }

    @Test
    fun `ambiguous card method does not present guessed cash balance`() {
        val data = FinanceData(preferences = FinancePreferencesEntity(openingCashMinor = 1_000, openingMonth = "2026-09"))
        assertNull(summary(listOf(transaction("card", 100, method = "CARD")), data).cashBalanceMinor)
        assertNull(summary(listOf(transaction("other", 100, method = "OTHER")), data).cashBalanceMinor)
        assertNull(summary(listOf(transaction("unknown", 100, "CREDIT", method = "UNKNOWN")), data).cashBalanceMinor)
        assertEquals(900L, summary(listOf(transaction("debit-card", 100, method = "DEBIT_CARD")), data).cashBalanceMinor)
    }

    @Test
    fun `ATM withdrawal moves existing money and later cash spending counts once`() {
        val data = FinanceData(preferences = FinancePreferencesEntity(openingCashMinor = 1_000, openingMonth = "2026-09"))
        val withdrawal = transaction("atm", 500, method = "ATM")
        val result = summary(listOf(withdrawal, transaction("cash-spend", 100, method = "CASH")), data)
        assertEquals(500L, result.transferMinor)
        assertEquals(100L, result.expenseMinor)
        assertEquals(900L, result.cashBalanceMinor)
        assertEquals("EXPENSE", FinanceCalculator.flowType(withdrawal, TransactionAnnotationEntity("atm", flowType = "EXPENSE")))
    }

    @Test
    fun `credit card cashback is income without increasing aggregate cash`() {
        val data = FinanceData(preferences = FinancePreferencesEntity(openingCashMinor = 1_000, openingMonth = "2026-09"))
        val result = summary(listOf(transaction("cashback", 100, "CREDIT", "Rewards", "CREDIT_CARD")), data)
        assertEquals(100L, result.incomeMinor)
        assertEquals(1_000L, result.cashBalanceMinor)
    }

    @Test
    fun `ratios remain unavailable when there is no income`() {
        val result = summary(listOf(transaction("expense", 100)))
        assertNull(result.savingsRate)
        assertNull(result.debtToIncome)
        assertEquals(-100L, result.savingsMinor)
    }

    @Test
    fun `refund larger than current spending is shown as negative net expenses not extra income`() {
        val result = summary(listOf(transaction("refund", 300, "CREDIT", "Refund"), transaction("spend", 100)))
        assertEquals(0L, result.incomeMinor)
        assertEquals(-200L, result.expenseMinor)
        assertEquals(200L, result.savingsMinor)
        assertNull(result.savingsRate)
    }

    @Test
    fun `forecast keeps unknown inputs unknown`() {
        val result = FinanceCalculator.forecast(FinanceData(preferences = FinancePreferencesEntity(monthlyIncomeMinor = 100_000)), month)
        assertEquals(12, result.size)
        assertEquals(100_000L, result.first().incomeMinor)
        assertNull(result.first().essentialMinor)
        assertNull(result.first().netMinor)
        assertNull(result.last().closingMinor)
    }

    @Test
    fun `forecast carries budget forward deducts planned savings once and anchors cash month`() {
        val data = FinanceData(
            preferences = FinancePreferencesEntity(openingCashMinor = 500, openingMonth = "2026-09"),
            budgets = listOf(BudgetEntity("2026-09", 100, 20, 10, 10, 10)),
        )
        val result = FinanceCalculator.forecast(data, month.plusMonths(1))
        assertEquals(550L, result.first().openingMinor)
        assertEquals(50L, result.first().netMinor)
        assertEquals(600L, result.first().closingMinor)
        assertEquals(1_150L, result.last().closingMinor)
        assertEquals(month.plusMonths(12), result.last().month)
    }

    @Test
    fun `forecast cannot invent an opening balance before its declared month`() {
        val data = FinanceData(
            preferences = FinancePreferencesEntity(openingCashMinor = 500, openingMonth = "2026-10"),
            budgets = listOf(BudgetEntity("2026-09", 100, 20, 10, 10, 10)),
        )
        val result = FinanceCalculator.forecast(data, month)
        assertNull(result.first().openingMinor)
        assertNull(result.first().closingMinor)
        assertEquals(500L, result[1].openingMinor)
        assertEquals(550L, result[1].closingMinor)
    }

    @Test
    fun `forecast ends derived debt payments after principal is paid and handles final partial payment`() {
        val data = FinanceData(
            budgets = listOf(BudgetEntity("2026-09", 1_000, 0, 0, null, 0)),
            debts = listOf(debt(outstanding = 250, emi = 100, rate = 0.0)),
        )
        val result = FinanceCalculator.forecast(data, month)
        assertEquals(listOf(100L, 100L, 50L, 0L), result.take(4).map { it.debtMinor })
        assertEquals(1_000L, result.last().netMinor)
    }

    @Test
    fun `zero interest payoff includes partial final payment and extra payments`() {
        assertEquals(DebtPayoff(3, 0, false), FinanceCalculator.payoff(debt(outstanding = 250, emi = 100, rate = 0.0)))
        assertEquals(DebtPayoff(2, 0, false), FinanceCalculator.payoff(debt(outstanding = 250, emi = 100, rate = 0.0).copy(extraPaymentMinor = 25)))
    }

    @Test
    fun `payoff accrues monthly interest and rejects non amortizing or invalid plans`() {
        assertEquals(DebtPayoff(2, 101, false), FinanceCalculator.payoff(debt(outstanding = 10_000, emi = 10_000, rate = 12.0)))
        assertTrue(FinanceCalculator.payoff(debt(outstanding = 10_000, emi = 100, rate = 12.0)).nonAmortizing)
        assertTrue(FinanceCalculator.payoff(debt(emi = 0)).nonAmortizing)
        assertNull(FinanceCalculator.payoff(debt(rate = Double.NaN)).months)
        assertEquals(DebtPayoff(0, 0, false), FinanceCalculator.payoff(debt(outstanding = 0)))
    }

    @Test
    fun `due date handles month end leap year future start and inactive debts`() {
        assertEquals(LocalDate.of(2028, 2, 29), FinanceCalculator.nextDue(debt().copy(dueDay = 31), LocalDate.of(2028, 2, 1)))
        assertEquals(LocalDate.of(2026, 2, 28), FinanceCalculator.nextDue(debt().copy(dueDay = 31, startDate = "2025-01-01"), LocalDate.of(2026, 2, 28)))
        assertEquals(LocalDate.of(2026, 4, 5), FinanceCalculator.nextDue(debt().copy(dueDay = 5, startDate = "2026-03-15"), LocalDate.of(2026, 2, 1)))
        assertNull(FinanceCalculator.nextDue(debt().copy(active = false), LocalDate.now()))
        assertNull(FinanceCalculator.nextDue(debt().copy(dueDay = 32), LocalDate.now()))
    }

    @Test
    fun `goal monthly allocation rounds upward and handles overdue or achieved goals`() {
        val goal = GoalEntity("goal", "Emergency fund", 10_000, 0, "2026-11-30")
        assertEquals(3_334L, FinanceCalculator.goalMonthlyRequired(goal, LocalDate.of(2026, 9, 8)))
        assertEquals(10_000L, FinanceCalculator.goalMonthlyRequired(goal.copy(targetDate = "2026-08-01"), LocalDate.of(2026, 9, 8)))
        assertEquals(0L, FinanceCalculator.goalMonthlyRequired(goal.copy(currentMinor = 15_000), LocalDate.of(2026, 9, 8)))
    }

    @Test
    fun `aggregations do not wrap into negative balances on large inputs`() {
        val result = summary(listOf(transaction("one", Long.MAX_VALUE, "CREDIT"), transaction("two", 1, "CREDIT")))
        assertEquals(Long.MAX_VALUE, result.incomeMinor)
        assertEquals(Long.MAX_VALUE, result.savingsMinor)
        assertFalse(result.incomeMinor < 0)
    }

    private fun summary(rows: List<TransactionEntity>, data: FinanceData = FinanceData()) =
        FinanceCalculator.summary(rows, data, month, zone, "INR")

    private fun transaction(
        id: String,
        amount: Long,
        direction: String = "DEBIT",
        category: String = "Uncategorized",
        method: String = "UPI",
        timestamp: String = "2026-09-08T10:00:00Z",
        status: String = "LOCAL",
        currency: String = "INR",
    ): TransactionEntity = TransactionEntity(
        transactionId = id,
        eventTimeEpochMs = Instant.parse(timestamp).toEpochMilli(),
        capturedTimeEpochMs = Instant.parse(timestamp).toEpochMilli(),
        amountMinor = amount,
        currency = currency,
        direction = direction,
        method = method,
        counterparty = "Payee",
        category = category,
        institution = null,
        accountLast4 = null,
        referenceId = null,
        balanceMinor = null,
        sourceApp = "test",
        sourceLabel = "Test",
        confidence = 1.0,
        parserRule = "test",
        notes = "",
        eventFingerprint = id,
        status = status,
    )

    private fun debt(outstanding: Long = 100_000, emi: Long = 1_000, rate: Double = 12.0) = DebtEntity(
        id = "debt", name = "Loan", originalMinor = 100_000, outstandingMinor = outstanding,
        annualInterestPercent = rate, emiMinor = emi, tenureMonths = 120, emisPaid = 0,
        dueDay = 5, startDate = "2026-01-01",
    )
}
