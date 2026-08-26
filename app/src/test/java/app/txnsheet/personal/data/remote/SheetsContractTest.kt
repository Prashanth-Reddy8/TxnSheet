package app.txnsheet.personal.data.remote

import app.txnsheet.personal.data.local.TransactionEntity
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SheetsContractTest {
    @Test
    fun `row mapper preserves the exact A through R schema`() {
        val transaction = completeTransaction()

        val row = TransactionSheetRowMapper.toRow(transaction, "Asia/Kolkata")

        assertEquals(TransactionSheetContract.HEADERS.size, row.size)
        assertEquals(transaction.transactionId, row[0])
        assertEquals(BigDecimal("1249.00"), row[3])
        assertEquals("INR", row[4])
        assertEquals("DEBIT", row[5])
        assertEquals("=HYPERLINK", row[7])
        assertEquals(BigDecimal("5000.00"), row[12])
        assertEquals(BigDecimal("0.94"), row[15])
        assertEquals("upi_generic@1.0.0", row[16])
    }

    @Test
    fun `date serial uses workbook timezone wall clock`() {
        val epoch = Instant.parse("2026-08-19T18:30:00Z").toEpochMilli()

        val kolkata = GoogleDateSerial.fromEpochMillis(epoch, ZoneId.of("Asia/Kolkata"))
        val utc = GoogleDateSerial.fromEpochMillis(epoch, ZoneId.of("UTC"))

        assertEquals(5.5 / 24.0, kolkata - utc, 0.0000001)
    }

    @Test
    fun `incomplete review record cannot be emitted to Sheets`() {
        val incomplete = completeTransaction().copy(amountMinor = null, direction = null)

        assertThrows(IllegalArgumentException::class.java) {
            TransactionSheetRowMapper.toRow(incomplete, "Asia/Kolkata")
        }
    }

    @Test
    fun `invalid UUID cannot reach the remote ledger`() {
        assertThrows(IllegalArgumentException::class.java) {
            TransactionSheetRowMapper.toRow(
                completeTransaction().copy(transactionId = "not-a-uuid"),
                "Asia/Kolkata",
            )
        }
    }

    private fun completeTransaction() = TransactionEntity(
        transactionId = UUID.fromString("da355564-455b-4e0d-8e7d-92c34ce74680").toString(),
        eventTimeEpochMs = Instant.parse("2026-08-19T08:00:00Z").toEpochMilli(),
        capturedTimeEpochMs = Instant.parse("2026-08-19T08:00:02Z").toEpochMilli(),
        amountMinor = 124_900L,
        currency = "INR",
        direction = "DEBIT",
        method = "UPI",
        counterparty = "=HYPERLINK",
        category = "Shopping",
        institution = "Example Bank",
        accountLast4 = "1234",
        referenceId = "UTR123456789",
        balanceMinor = 500_000L,
        sourceApp = "com.example.bank",
        sourceLabel = "Example Bank",
        confidence = 0.94,
        parserRule = "upi_generic@1.0.0",
        notes = "",
        eventFingerprint = "f".repeat(64),
        status = "QUEUED",
    )
}
