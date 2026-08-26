package app.txnsheet.personal.diagnostics

import app.txnsheet.personal.data.local.DiagnosticDao
import app.txnsheet.personal.data.local.DiagnosticEventEntity

class PrivacySafeDiagnostics(private val dao: DiagnosticDao) {
    suspend fun record(
        code: String,
        component: String,
        severity: String = "INFO",
        safeContext: String? = null,
    ) {
        require(!FORBIDDEN_CONTEXT.matcher(safeContext.orEmpty()).find()) {
            "Diagnostic context may contain financial or authentication data"
        }
        dao.insert(
            DiagnosticEventEntity(
                createdAtEpochMs = System.currentTimeMillis(),
                code = code.take(64),
                severity = severity.take(16),
                component = component.take(32),
                redactedContext = safeContext?.take(96),
            ),
        )
    }

    private companion object {
        val FORBIDDEN_CONTEXT = Regex(
            pattern = "(?i)(₹|INR|Rs\\.?|otp|token|bearer|account|a/c|utr|ref(?:erence)?|\\d{4,})",
        ).toPattern()
    }
}
