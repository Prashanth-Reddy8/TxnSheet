package app.txnsheet.personal.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MerchantCategoriesTest {
    @Test
    fun `recognizable merchant tokens map to workbook categories`() {
        val examples = mapOf(
            "ZOMATO LIMITED" to "Food",
            "swiggy@upi" to "Food",
            "BPCL FUEL STATION" to "Fuel",
            "HPCL" to "Fuel",
            "Indian Oil Corporation" to "Fuel",
            "IndianOil" to "Fuel",
            "AMAZON SELLER SERVICES" to "Shopping",
            "flipkart.com" to "Shopping",
            "NETFLIX ENTERTAINMENT" to "Subscriptions",
            "Spotify India" to "Subscriptions",
            "Uber India" to "Travel",
            "OLA CABS" to "Travel",
        )
        examples.forEach { (merchant, expected) -> assertEquals(merchant, expected, MerchantCategories.suggest(merchant)) }
    }

    @Test
    fun `substring collisions and unknown names are not categorized`() {
        listOf("Chocolate Shop", "Viola Traders", "Supermarket", "Uberoi Enterprises", "Ravi Kumar", "", " ")
            .forEach { assertNull(it, MerchantCategories.suggest(it)) }
        assertNull(MerchantCategories.suggest(null))
    }

    @Test
    fun `gateway labels and conflicting brands remain unknown`() {
        assertNull(MerchantCategories.suggest("Amazon Pay"))
        assertNull(MerchantCategories.suggest("amazonpay@upi"))
        assertNull(MerchantCategories.suggest("UBER AMAZON"))
    }

    @Test
    fun `specific existing category is preserved while generic defaults gain a suggestion`() {
        assertEquals("Family", MerchantCategories.resolveDefault("Amazon", "Family"))
        assertEquals("Shopping", MerchantCategories.resolveDefault("Amazon", "Uncategorized"))
        assertEquals("Shopping", MerchantCategories.resolveDefault("Amazon", "Misc"))
        assertEquals("Shopping", MerchantCategories.resolveDefault("Amazon", "Miscellaneous"))
        assertEquals("Uncategorized", MerchantCategories.resolveDefault("Ravi Kumar", "Uncategorized"))
        assertEquals("Uncategorized", MerchantCategories.resolveDefault(null, "Uncategorized"))
    }

    @Test
    fun `normalization handles case full width and punctuation without broad substrings`() {
        assertEquals("Food", MerchantCategories.suggest("ＳＷＩＧＧＹ\u00A0INDIA"))
        assertEquals("Fuel", MerchantCategories.suggest("INDIAN-OIL"))
        assertNull(MerchantCategories.suggest("indianoiler"))
    }

    @Test
    fun `category choices cover workbook labels and an honest unassigned option`() {
        assertTrue(MerchantCategories.categories.containsAll(listOf(
            "EMI", "Credit Card", "Salary", "Other Income", "Uncategorized", "Miscellaneous",
        )))
        assertEquals(MerchantCategories.categories.distinct(), MerchantCategories.categories)
    }
}
