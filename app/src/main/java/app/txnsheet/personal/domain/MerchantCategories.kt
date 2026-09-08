package app.txnsheet.personal.domain

import app.txnsheet.personal.parsing.TextNormalizer
import java.util.Locale

/** Small, explainable local merchant aliases. Unrecognized recipients remain uncategorized. */
object MerchantCategories {
    val categories: List<String> = listOf(
        "Food", "Fuel", "Rent", "Utilities", "Travel", "Shopping", "Entertainment", "Health",
        "Insurance", "EMI", "Credit Card", "Subscriptions", "Education", "Family", "Miscellaneous",
        "Salary", "Other Income", "Uncategorized",
    )

    private val aliases = linkedMapOf(
        "Food" to listOf(listOf("zomato"), listOf("swiggy")),
        "Fuel" to listOf(listOf("bpcl"), listOf("hpcl"), listOf("indian", "oil"), listOf("indianoil")),
        "Shopping" to listOf(listOf("amazon"), listOf("flipkart")),
        "Subscriptions" to listOf(listOf("netflix"), listOf("spotify")),
        "Travel" to listOf(listOf("uber"), listOf("ola")),
    )
    private val tokenSeparator = Regex("[^\\p{L}\\p{N}]+")
    private val unassignedCategories = setOf("uncategorized", "misc", "miscellaneous")

    /** Owner rules run before this fallback; existing specific categories retain their meaning. */
    fun resolveDefault(counterparty: String?, fallback: String): String {
        if (fallback.trim().lowercase(Locale.ROOT) !in unassignedCategories) return fallback
        return suggest(counterparty) ?: fallback
    }

    fun suggest(counterparty: String?): String? {
        val tokens = counterparty?.let(TextNormalizer::normalize)?.lowercase(Locale.ROOT)
            ?.split(tokenSeparator)?.filter(String::isNotBlank).orEmpty()
        if (tokens.isEmpty()) return null
        // A wallet/payment gateway label does not identify what the owner bought.
        if (tokens.containsSequence(listOf("amazon", "pay")) || "amazonpay" in tokens) return null
        val matches = aliases.filterValues { categoryAliases ->
            categoryAliases.any { tokens.containsSequence(it) }
        }.keys
        // Conflicting brand labels are ambiguous and need an owner rule or manual choice.
        return matches.singleOrNull()
    }

    private fun List<String>.containsSequence(sequence: List<String>): Boolean =
        size >= sequence.size && indices.any { start ->
            start + sequence.size <= size && sequence.indices.all { this[start + it] == sequence[it] }
        }
}
