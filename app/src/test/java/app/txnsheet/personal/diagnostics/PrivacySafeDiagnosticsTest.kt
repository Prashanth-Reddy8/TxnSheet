package app.txnsheet.personal.diagnostics

import app.txnsheet.personal.data.local.DiagnosticDao
import app.txnsheet.personal.data.local.DiagnosticEventEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PrivacySafeDiagnosticsTest {
    @Test
    fun `fixed privacy-safe context is recorded`() = runTest {
        val dao = RecordingDiagnosticDao()

        PrivacySafeDiagnostics(dao).record(
            code = "SYNC_SCHEMA_MISMATCH",
            component = "sync",
            severity = "WARN",
            safeContext = "header_mismatch",
        )

        assertEquals("SYNC_SCHEMA_MISMATCH", dao.events.single().code)
        assertEquals("header_mismatch", dao.events.single().redactedContext)
    }

    @Test
    fun `financial identifiers and secrets are refused`() {
        val diagnostics = PrivacySafeDiagnostics(RecordingDiagnosticDao())

        listOf(
            "INR amount",
            "otp present",
            "bearer secret",
            "account suffix",
            "reference found",
            "id 123456",
        ).forEach { unsafe ->
            assertThrows(IllegalArgumentException::class.java) {
                kotlinx.coroutines.runBlocking {
                    diagnostics.record("TEST", "test", safeContext = unsafe)
                }
            }
        }
    }

    private class RecordingDiagnosticDao : DiagnosticDao {
        val events = mutableListOf<DiagnosticEventEntity>()

        override suspend fun insert(event: DiagnosticEventEntity) {
            events += event
        }

        override fun observeRecent(limit: Int): Flow<List<DiagnosticEventEntity>> =
            flowOf(events.take(limit))

        override suspend fun purgeBefore(cutoffEpochMs: Long): Int = 0

        override suspend fun deleteAll() {
            events.clear()
        }
    }
}
