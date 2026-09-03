package app.txnsheet.personal.ui

import app.txnsheet.personal.data.local.TransactionEntity
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale

private val IndiaLocale = Locale("en", "IN")
private val DayFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", IndiaLocale)
private val ShortTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", IndiaLocale)
private val FullDateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", IndiaLocale)

fun formatMoney(
    amountMinor: Long,
    currencyCode: String = "INR",
    direction: String? = null,
): String {
    val formatter = NumberFormat.getCurrencyInstance(IndiaLocale).apply {
        runCatching { currency = Currency.getInstance(currencyCode) }
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }
    val amount = BigDecimal.valueOf(amountMinor).movePointLeft(2).setScale(2, RoundingMode.UNNECESSARY)
    val sign = when (direction) {
        "DEBIT" -> "−"
        "CREDIT" -> "+"
        else -> ""
    }
    return sign + formatter.format(amount)
}

fun formatDay(epochMs: Long, zoneId: String = "Asia/Kolkata"): String =
    Instant.ofEpochMilli(epochMs).atZone(ZoneId.of(zoneId)).format(DayFormatter)

fun formatTime(epochMs: Long, zoneId: String = "Asia/Kolkata"): String =
    Instant.ofEpochMilli(epochMs).atZone(ZoneId.of(zoneId)).format(ShortTimeFormatter)

fun formatDateTime(epochMs: Long, zoneId: String = "Asia/Kolkata"): String =
    Instant.ofEpochMilli(epochMs).atZone(ZoneId.of(zoneId)).format(FullDateTimeFormatter)

fun statusLabel(status: String): String = when (status.uppercase(Locale.ROOT)) {
    "LOCAL", "SYNCED", "QUEUED", "RETRY", "SYNCING" -> "Saved on phone"
    "REVIEW" -> "Check details"
    "AUTH_REQUIRED", "SHEET_REQUIRED", "SCHEMA_ERROR", "FAILED" -> "Saved on phone"
    "IGNORED" -> "Ignored"
    "PARSED", "CAPTURED" -> "Saved on phone"
    else -> status.lowercase(Locale.ROOT).replaceFirstChar(Char::titlecase)
}

fun transactionA11yLabel(transaction: TransactionEntity, zoneId: String): String = buildString {
    append(
        when (transaction.direction) {
            "DEBIT" -> "Debit"
            "CREDIT" -> "Credit"
            else -> "Transaction"
        },
    )
    append(", ")
    append(
        transaction.amountMinor?.let { formatMoney(it, transaction.currency).replace("₹", "rupees ") }
            ?: "amount needs review",
    )
    transaction.counterparty?.takeIf(String::isNotBlank)?.let {
        append(", ")
        append(it)
    }
    append(", ")
    append(transaction.category)
    append(", ")
    append(statusLabel(transaction.status))
    append(", ")
    append(formatDateTime(transaction.eventTimeEpochMs, zoneId))
}

fun redactSourceText(value: String): String = value
    .replace(Regex("(?i)\\b(?:otp|one[ -]?time password|verification code)\\b[^.\\n]*"), "[security text hidden]")
    .replace(Regex("\\d{5,}")) { match -> "••••${match.value.takeLast(4)}" }
    .take(1_200)
