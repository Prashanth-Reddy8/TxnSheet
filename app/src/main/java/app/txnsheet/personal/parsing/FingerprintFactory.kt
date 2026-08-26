package app.txnsheet.personal.parsing

import app.txnsheet.personal.domain.TransactionDraft
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.util.Locale

/** Creates the versioned, privacy-safe local duplicate fingerprint. */
object FingerprintFactory {
    fun create(
        draft: TransactionDraft,
        sourcePackage: String,
        normalizedText: String,
        captureTime: Instant,
    ): String {
        require(sourcePackage.isNotBlank()) { "sourcePackage must not be blank" }
        val amountMinor = requireNotNull(draft.amountMinor) {
            "A valid amount is required before fingerprinting"
        }
        val direction = requireNotNull(draft.direction) {
            "A valid direction is required before fingerprinting"
        }

        val canonicalInput = if (!draft.referenceId.isNullOrBlank()) {
            listOf(
                "v1",
                canonical(draft.institution.orEmpty()),
                canonical(draft.referenceId),
                amountMinor.toString(),
                direction.name,
            ).joinToString("|")
        } else {
            val normalizedTextHash = sha256(TextNormalizer.normalize(normalizedText))
            val captureMinute = Math.floorDiv(captureTime.epochSecond, 60L)
            listOf(
                "v1",
                canonical(sourcePackage),
                normalizedTextHash,
                captureMinute.toString(),
            ).joinToString("|")
        }

        return sha256(canonicalInput)
    }

    private fun canonical(value: String): String = TextNormalizer.normalize(value)
        .lowercase(Locale.ROOT)

    private fun sha256(value: String): String = MessageDigest
        .getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
}
