package app.txnsheet.personal.data.repository

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalWorkbookReaderTest {
    @Test
    fun `supported tables import transaction annotation budget debt and goal fields`() {
        val preview = read(linkedMapOf(
            "Transactions" to transactionRow(4, "2026-09-08", "Expense", "Food", "Sample Grocer", "123.45", essential = "Yes"),
            "Monthly Budget" to row(4,
                text("A4", "2026-09-01"), number("B4", "50000"), number("C4", "20000"),
                number("D4", "5000"), number("E4", "10000"), number("F4", "15000")),
            "EMI Tracker" to debtRow(4, "12.5"),
            "Goals" to row(4, text("A4", "Sample Goal"), number("B4", "10000"), number("C4", "2500"),
                text("D4", "2027-09-08"), text("I4", "High")),
        ))
        val transaction = preview.transactions.single()
        assertEquals(12_345L, transaction.amountMinor)
        assertEquals("DEBIT", transaction.direction)
        assertEquals("UPI", transaction.method)
        assertEquals("Sample Grocer", transaction.counterparty)
        assertEquals("Food", transaction.category)
        assertEquals(true, preview.annotations.single().essential)
        assertEquals("2026-09", preview.budgets.single().month)
        assertEquals(5_000_000L, preview.budgets.single().incomeMinor)
        assertEquals(12.5, preview.debts.single().annualInterestPercent, 0.0001)
        assertEquals(1_000_000L, preview.goals.single().targetMinor)
        assertEquals(250_000L, preview.goals.single().currentMinor)
        assertEquals(listOf("Transactions", "Monthly Budget", "EMI Tracker", "Goals"), preview.sheetNames)
        assertEquals(0, preview.skippedRows)
    }

    @Test
    fun `Excel 1900 and 1904 date systems produce the same local transaction day`() {
        val day = LocalDate.of(2026, 9, 8)
        for (date1904 in listOf(false, true)) {
            val base = if (date1904) LocalDate.of(1904, 1, 1) else LocalDate.of(1899, 12, 30)
            val serial = ChronoUnit.DAYS.between(base, day).toString()
            val contents = row(4, number("A4", serial), text("C4", "Income"), text("D4", "Salary"),
                text("E4", "Sample Employer"), text("F4", "Bank Transfer"), number("G4", "1000"))
            val transaction = read(linkedMapOf("Transactions" to contents), date1904 = date1904).transactions.single()
            assertEquals(day.atStartOfDay(ZoneId.of("Asia/Kolkata")).toInstant().toEpochMilli(), transaction.eventTimeEpochMs)
            assertEquals("CREDIT", transaction.direction)
        }
    }

    @Test
    fun `import IDs survive row shifts and reorder while retaining identical transaction occurrences`() {
        fun purchase(index: Int, merchant: String = "Sample Grocer") =
            transactionRow(index, "2026-09-08", "Expense", "Food", merchant, "100")
        val first = read(linkedMapOf("Transactions" to (purchase(4) + purchase(5, "Sample Cafe") + purchase(6))))
        val moved = read(linkedMapOf("Transactions" to (purchase(10, "Sample Cafe") + purchase(14) + purchase(18))))
        assertEquals(3, first.transactions.size)
        assertEquals(3, first.transactions.map { it.transactionId }.distinct().size)
        assertEquals(first.transactions.map { it.transactionId }.toSet(), moved.transactions.map { it.transactionId }.toSet())
        assertNotEquals(first.transactions[0].transactionId, first.transactions[2].transactionId)
    }

    @Test
    fun `APR percent formats convert fractional storage and ordinary numeric APR is unchanged`() {
        val debts = read(linkedMapOf("EMI Tracker" to (
            debtRow(4, "0.125", style = 1) + debtRow(5, "12.5") + debtRow(6, "0.125", style = 2)
        )), styles = PERCENT_STYLES).debts
        assertEquals(3, debts.size)
        debts.forEach { assertEquals(12.5, it.annualInterestPercent, 0.0001) }
    }

    @Test
    fun `formulas without cached values never fabricate a completed transaction`() {
        val contents = row(4, text("A4", "2026-09-08"), text("C4", "Expense"), text("E4", "Sample Shop"),
            formula("G4", "SUM(G8:G9)")) +
            row(5, formula("A5", "TODAY()"), text("C5", "Expense"), number("G5", "100"))
        val preview = read(linkedMapOf("Transactions" to contents))
        assertTrue(preview.transactions.isEmpty())
        assertTrue(preview.skippedRows >= 1)
    }

    @Test
    fun `zero budget inputs remain zero and absent inputs remain unknown`() {
        val preview = read(linkedMapOf("Monthly Budget" to row(4,
            text("A4", "2026-09-01"), number("B4", "0"), number("D4", "0"), formula("E4", "SUM(A1:A2)"))))
        val budget = preview.budgets.single()
        assertEquals(0L, budget.incomeMinor)
        assertNull(budget.essentialMinor)
        assertEquals(0L, budget.discretionaryMinor)
        assertNull(budget.debtMinor)
        assertNull(budget.savingsMinor)
    }

    @Test
    fun `shared string and escaped text survive import as literal workbook data`() {
        val contents = row(4, text("A4", "2026-09-08"), text("C4", "Expense"),
            "<c r=\"E4\" t=\"s\"><v>0</v></c>", number("G4", "100"), text("J4", "Note \"quoted\"\nnext line"))
        val preview = read(linkedMapOf("Transactions" to contents), sharedStrings =
            "<sst xmlns=\"$SHEET_NS\"><si><t>Sample &amp; Co</t></si></sst>")
        assertEquals("Sample & Co", preview.transactions.single().counterparty)
        assertEquals("Note \"quoted\"\nnext line", preview.transactions.single().notes)
        assertTrue(preview.snapshot.sheetsJson.contains("Sample & Co"))
        assertFalse(preview.snapshot.sheetsJson.contains("Note \"quoted\"\nnext line"))
    }

    @Test
    fun `XML entity declarations are rejected in UTF8 and UTF16 before any external access`() {
        for (encoding in listOf(Charsets.UTF_8, Charsets.UTF_16)) {
            val document = "<?xml version=\"1.0\" encoding=\"${encoding.name()}\"?>" +
                "<!DOCTYPE workbook [<!ENTITY external SYSTEM \"file:///txnsheet-test-must-not-be-read\">" +
                "<!ENTITY local \"untrusted\">]>" + workbookXml(listOf("Transactions"))
                    .replace("</workbook>", "<definedNames><definedName name=\"test\">&local;</definedName></definedNames></workbook>")
            val bytes = fixture(linkedMapOf("Transactions" to transactionRow(4, "2026-09-08", "Expense", "Food", "Sample Shop", "100")),
                workbookOverride = document.toByteArray(encoding))
            assertThrows(Exception::class.java) { LocalWorkbookReader.read(ByteArrayInputStream(bytes)) }
        }
    }

    @Test
    fun `zip entry bound stops oversized package structure`() {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            repeat(1_001) { index ->
                zip.putNextEntry(ZipEntry("extra/entry-$index"))
                zip.closeEntry()
            }
        }
        val error = assertThrows(IllegalArgumentException::class.java) {
            LocalWorkbookReader.read(ByteArrayInputStream(output.toByteArray()))
        }
        assertTrue(error.message.orEmpty().contains("too many entries", ignoreCase = true))
    }

    private fun read(
        sheets: LinkedHashMap<String, String>,
        date1904: Boolean = false,
        styles: String? = null,
        sharedStrings: String? = null,
    ) = LocalWorkbookReader.read(ByteArrayInputStream(fixture(sheets, date1904, styles, sharedStrings)))

    private fun fixture(
        sheets: LinkedHashMap<String, String>,
        date1904: Boolean = false,
        styles: String? = null,
        sharedStrings: String? = null,
        workbookOverride: ByteArray? = null,
    ): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            fun entry(path: String, bytes: ByteArray) {
                zip.putNextEntry(ZipEntry(path))
                zip.write(bytes)
                zip.closeEntry()
            }
            entry("xl/workbook.xml", workbookOverride ?: workbookXml(sheets.keys.toList(), date1904).toByteArray())
            val relations = sheets.keys.mapIndexed { index, _ ->
                "<Relationship Id=\"rId${index + 1}\" Target=\"worksheets/sheet${index + 1}.xml\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\"/>"
            }.joinToString("")
            entry("xl/_rels/workbook.xml.rels", "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">$relations</Relationships>".toByteArray())
            sheets.values.forEachIndexed { index, rows ->
                entry("xl/worksheets/sheet${index + 1}.xml", "<worksheet xmlns=\"$SHEET_NS\"><sheetData>$rows</sheetData></worksheet>".toByteArray())
            }
            styles?.let { entry("xl/styles.xml", it.toByteArray()) }
            sharedStrings?.let { entry("xl/sharedStrings.xml", it.toByteArray()) }
        }
        return output.toByteArray()
    }

    private fun workbookXml(names: List<String>, date1904: Boolean = false): String {
        val sheets = names.mapIndexed { index, name ->
            "<sheet name=\"${escape(name)}\" sheetId=\"${index + 1}\" r:id=\"rId${index + 1}\"/>"
        }.joinToString("")
        return "<workbook xmlns=\"$SHEET_NS\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><workbookPr date1904=\"${if (date1904) 1 else 0}\"/><sheets>$sheets</sheets></workbook>"
    }

    private fun transactionRow(index: Int, date: String, type: String, category: String, merchant: String, amount: String, essential: String? = null) =
        row(index, text("A$index", date), text("C$index", type), text("D$index", category), text("E$index", merchant),
            text("F$index", "UPI"), number("G$index", amount), essential?.let { text("H$index", it) }.orEmpty(), text("I$index", "Sample Bank"))

    private fun debtRow(index: Int, rate: String, style: Int? = null) = row(index,
        text("A$index", "sample-loan-$index"), text("B$index", "Sample Loan $index"), text("C$index", "Personal Loan"),
        number("D$index", "100000"), number("E$index", "75000"), number("F$index", rate, style),
        number("G$index", "24"), number("H$index", "6"), number("J$index", "5000"), number("K$index", "10"),
        text("L$index", "2026-03-10"), text("O$index", "High"), text("P$index", "Active"))

    private fun row(index: Int, vararg cells: String) = "<row r=\"$index\">${cells.joinToString("")}</row>"
    private fun text(ref: String, value: String) = "<c r=\"$ref\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${escape(value)}</t></is></c>"
    private fun number(ref: String, value: String, style: Int? = null) = "<c r=\"$ref\"${style?.let { " s=\"$it\"" }.orEmpty()}><v>$value</v></c>"
    private fun formula(ref: String, value: String) = "<c r=\"$ref\"><f>${escape(value)}</f></c>"
    private fun escape(value: String) = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private companion object {
        const val SHEET_NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
        const val PERCENT_STYLES = "<styleSheet xmlns=\"$SHEET_NS\"><numFmts count=\"1\"><numFmt numFmtId=\"164\" formatCode=\"0.00%\"/></numFmts><cellXfs count=\"3\"><xf numFmtId=\"0\"/><xf numFmtId=\"9\"/><xf numFmtId=\"164\"/></cellXfs></styleSheet>"
    }
}
