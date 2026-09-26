package com.vaibhav.kharchapaani.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoriesTest {

    @Test
    fun `the statement importer's categories are ordinary choices now`() {
        val prefs = CategoryPrefs()
        assertTrue("Groceries" in Categories.expenseCategories(prefs))
        assertTrue("Investment" in Categories.expenseCategories(prefs))
        assertTrue("Income (Salary)" in Categories.incomeCategories(prefs))
    }

    @Test
    fun `your own categories sit before Other, which stays last`() {
        val prefs = CategoryPrefs(customExpense = listOf("Pets", "Gym"), customIncome = listOf("Rent received"))

        val expense = Categories.expenseCategories(prefs)
        assertEquals(listOf("Pets", "Gym", "Other"), expense.takeLast(3))
        assertEquals(listOf("Income (Salary)", "Income (Client)", "Rent received", "Income (Other)"), Categories.incomeCategories(prefs))
    }

    @Test
    fun `hidden defaults leave the pickers, but Other can't be hidden`() {
        val prefs = CategoryPrefs(hiddenCategories = setOf("Team Payment", "Other", "Income (Other)"), hiddenPaymentMethods = setOf("PopUPI", "Other"))

        assertFalse("Team Payment" in Categories.expenseCategories(prefs))
        assertTrue("Other" in Categories.expenseCategories(prefs))
        assertTrue("Income (Other)" in Categories.incomeCategories(prefs))
        assertFalse("PopUPI" in Categories.paymentMethods(prefs))
        assertTrue("Other" in Categories.paymentMethods(prefs))
    }

    @Test
    fun `a parsed category matches a choice whatever its case or spacing`() {
        val choices = Categories.expenseCategories(CategoryPrefs(customExpense = listOf("Pets")))

        assertEquals("Groceries", Categories.match(" groceries ", choices, Categories.OTHER))
        assertEquals("Team Payment", Categories.match("team  payment", choices, Categories.OTHER))
        assertEquals("Pets", Categories.match("PETS", choices, Categories.OTHER))
    }

    @Test
    fun `an unknown or missing category falls back instead of becoming a one-off`() {
        val expense = Categories.expenseCategories(CategoryPrefs())
        val income = Categories.incomeCategories(CategoryPrefs())

        assertEquals("Other", Categories.match("Crypto moonshots", expense, Categories.OTHER))
        assertEquals("Other", Categories.match(null, expense, Categories.OTHER))
        assertEquals("Income (Other)", Categories.match("Lottery", income, Categories.OTHER_INCOME))
        assertEquals("Income (Salary)", Categories.match("income (salary)", income, Categories.OTHER_INCOME))
    }

    @Test
    fun `a new name is tidied, and refused when blank, too long or already there`() {
        val existing = Categories.expenseCategories(CategoryPrefs(customExpense = listOf("Pets")))

        assertEquals("Kids school", Categories.cleanNewName("  Kids   school ", existing))
        assertNull(Categories.cleanNewName("   ", existing))
        assertNull(Categories.cleanNewName("x".repeat(25), existing))
        assertNull(Categories.cleanNewName("food", existing))
        assertNull(Categories.cleanNewName(" pets", existing))
    }

    @Test
    fun `new entries start on UPI and Food unless another default is chosen`() {
        assertEquals("UPI", Categories.defaultPaymentMethod(CategoryPrefs()))
        assertEquals("Food", Categories.defaultCategory("expense", CategoryPrefs()))
        val chosen = CategoryPrefs(defaultCategory = "Transport", defaultPaymentMethod = "Cash")
        assertEquals("Cash", Categories.defaultPaymentMethod(chosen))
        assertEquals("Transport", Categories.defaultCategory("expense", chosen))
    }

    @Test
    fun `a chosen default that is later hidden gives way to the first one still offered`() {
        val prefs = CategoryPrefs(defaultPaymentMethod = "Cash", hiddenPaymentMethods = setOf("Cash"), defaultCategory = "Transport", hiddenCategories = setOf("Transport"))
        assertEquals(Categories.paymentMethods(prefs).first(), Categories.defaultPaymentMethod(prefs))
        assertEquals(Categories.expenseCategories(prefs).first(), Categories.defaultCategory("expense", prefs))
    }

    @Test
    fun `income never starts on the spending default`() {
        assertEquals("Income (Client)", Categories.defaultCategory("income", CategoryPrefs(defaultCategory = "Transport")))
    }

    @Test
    fun `income categories are the income defaults and your own income ones`() {
        val prefs = CategoryPrefs(customIncome = listOf("Tutoring"), customExpense = listOf("Pets"))

        assertTrue(Categories.isIncomeCategory("Income (Client)", prefs))
        assertTrue(Categories.isIncomeCategory("Tutoring", prefs))
        assertFalse(Categories.isIncomeCategory("Pets", prefs))
        assertEquals(Categories.incomeCategories(prefs), Categories.categoriesFor("income", prefs))
    }
}
