package app.txnsheet.personal.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.txnsheet.personal.data.local.WorkbookSnapshotEntity
import app.txnsheet.personal.data.repository.WorkbookPreview
import org.json.JSONArray

@Composable
fun WorkbookImportDialog(preview: WorkbookPreview, working: Boolean, onImport: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = { if (!working) onDismiss() },
        title = { Text("Import your workbook") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${preview.sheetNames.size} tabs read successfully.")
                Text("${preview.transactions.size} transactions\n${preview.budgets.size} monthly budgets\n${preview.debts.size} debts\n${preview.goals.size} goals")
                if (preview.skippedRows > 0) Text("${preview.skippedRows} incomplete rows will be kept in the workbook viewer only.")
                Text("Existing records and plans are kept. The full workbook copy in the viewer is replaced. All imported data stays on this phone.")
                Text("Formula cells without saved results remain visible as formulas. Legacy tabs are preserved for reference and do not change dashboard totals.", style = MaterialTheme.typography.bodySmall)
                Text("Transactions already captured from notifications may also exist in this workbook. Check for overlapping records after importing.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onImport, enabled = !working) { Text(if (working) "Importing…" else "Import") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !working) { Text("Cancel") } },
    )
}

@Composable
fun WorkbookScreen(snapshot: WorkbookSnapshotEntity?, onBack: () -> Unit, onImport: () -> Unit) {
    val sheets = remember(snapshot?.sheetsJson) { runCatching { JSONArray(snapshot?.sheetsJson ?: "[]") }.getOrElse { JSONArray() } }
    var selected by rememberSaveable(snapshot?.importedAtEpochMs) { mutableStateOf(0) }
    var search by rememberSaveable { mutableStateOf("") }
    val tab = if (sheets.length() > 0) sheets.optJSONObject(selected.coerceIn(0, sheets.length() - 1)) else null
    val rows = remember(tab?.toString(), search) {
        val data = tab?.optJSONArray("rows") ?: JSONArray()
        (0 until data.length()).map { data.getJSONObject(it) }.filter { search.isBlank() || it.toString().contains(search, true) }
    }
    Scaffold(topBar = { DetailTopBar("Workbook data", onBack) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("Your original workbook", style = MaterialTheme.typography.headlineSmall)
                Text("All populated tabs and cells are available here. Use Plan and Activity to edit the app's records.", style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = onImport) { Text(if (snapshot == null) "Choose Excel file" else "Import another workbook") }
                if (snapshot != null) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        (0 until sheets.length()).forEach { index -> FilterChip(selected == index, { selected = index }, label = { Text(sheets.getJSONObject(index).optString("name")) }) }
                    }
                    OutlinedTextField(search, { search = it.take(80) }, label = { Text("Find a value or label") }, modifier = Modifier.fillMaxWidth())
                }
            }
            items(rows, key = { it.optString("_row") }) { row ->
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Row ${row.optString("_row")}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        row.keys().asSequence().filter { it != "_row" }.sortedWith(compareBy<String> { it.length }.thenBy { it }).forEach { column ->
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(column, Modifier.width(28.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(row.optString(column), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}
