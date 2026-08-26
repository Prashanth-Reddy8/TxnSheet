package app.txnsheet.personal.data.remote

import app.txnsheet.personal.data.local.TransactionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Minimal, privacy-conscious Sheets v4 client. It deliberately has no HTTP logging and accepts
 * an ephemeral access token per call, so neither credentials nor transaction rows are persisted.
 */
class GoogleSheetsHttpGateway(
    private val client: OkHttpClient = defaultClient(),
) : SheetsGateway {
    private val requestGate = Mutex()
    private var lastRequestStartedNanos = 0L
    override suspend fun createWorkbook(
        token: EphemeralAccessToken,
        request: WorkbookCreationRequest,
    ): WorkbookDescriptor {
        val createBody = JSONObject()
            .put(
                "properties",
                JSONObject()
                    .put("title", request.title.ifBlank { "TxnSheet Ledger" }),
            )
            .put(
                "sheets",
                JSONArray()
                    .put(sheetDefinition(TransactionSheetContract.TRANSACTIONS_TAB, 2_000, 18, 1))
                    .put(sheetDefinition(TransactionSheetContract.DASHBOARD_TAB, 100, 8, 0))
                    .put(sheetDefinition(TransactionSheetContract.CONFIG_TAB, 30, 2, 1)),
            )

        // Do not force locale or timeZone during creation. Sheets supports only a subset of
        // CLDR/locale identifiers and rejects the entire create request with HTTP 400 when a
        // device-provided alias is not accepted. The owner's Google account supplies safe
        // defaults; TxnSheet still stores its explicit timezone in Config and uses it for every
        // transaction date conversion.

        val created = executeJson(
            request = authorizedRequest(
                url = endpoint(),
                token = token,
            ).post(createBody.toString().toRequestBody(JSON_MEDIA_TYPE)).build(),
        )
        val descriptor = descriptorFrom(created)

        initializeValues(token, descriptor, request)
        initializeFormatting(token, descriptor)

        when (validateSchema(token, descriptor.spreadsheetId, request.schemaVersion)) {
            SchemaValidationResult.Valid -> Unit
            is SchemaValidationResult.Invalid -> throw SheetsProtocolException(
                "CREATED_WORKBOOK_SCHEMA_INVALID",
            )
        }
        return descriptor
    }

    override suspend fun inspectWorkbook(
        token: EphemeralAccessToken,
        spreadsheetId: String,
    ): WorkbookDescriptor {
        requireSpreadsheetId(spreadsheetId)
        val url = endpoint(spreadsheetId).newBuilder()
            .addQueryParameter(
                "fields",
                "spreadsheetId,spreadsheetUrl,sheets.properties(sheetId,title)",
            )
            .build()
        return descriptorFrom(
            executeJson(authorizedRequest(url, token).get().build()),
        )
    }

    override suspend fun validateSchema(
        token: EphemeralAccessToken,
        spreadsheetId: String,
        supportedSchemaVersion: Int,
    ): SchemaValidationResult {
        requireSpreadsheetId(spreadsheetId)
        val url = endpoint(spreadsheetId, "values:batchGet").newBuilder()
            .addQueryParameter("ranges", TransactionSheetContract.HEADER_RANGE)
            .addQueryParameter("ranges", TransactionSheetContract.CONFIG_RANGE)
            .addQueryParameter("majorDimension", "ROWS")
            .addQueryParameter("valueRenderOption", "UNFORMATTED_VALUE")
            .build()
        val result = executeJson(authorizedRequest(url, token).get().build())
        val ranges = result.optJSONArray("valueRanges")
            ?: return SchemaValidationResult.Invalid(
                SchemaValidationResult.Invalid.Reason.CONFIG_MISSING,
            )
        val headerValues = ranges.optJSONObject(0)
            ?.optJSONArray("values")
            ?.optJSONArray(0)
            ?.asStrings()
            .orEmpty()
        if (headerValues != TransactionSheetContract.HEADERS) {
            return SchemaValidationResult.Invalid(
                SchemaValidationResult.Invalid.Reason.HEADER_MISMATCH,
            )
        }

        val configRows = ranges.optJSONObject(1)
            ?.optJSONArray("values")
            ?: return SchemaValidationResult.Invalid(
                SchemaValidationResult.Invalid.Reason.CONFIG_MISSING,
            )
        val config = buildMap<String, Any> {
            for (index in 0 until configRows.length()) {
                val row = configRows.optJSONArray(index) ?: continue
                if (row.length() >= 2) put(row.optString(0), row.opt(1))
            }
        }
        if (!REQUIRED_CONFIG_KEYS.all(config::containsKey)) {
            return SchemaValidationResult.Invalid(
                SchemaValidationResult.Invalid.Reason.CONFIG_MISSING,
            )
        }
        val remoteVersion = when (val value = config["schema_version"]) {
            is Number -> value.toInt()
            else -> value?.toString()?.toIntOrNull()
        }
        return if (remoteVersion == supportedSchemaVersion) {
            SchemaValidationResult.Valid
        } else {
            SchemaValidationResult.Invalid(
                SchemaValidationResult.Invalid.Reason.SCHEMA_VERSION_UNSUPPORTED,
            )
        }
    }

    override suspend fun findTransactionByUuid(
        token: EphemeralAccessToken,
        spreadsheetId: String,
        transactionId: String,
    ): RemoteTransactionLocation? {
        requireSpreadsheetId(spreadsheetId)
        require(UUID_PATTERN.matches(transactionId)) { "transactionId must be a UUID" }
        val url = endpoint(
            spreadsheetId,
            "values",
            TransactionSheetContract.UUID_RANGE,
        ).newBuilder()
            .addQueryParameter("majorDimension", "COLUMNS")
            .addQueryParameter("valueRenderOption", "UNFORMATTED_VALUE")
            .build()
        val values = executeJson(authorizedRequest(url, token).get().build())
            .optJSONArray("values")
            ?.optJSONArray(0)
            ?: return null
        for (index in 0 until values.length()) {
            if (values.optString(index) == transactionId) {
                return RemoteTransactionLocation(rowNumber = index + 2)
            }
        }
        return null
    }

    override suspend fun appendTransaction(
        token: EphemeralAccessToken,
        spreadsheetId: String,
        transaction: TransactionEntity,
        timezone: String,
    ): AppendReceipt {
        requireSpreadsheetId(spreadsheetId)
        val row = TransactionSheetRowMapper.toRow(transaction, timezone)
        val url = endpoint(
            spreadsheetId,
            "values",
            "${TransactionSheetContract.APPEND_RANGE}:append",
        ).newBuilder()
            .addQueryParameter("valueInputOption", "RAW")
            .addQueryParameter("insertDataOption", "INSERT_ROWS")
            .addQueryParameter("includeValuesInResponse", "false")
            .build()
        val result = executeJson(
            authorizedRequest(url, token)
                .post(rowsBody(listOf(row)).toString().toRequestBody(JSON_MEDIA_TYPE))
                .build(),
        )
        val range = result.optJSONObject("updates")?.optString("updatedRange")
            ?.takeIf(String::isNotBlank)
            ?: throw SheetsProtocolException("APPEND_RANGE_MISSING")
        return AppendReceipt(range)
    }

    override suspend fun updateCorrection(
        token: EphemeralAccessToken,
        spreadsheetId: String,
        transaction: TransactionEntity,
        timezone: String,
        location: RemoteTransactionLocation,
    ): AppendReceipt {
        requireSpreadsheetId(spreadsheetId)
        require(location.rowNumber >= 2) { "Cannot update the header row" }
        val expected = RemoteTransactionLocation(location.rowNumber)
        require(location.range == expected.range) { "Unverified correction range" }
        val rowWithoutUuid = TransactionSheetRowMapper.toRow(transaction, timezone).drop(1)
        val updateRange = "Transactions!B${location.rowNumber}:R${location.rowNumber}"
        val url = endpoint(spreadsheetId, "values", updateRange).newBuilder()
            .addQueryParameter("valueInputOption", "RAW")
            .addQueryParameter("includeValuesInResponse", "false")
            .build()
        val result = executeJson(
            authorizedRequest(url, token)
                .put(rowsBody(listOf(rowWithoutUuid)).toString().toRequestBody(JSON_MEDIA_TYPE))
                .build(),
        )
        val range = result.optString("updatedRange").takeIf(String::isNotBlank)
            ?: throw SheetsProtocolException("UPDATE_RANGE_MISSING")
        return AppendReceipt(range)
    }

    private suspend fun initializeValues(
        token: EphemeralAccessToken,
        workbook: WorkbookDescriptor,
        request: WorkbookCreationRequest,
    ) {
        val header = TransactionSheetContract.HEADERS
        val config = listOf(
            listOf("parameter", "value"),
            listOf("schema_version", request.schemaVersion),
            listOf("app_version", request.appVersion),
            listOf("created_at", Instant.ofEpochMilli(request.createdAtEpochMs).toString()),
            listOf("timezone", request.timezone),
            listOf("currency", request.currency),
        )
        val dashboard = listOf(
            listOf("TxnSheet dashboard"),
            listOf("Live from the Transactions tab"),
            listOf("Metric", "Value"),
            listOf(
                "Credits this month",
                "=SUMIFS(Transactions!D:D,Transactions!F:F,\"CREDIT\",Transactions!B:B,\">=\"&EOMONTH(TODAY(),-1)+1)",
            ),
            listOf(
                "Debits this month",
                "=SUMIFS(Transactions!D:D,Transactions!F:F,\"DEBIT\",Transactions!B:B,\">=\"&EOMONTH(TODAY(),-1)+1)",
            ),
            listOf("Net this month", "=B4-B5"),
            listOf("Transactions", "=COUNTA(Transactions!A2:A)"),
        )
        val data = JSONArray()
            .put(valueRange(TransactionSheetContract.HEADER_RANGE, listOf(header)))
            .put(valueRange("Config!A1:B6", config))
            .put(valueRange("Dashboard!A1:B7", dashboard))
        val body = JSONObject()
            .put("valueInputOption", "USER_ENTERED")
            .put("data", data)
        executeJson(
            authorizedRequest(endpoint(workbook.spreadsheetId, "values:batchUpdate"), token)
                .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build(),
        )
    }

    private suspend fun initializeFormatting(
        token: EphemeralAccessToken,
        workbook: WorkbookDescriptor,
    ) {
        val requests = JSONArray()
            .put(headerFormat(workbook.transactionsTabId, 18))
            .put(headerFormat(workbook.configTabId, 2))
            .put(headerFormat(workbook.dashboardTabId, 2, rowIndex = 2))
            .put(numberFormat(workbook.transactionsTabId, 1, 3, "yyyy-mm-dd hh:mm"))
            .put(numberFormat(workbook.transactionsTabId, 3, 4, "#,##0.00"))
            .put(numberFormat(workbook.transactionsTabId, 12, 13, "#,##0.00"))
            .put(autoResize(workbook.transactionsTabId, 0, 18))
            .put(autoResize(workbook.dashboardTabId, 0, 2))
            .put(autoResize(workbook.configTabId, 0, 2))
        val body = JSONObject().put("requests", requests)
        executeJson(
            authorizedRequest(endpoint(workbook.spreadsheetId, ":batchUpdate"), token)
                .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build(),
        )
    }

    private fun descriptorFrom(json: JSONObject): WorkbookDescriptor {
        val spreadsheetId = json.optString("spreadsheetId").takeIf(String::isNotBlank)
            ?: throw SheetsProtocolException("SPREADSHEET_ID_MISSING")
        val ids = mutableMapOf<String, Int>()
        val sheets = json.optJSONArray("sheets") ?: JSONArray()
        for (index in 0 until sheets.length()) {
            val properties = sheets.optJSONObject(index)?.optJSONObject("properties") ?: continue
            ids[properties.optString("title")] = properties.optInt("sheetId", Int.MIN_VALUE)
        }
        fun idFor(title: String): Int = ids[title]
            ?.takeUnless { it == Int.MIN_VALUE }
            ?: throw SheetsProtocolException("SHEET_MISSING_$title")
        return WorkbookDescriptor(
            spreadsheetId = spreadsheetId,
            spreadsheetUrl = json.optString("spreadsheetUrl").takeIf(String::isNotBlank)
                ?: "https://docs.google.com/spreadsheets/d/$spreadsheetId/edit",
            transactionsTabId = idFor(TransactionSheetContract.TRANSACTIONS_TAB),
            dashboardTabId = idFor(TransactionSheetContract.DASHBOARD_TAB),
            configTabId = idFor(TransactionSheetContract.CONFIG_TAB),
        )
    }

    private suspend fun executeJson(request: Request): JSONObject = requestGate.withLock {
        val elapsedMillis = (System.nanoTime() - lastRequestStartedNanos) / NANOS_PER_MILLISECOND
        if (lastRequestStartedNanos != 0L && elapsedMillis < MIN_REQUEST_INTERVAL_MILLIS) {
            delay(MIN_REQUEST_INTERVAL_MILLIS - elapsedMillis)
        }
        lastRequestStartedNanos = System.nanoTime()
        withContext(Dispatchers.IO) {
            client.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val parsed = runCatching { JSONObject(raw) }.getOrNull()
                    val error = parsed?.optJSONObject("error")
                    throw SheetsHttpException(
                        statusCode = response.code,
                        apiStatus = error?.optString("status")?.takeIf(String::isNotBlank),
                        retryAfterMillis = parseRetryAfter(response.header("Retry-After")),
                        apiReason = extractReason(error),
                    )
                }
                if (raw.isBlank()) return@use JSONObject()
                try {
                    JSONObject(raw)
                } catch (_: JSONException) {
                    throw SheetsProtocolException("INVALID_JSON_RESPONSE")
                }
            }
        }
    }

    private fun authorizedRequest(url: HttpUrl, token: EphemeralAccessToken): Request.Builder =
        Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${token.raw}")
            .header("Accept", "application/json")
            .header("User-Agent", "TxnSheet-Android/1")

    private fun endpoint(vararg pathSegments: String): HttpUrl {
        val builder = API_ROOT.newBuilder()
        pathSegments.forEach { segment ->
            if (segment.startsWith(":")) {
                val old = builder.build().encodedPath.trimEnd('/')
                builder.encodedPath(old + segment)
            } else {
                builder.addPathSegment(segment)
            }
        }
        return builder.build()
    }

    private fun sheetDefinition(
        title: String,
        rows: Int,
        columns: Int,
        frozenRows: Int,
    ): JSONObject = JSONObject().put(
        "properties",
        JSONObject()
            .put("title", title)
            .put(
                "gridProperties",
                JSONObject()
                    .put("rowCount", rows)
                    .put("columnCount", columns)
                    .put("frozenRowCount", frozenRows),
            ),
    )

    private fun rowsBody(rows: List<List<Any>>): JSONObject = JSONObject()
        .put("majorDimension", "ROWS")
        .put("values", rows.toJsonArray())

    private fun valueRange(range: String, rows: List<List<Any>>): JSONObject = rowsBody(rows)
        .put("range", range)

    private fun List<List<Any>>.toJsonArray(): JSONArray = JSONArray().also { outer ->
        forEach { row ->
            outer.put(JSONArray().also { inner -> row.forEach(inner::put) })
        }
    }

    private fun headerFormat(sheetId: Int, columns: Int, rowIndex: Int = 0): JSONObject =
        JSONObject().put(
            "repeatCell",
            JSONObject()
                .put(
                    "range",
                    gridRange(sheetId, rowIndex, rowIndex + 1, 0, columns),
                )
                .put(
                    "cell",
                    JSONObject().put(
                        "userEnteredFormat",
                        JSONObject()
                            .put(
                                "backgroundColorStyle",
                                JSONObject().put(
                                    "rgbColor",
                                    JSONObject().put("red", 0.10).put("green", 0.12).put("blue", 0.15),
                                ),
                            )
                            .put(
                                "textFormat",
                                JSONObject().put("bold", true).put(
                                    "foregroundColorStyle",
                                    JSONObject().put(
                                        "rgbColor",
                                        JSONObject().put("red", 1).put("green", 1).put("blue", 1),
                                    ),
                                ),
                            )
                            .put("verticalAlignment", "MIDDLE")
                            .put("wrapStrategy", "WRAP"),
                    ),
                )
                .put("fields", "userEnteredFormat(backgroundColorStyle,textFormat,verticalAlignment,wrapStrategy)"),
        )

    private fun numberFormat(
        sheetId: Int,
        startColumn: Int,
        endColumn: Int,
        pattern: String,
    ): JSONObject = JSONObject().put(
        "repeatCell",
        JSONObject()
            .put("range", gridRange(sheetId, 1, null, startColumn, endColumn))
            .put(
                "cell",
                JSONObject().put(
                    "userEnteredFormat",
                    JSONObject().put(
                        "numberFormat",
                        JSONObject()
                            .put("type", if (pattern.contains("yy")) "DATE_TIME" else "NUMBER")
                            .put("pattern", pattern),
                    ),
                ),
            )
            .put("fields", "userEnteredFormat.numberFormat"),
    )

    private fun autoResize(sheetId: Int, startColumn: Int, endColumn: Int): JSONObject =
        JSONObject().put(
            "autoResizeDimensions",
            JSONObject().put(
                "dimensions",
                JSONObject()
                    .put("sheetId", sheetId)
                    .put("dimension", "COLUMNS")
                    .put("startIndex", startColumn)
                    .put("endIndex", endColumn),
            ),
        )

    private fun gridRange(
        sheetId: Int,
        startRow: Int,
        endRow: Int?,
        startColumn: Int,
        endColumn: Int,
    ): JSONObject = JSONObject()
        .put("sheetId", sheetId)
        .put("startRowIndex", startRow)
        .put("startColumnIndex", startColumn)
        .put("endColumnIndex", endColumn)
        .also { if (endRow != null) it.put("endRowIndex", endRow) }

    private fun JSONArray.asStrings(): List<String> = buildList(length()) {
        for (index in 0 until length()) add(optString(index))
    }

    private fun extractReason(error: JSONObject?): String? {
        if (error == null) return null
        val details = error.optJSONArray("details")
        if (details != null) {
            for (index in 0 until details.length()) {
                val reason = details.optJSONObject(index)?.optString("reason")
                if (!reason.isNullOrBlank()) return reason
            }
        }
        return error.optJSONArray("errors")
            ?.optJSONObject(0)
            ?.optString("reason")
            ?.takeIf(String::isNotBlank)
    }

    private fun parseRetryAfter(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        value.trim().toLongOrNull()?.let { return TimeUnit.SECONDS.toMillis(it.coerceAtLeast(0)) }
        return runCatching {
            val at = ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()
            (at.toEpochMilli() - System.currentTimeMillis()).coerceAtLeast(0)
        }.getOrNull()
    }

    private fun requireSpreadsheetId(id: String) {
        require(SPREADSHEET_ID_PATTERN.matches(id)) { "Invalid spreadsheet id" }
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private const val MIN_REQUEST_INTERVAL_MILLIS = 1_000L
        private const val NANOS_PER_MILLISECOND = 1_000_000L
        private val API_ROOT = HttpUrl.Builder()
            .scheme("https")
            .host("sheets.googleapis.com")
            .addPathSegments("v4/spreadsheets")
            .build()
        private val SPREADSHEET_ID_PATTERN = Regex("^[A-Za-z0-9_-]{10,200}$")
        private val UUID_PATTERN = Regex(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$",
        )
        private val REQUIRED_CONFIG_KEYS = setOf(
            "schema_version",
            "app_version",
            "created_at",
            "timezone",
            "currency",
        )

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            // At most 21 calls are possible in a ten-write Worker run; this keeps the worst-case
            // network budget under WorkManager's execution window while remaining mobile-safe.
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .writeTimeout(25, TimeUnit.SECONDS)
            .callTimeout(25, TimeUnit.SECONDS)
            // A values.append POST has no server-side idempotency key. All retries must return
            // through LedgerSyncWorker's remote UUID preflight, never OkHttp's transparent retry.
            .retryOnConnectionFailure(false)
            .build()
    }
}
