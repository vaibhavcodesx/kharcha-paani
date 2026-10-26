package com.vaibhav.kharchapaani.domain

import kotlinx.serialization.Serializable

/**
 * This account's choices of category and payment method: the defaults, minus any it has hidden, plus
 * its own. Kept on this phone. An expense stores its category as plain text, so a custom category on a
 * group expense still shows by name on groupmates' phones without anyone's list being synced
 * (backlog item 7, approved 2026-09-15).
 */
@Serializable
data class CategoryPrefs(
    val customExpense: List<String> = emptyList(),
    val customIncome: List<String> = emptyList(),
    val hiddenCategories: Set<String> = emptySet(),
    val customPaymentMethods: List<String> = emptyList(),
    val hiddenPaymentMethods: Set<String> = emptySet(),
    /** What a new expense starts on. Null is [Categories.DEFAULT_CATEGORY]. */
    val defaultCategory: String? = null,
    /** What a new entry is paid with. Null is [Categories.DEFAULT_PAYMENT_METHOD]. */
    val defaultPaymentMethod: String? = null,
)

object Categories {

    const val OTHER = "Other"
    const val OTHER_INCOME = "Income (Other)"
    const val MAX_NAME_LENGTH = 24

    /**
     * One list for every picker, the AI prompts and the statement importer. The importer used to offer
     * Groceries, Investment and Income (Salary), which the rest of the app didn't know, so an imported
     * grocery run showed with no icon and couldn't be picked again when edited.
     */
    val defaultExpense = listOf(
        "Food", "Beverages", "Groceries", "Packaged goods", "Transport", "Shopping", "Self-care",
        "Bills", "Entertainment", "Health", "Rent", "Investment", "Team Payment", OTHER,
    )
    val defaultIncome = listOf("Income (Salary)", "Income (Client)", OTHER_INCOME)

    /**
     * Plain "UPI" first: most payments are UPI whichever app sends them, and the list started with one
     * app's name, which new entries then defaulted to. Entries already logged keep theirs.
     */
    val defaultPaymentMethods = listOf("UPI", "PopUPI", "GPay", "PhonePe", "Paytm", "Debit Card", "Credit Card", "Cash", "Bank Transfer", OTHER)

    const val DEFAULT_CATEGORY = "Food"
    const val DEFAULT_PAYMENT_METHOD = "UPI"
    private const val DEFAULT_INCOME_CATEGORY = "Income (Client)"

    /**
     * The category a new entry starts on: the one chosen in settings, or [DEFAULT_CATEGORY] — unless
     * it has since been hidden or removed, when the first one still offered stands in. An income
     * entry starts on the usual income category, since the spending default can't be one.
     */
    fun defaultCategory(type: String, prefs: CategoryPrefs): String {
        val choices = categoriesFor(type, prefs)
        val preferred = if (type == "income") DEFAULT_INCOME_CATEGORY else prefs.defaultCategory ?: DEFAULT_CATEGORY
        return preferred.takeIf { it in choices } ?: choices.firstOrNull() ?: preferred
    }

    /** The payment method a new entry starts on, with the same fallback as [defaultCategory]. */
    fun defaultPaymentMethod(prefs: CategoryPrefs): String {
        val choices = paymentMethods(prefs)
        val preferred = prefs.defaultPaymentMethod ?: DEFAULT_PAYMENT_METHOD
        return preferred.takeIf { it in choices } ?: choices.firstOrNull() ?: preferred
    }

    /** "Other" is where anything unrecognised lands, so it can't be hidden. */
    private val alwaysShown = setOf(OTHER, OTHER_INCOME)

    fun expenseCategories(prefs: CategoryPrefs): List<String> =
        defaultExpense.filterNot { it in prefs.hiddenCategories && it !in alwaysShown }.let { withCustom(it, prefs.customExpense) }

    fun incomeCategories(prefs: CategoryPrefs): List<String> =
        defaultIncome.filterNot { it in prefs.hiddenCategories && it !in alwaysShown }.let { withCustom(it, prefs.customIncome) }

    fun paymentMethods(prefs: CategoryPrefs): List<String> =
        defaultPaymentMethods.filterNot { it in prefs.hiddenPaymentMethods && it != OTHER }.let { withCustom(it, prefs.customPaymentMethods) }

    fun categoriesFor(type: String, prefs: CategoryPrefs): List<String> =
        if (type == "income") incomeCategories(prefs) else expenseCategories(prefs)

    fun isIncomeCategory(name: String, prefs: CategoryPrefs): Boolean =
        name in defaultIncome || name in prefs.customIncome

    /** Custom ones go just before "Other", which stays last. */
    private fun withCustom(shown: List<String>, custom: List<String>): List<String> {
        val other = shown.filter { it in alwaysShown }
        return shown.filterNot { it in alwaysShown } + custom + other
    }

    /**
     * What a parser (the AI, the statement reader) came back with, matched to a choice regardless of
     * case or spacing; anything else becomes [fallback] rather than an unpickable one-off.
     */
    fun match(raw: String?, choices: List<String>, fallback: String): String {
        val key = raw?.normalizedKey() ?: return fallback
        return choices.firstOrNull { it.normalizedKey() == key } ?: fallback
    }

    /** A new name as it would be saved, or null when it's blank, too long, or already a choice. */
    fun cleanNewName(raw: String, existing: Collection<String>): String? {
        val tidy = raw.trim().replace(Regex("\\s+"), " ")
        if (tidy.isEmpty() || tidy.codePointCount(0, tidy.length) > MAX_NAME_LENGTH) return null
        return tidy.takeUnless { name -> existing.any { it.normalizedKey() == name.normalizedKey() } }
    }

    private fun String.normalizedKey() = trim().replace(Regex("\\s+"), " ").lowercase()
}
