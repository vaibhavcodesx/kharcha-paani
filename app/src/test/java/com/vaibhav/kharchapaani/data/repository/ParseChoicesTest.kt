package com.vaibhav.kharchapaani.data.repository

import com.vaibhav.kharchapaani.domain.CategoryPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ParseChoicesTest {

    private val choices = ParseChoices.of(
        CategoryPrefs(customExpense = listOf("Pets"), hiddenCategories = setOf("Team Payment"), customPaymentMethods = listOf("Amex")),
    )

    @Test
    fun `the prompts offer this account's categories and methods, custom ones included, hidden ones not`() {
        assertTrue("Pets" in choices.categoryList.split(", "))
        assertTrue("Groceries" in choices.categoryList.split(", "))
        assertTrue("Income (Salary)" in choices.categoryList.split(", "))
        assertFalse("Team Payment" in choices.categoryList.split(", "))
        assertTrue("Amex" in choices.paymentList.split(", "))
    }

    @Test
    fun `a statement's groceries, investment and salary rows keep those categories`() {
        assertEquals("Groceries", choices.categoryFor("expense", "Groceries"))
        assertEquals("Investment", choices.categoryFor("expense", "investment"))
        assertEquals("Income (Salary)", choices.categoryFor("income", "Income (Salary)"))
    }

    @Test
    fun `a category from the wrong side, or unknown, lands on that side's Other`() {
        assertEquals("Other", choices.categoryFor("expense", "Income (Salary)"))
        assertEquals("Income (Other)", choices.categoryFor("income", "Food"))
        assertEquals("Other", choices.categoryFor("expense", "Crypto"))
        assertEquals("Pets", choices.categoryFor("expense", "pets"))
    }

    @Test
    fun `a payment method is spelled this account's way when known, and kept as read otherwise`() {
        assertEquals("GPay", choices.paymentFor("gpay"))
        assertEquals("NEFT", choices.paymentFor(" NEFT "))
        assertEquals("Other", choices.paymentFor(""))
    }
}
