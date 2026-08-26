package app.txnsheet.personal.data.repository

import app.txnsheet.personal.data.local.CategoryRuleDao
import app.txnsheet.personal.parsing.TextNormalizer
import java.util.Locale

/** Applies owner-created category rules locally and in a stable priority order. */
class CategoryRuleResolver(private val dao: CategoryRuleDao) {
    suspend fun resolve(counterparty: String?, fallback: String): String {
        val candidate = counterparty
            ?.let(TextNormalizer::normalize)
            ?.lowercase(Locale.ROOT)
            ?.takeIf(String::isNotBlank)
            ?: return fallback

        val match = dao.allByPriority().firstOrNull { rule ->
            val term = TextNormalizer.normalize(rule.normalizedTerm)
                .lowercase(Locale.ROOT)
                .takeIf(String::isNotBlank)
                ?: return@firstOrNull false
            when (rule.matchType.uppercase(Locale.ROOT)) {
                "EXACT" -> candidate == term
                "CONTAINS" -> candidate.contains(term)
                else -> false
            }
        }
        return match?.category?.trim()?.take(64)?.takeIf(String::isNotBlank) ?: fallback
    }
}

