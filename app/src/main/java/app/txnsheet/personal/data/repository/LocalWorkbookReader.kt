package app.txnsheet.personal.data.repository

import app.txnsheet.personal.data.local.*
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

data class WorkbookPreview(
    val snapshot: WorkbookSnapshotEntity,
    val transactions: List<TransactionEntity>,
    val annotations: List<TransactionAnnotationEntity>,
    val budgets: List<BudgetEntity>,
    val debts: List<DebtEntity>,
    val goals: List<GoalEntity>,
    val sheetNames: List<String>,
    val skippedRows: Int,
)

/** Reads the owner's selected XLSX without a network or storage permission. Never evaluates formulas. */
object LocalWorkbookReader {
    fun read(input: InputStream): WorkbookPreview {
        val files = mutableMapOf<String, ByteArray>()
        var total = 0
        var entries = 0
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                require(++entries <= 1_000) { "Workbook contains too many entries" }
                if (entry.isDirectory) continue
                val bytes = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = zip.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= 20 * 1024 * 1024) { "Workbook exceeds 20 MB unpacked" }
                    if (entry.name.endsWith(".xml") || entry.name.endsWith(".rels")) bytes.write(buffer, 0, count)
                }
                if (bytes.size() > 0) files[entry.name] = bytes.toByteArray()
            }
        }
        fun xml(path: String) = files[path]?.let { bytes ->
            val text = bytes.toString(Charsets.UTF_8)
            require(!text.contains("<!DOCTYPE", true) && !text.contains("<!ENTITY", true)) { "Unsupported XML declaration" }
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                isExpandEntityReferences = false
                runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
                runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
                runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
                runCatching { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
            }
            factory.newDocumentBuilder().apply {
                setEntityResolver { _, _ -> throw org.xml.sax.SAXException("External entities are unsupported") }
            }.parse(ByteArrayInputStream(bytes))
        }
        val workbook = requireNotNull(xml("xl/workbook.xml")) { "Choose an .xlsx workbook" }
        val relations = requireNotNull(xml("xl/_rels/workbook.xml.rels"))
        val targets = relations.getElementsByTagNameNS("*", "Relationship").elements().associate {
            it.getAttribute("Id") to it.getAttribute("Target")
        }
        val shared = xml("xl/sharedStrings.xml")?.getElementsByTagNameNS("*", "si")?.elements()?.map { it.textContent }.orEmpty()
        val styles = xml("xl/styles.xml")
        val numberFormats = styles?.getElementsByTagNameNS("*", "numFmt")?.elements()?.associate { it.getAttribute("numFmtId") to it.getAttribute("formatCode") }.orEmpty()
        val cellFormats = styles?.getElementsByTagNameNS("*", "cellXfs")?.elements()?.firstOrNull()?.getElementsByTagNameNS("*", "xf")?.elements().orEmpty()
        val percentStyles = cellFormats.mapIndexedNotNull { index, element ->
            val numberId = element.getAttribute("numFmtId")
            index.takeIf { numberId in setOf("9", "10") || numberFormats[numberId]?.contains('%') == true }
        }.toSet()
        val date1904 = workbook.getElementsByTagNameNS("*", "workbookPr").elements().firstOrNull()?.getAttribute("date1904") in setOf("1", "true")
        val base = if (date1904) LocalDate.of(1904, 1, 1) else LocalDate.of(1899, 12, 30)
        val sheets = linkedMapOf<String, List<Map<String, String>>>()
        val percentageCells = mutableSetOf<String>()
        var cellCount = 0
        for (sheet in workbook.getElementsByTagNameNS("*", "sheet").elements()) {
            require(sheets.size < 60) { "Too many tabs" }
            val rel = sheet.getAttributeNS("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id")
            val target = targets[rel] ?: continue
            val path = if (target.startsWith('/')) target.removePrefix("/") else "xl/$target"
            val document = xml(path) ?: continue
            val rows = document.getElementsByTagNameNS("*", "row").elements().mapNotNull { row ->
                val values = linkedMapOf<String, String>("_row" to row.getAttribute("r"))
                for (cell in row.getElementsByTagNameNS("*", "c").elements()) {
                    require(++cellCount <= 120_000) { "Workbook contains too many cells" }
                    val raw = cell.getElementsByTagNameNS("*", "v").item(0)?.textContent.orEmpty()
                    val value = when (cell.getAttribute("t")) {
                        "s" -> shared.getOrNull(raw.toIntOrNull() ?: -1).orEmpty()
                        "inlineStr" -> cell.getElementsByTagNameNS("*", "is").item(0)?.textContent.orEmpty()
                        else -> raw.ifBlank { cell.getElementsByTagNameNS("*", "f").item(0)?.textContent?.let { "=$it" }.orEmpty() }
                    }.take(4000)
                    if (value.isNotBlank()) values[cell.getAttribute("r").takeWhile(Char::isLetter)] = value
                    if (cell.getAttribute("s").toIntOrNull() in percentStyles) percentageCells += sheet.getAttribute("name") + "!" + cell.getAttribute("r")
                }
                values.takeIf { it.size > 1 }
            }
            sheets[sheet.getAttribute("name").take(120)] = rows
        }
        require(sheets.isNotEmpty()) { "Workbook has no readable tabs" }
        fun date(value: String?): LocalDate? = value?.let {
            runCatching { LocalDate.parse(it.take(10)) }.getOrNull()
                ?: it.toDoubleOrNull()?.takeIf { number -> number in 1.0..200000.0 }?.toLong()?.let(base::plusDays)
        }
        fun money(value: String?): Long? = value?.replace(",", "")?.trim()?.toBigDecimalOrNull()?.let {
            runCatching { it.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact() }.getOrNull()
        }?.takeIf { it in 0..999_999_999_999L }
        fun rows(name: String) = sheets[name].orEmpty().filter { (it["_row"]?.toIntOrNull() ?: 0) > 3 }
        val tx = mutableListOf<TransactionEntity>()
        val annotations = mutableListOf<TransactionAnnotationEntity>()
        var skipped = 0
        val now = System.currentTimeMillis()
        val occurrences = mutableMapOf<String, Int>()
        for (row in rows("Transactions")) {
            if (row["A"].isNullOrBlank() && row["G"].isNullOrBlank()) continue
            val day = date(row["A"])
            val amount = money(row["G"])
            val direction = when (row["C"]?.lowercase()) { "income" -> "CREDIT"; "expense" -> "DEBIT"; else -> null }
            if (day == null || amount == null || amount <= 0 || direction == null) { skipped++; continue }
            val canonical = listOf(day, direction, amount, row["D"], row["E"], row["F"], row["I"]).joinToString("|")
            val occurrence = occurrences.getOrDefault(canonical, 0)
            occurrences[canonical] = occurrence + 1
            val id = "workbook:" + UUID.nameUUIDFromBytes("$canonical|$occurrence".toByteArray())
            tx += TransactionEntity(id, day.atStartOfDay(ZoneId.of("Asia/Kolkata")).toInstant().toEpochMilli(), now,
                amount, "INR", direction, method(row["F"]), row["E"], row["D"] ?: "Uncategorized", row["I"], null,
                null, null, "workbook", "Workbook import", 1.0, "workbook-v1", row["J"].orEmpty().take(240), id, "LOCAL")
            annotations += TransactionAnnotationEntity(id, when (row["H"]?.lowercase()) { "yes" -> true; "no" -> false; else -> null })
        }
        val budgets = rows("Monthly Budget").mapNotNull { row ->
            val month = date(row["A"])?.let(YearMonth::from)?.toString() ?: return@mapNotNull null
            BudgetEntity(month, money(row["B"]), money(row["C"]), money(row["D"]), money(row["E"]), money(row["F"]))
        }
        val debts = rows("EMI Tracker").mapNotNull { row ->
            val name = row["B"]?.takeIf { it.isNotBlank() && !it.startsWith('=') } ?: return@mapNotNull null
            val original = money(row["D"]); val outstanding = money(row["E"]); val emi = money(row["J"])
            val start = date(row["L"])
            val rate = row["F"]?.removeSuffix("%")?.toDoubleOrNull()?.let { value -> if ("EMI Tracker!F${row["_row"]}" in percentageCells) value * 100 else value }
            val term = row["G"]?.toDoubleOrNull()?.toInt()
            val paid = row["H"]?.toDoubleOrNull()?.toInt(); val due = row["K"]?.toDoubleOrNull()?.toInt()
            if (original == null || outstanding == null || emi == null || start == null || rate == null || term == null || paid == null || due !in 1..31 || rate !in 0.0..100.0 || term !in 1..600 || paid !in 0..term) { skipped++; return@mapNotNull null }
            DebtEntity("workbook:" + UUID.nameUUIDFromBytes((row["A"] ?: "$name|$start").toByteArray()), name, row["C"] ?: "Other", original, outstanding, rate, emi, term, paid, requireNotNull(due), start.toString(), priority = row["O"] ?: "Medium", active = row["P"]?.lowercase() !in setOf("closed", "paid"))
        }
        val goals = rows("Goals").mapNotNull { row ->
            val name = row["A"]?.takeIf { it.isNotBlank() && !it.startsWith('=') } ?: return@mapNotNull null
            val target = money(row["B"]); val current = money(row["C"]); val deadline = date(row["D"])
            if (target == null || target <= 0 || current == null || deadline == null) { skipped++; return@mapNotNull null }
            GoalEntity("workbook:" + UUID.nameUUIDFromBytes("$name|$deadline".toByteArray()), name, target, current, deadline.toString(), row["I"] ?: "Medium")
        }
        val json = sheets.entries.joinToString(",", "[", "]") { (name, data) ->
            val rowsJson = data.joinToString(",", "[", "]") { row -> row.entries.joinToString(",", "{", "}") { (column, value) -> "${quote(column)}:${quote(value)}" } }
            "{\"name\":${quote(name)},\"rows\":$rowsJson}"
        }
        return WorkbookPreview(WorkbookSnapshotEntity(title = "Personal Finance workbook", importedAtEpochMs = now, sheetsJson = json), tx, annotations, budgets, debts, goals, sheets.keys.toList(), skipped)
    }

    private fun quote(value: String): String = buildString {
        append('"')
        value.forEach { character -> when (character) {
            '"' -> append("\\\""); '\\' -> append("\\\\"); '\n' -> append("\\n"); '\r' -> append("\\r"); '\t' -> append("\\t")
            else -> if (character.code < 32) append("\\u" + character.code.toString(16).padStart(4, '0')) else append(character)
        } }
        append('"')
    }

    private fun org.w3c.dom.NodeList.elements(): List<Element> = (0 until length).mapNotNull { item(it) as? Element }
    private fun method(value: String?): String = when (value?.lowercase()) {
        "upi" -> "UPI"; "debit card" -> "DEBIT_CARD"; "credit card" -> "CREDIT_CARD"; "cash" -> "CASH"
        "bank transfer" -> "BANK_TRANSFER"; "auto debit" -> "AUTO_DEBIT"; else -> "OTHER"
    }
}
