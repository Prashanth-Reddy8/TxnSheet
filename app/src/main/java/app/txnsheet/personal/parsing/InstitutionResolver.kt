package app.txnsheet.personal.parsing

import java.util.Locale

/** Separates the app displaying an alert from a bank explicitly identified in that alert. */
internal object InstitutionResolver {
    private val messagingLabels = setOf(
        "messages", "google messages", "messaging", "sms", "truecaller",
        "com.google.android.apps.messaging", "com.truecaller",
    )
    private val bankMarkers = linkedMapOf(
        "Kotak Mahindra Bank" to Regex("""\bKOTAK(?:\s{1,3}MAHINDRA)?\s{1,3}BANK\b|\bKOTAKB\b""", RegexOption.IGNORE_CASE),
        "HDFC Bank" to Regex("""\bHDFC\s{1,3}BANK\b|\bHDFCBK\b""", RegexOption.IGNORE_CASE),
        "ICICI Bank" to Regex("""\bICICI\s{1,3}BANK\b|\bICICIB\b""", RegexOption.IGNORE_CASE),
        "State Bank of India" to Regex("""\bSTATE\s{1,3}BANK\s{1,3}OF\s{1,3}INDIA\b|\b(?:SBI|SBIINB|SBIPSG)\b""", RegexOption.IGNORE_CASE),
        "Axis Bank" to Regex("""\bAXIS\s{1,3}BANK\b|\b(?:AXISBK|AXISB)\b""", RegexOption.IGNORE_CASE),
    )

    fun resolve(normalizedText: String, explicitlyProvided: String?): String? {
        explicitlyProvided?.let(TextNormalizer::normalize)?.trim()?.take(128)
            ?.takeIf { it.isNotBlank() && it.lowercase(Locale.ROOT) !in messagingLabels }
            ?.let { return it }
        val matches = bankMarkers.filterValues { it.containsMatchIn(normalizedText) }.keys
        return matches.singleOrNull()
    }
}
