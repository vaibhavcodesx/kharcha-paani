package com.vaibhav.kharchapaani.data.repository

import com.vaibhav.kharchapaani.domain.CategoryPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ParseChoicesTest {

    private val choices = ParseChoices.of(
        CategoryPrefs(customExpense = listOf("Pets"), hiddenCategories = setOf("Transport"), customPaymentMethods = listOf("Amex")),
    )

    @Test
    fun `the prompts offer this account's categories and methods, custom ones included, hidden ones not`() {
        assertTrue("Pets" in choices.categoryList.split(", "))
        assertTrue("Food" in choices.categoryList.split(", "))
        assertTrue("Income (Salary)" in choices.categoryList.split(", "))
        val promptCategories = choices.categoryList.split(", ")
        val promptMethods = choices.paymentList.split(", ")
        assertFalse("Transport" in promptCategories)
        assertFalse("Team Payment" in promptCategories)
        assertFalse("Income (Client)" in promptCategories)
        assertFalse("PopUPI" in promptMethods)
        assertTrue("Amex" in choices.paymentList.split(", "))
    }

    @Test
    fun `statement parsing keeps offered categories and handles unsupported categories`() {
        assertEquals("Other", choices.categoryFor("expense", "Team Payment"))
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
