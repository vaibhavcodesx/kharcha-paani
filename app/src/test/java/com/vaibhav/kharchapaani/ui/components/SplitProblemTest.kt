package com.vaibhav.kharchapaani.ui.components

import com.vaibhav.kharchapaani.data.model.ShareInput
import com.vaibhav.kharchapaani.data.model.SplitMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SplitProblemTest {

    private fun share(id: Long, amount: Double = 0.0, pct: Double = 0.0, selected: Boolean = true) =
        ShareInput(personId = id, personName = "p$id", amount = amount, percentage = pct, isSelected = selected)

    @Test
    fun `exact shares that add up are fine, to the paisa`() {
        assertNull(splitProblem("100", SplitMode.EXACT, listOf(share(-1, 33.33), share(2, 33.33), share(3, 33.34))))
    }

    @Test
    fun `exact shares short of the total or over it say by how much`() {
        assertEquals("₹200 still to assign", splitProblem("1000", SplitMode.EXACT, listOf(share(-1, 300.0), share(2, 500.0))))
        assertEquals("₹50 more than the total", splitProblem("100", SplitMode.EXACT, listOf(share(-1, 100.0), share(2, 50.0))))
    }

    @Test
    fun `an unticked person's amount doesn't count`() {
        assertNull(splitProblem("100", SplitMode.EXACT, listOf(share(-1, 100.0), share(2, 999.0, selected = false))))
    }

    @Test
    fun `percentages must reach a hundred`() {
        assertEquals("Percentages add up to 90.0%, not 100%", splitProblem("100", SplitMode.PERCENTAGE, listOf(share(-1, pct = 50.0), share(2, pct = 40.0))))
        assertNull(splitProblem("100", SplitMode.PERCENTAGE, listOf(share(-1, pct = 33.3), share(2, pct = 33.3), share(3, pct = 33.3))))
    }

    @Test
    fun `a split with nobody in it is not a split`() {
        // Saved, this became a plain full-amount expense with the split quietly dropped.
        assertEquals("Pick who's in the split", splitProblem("100", SplitMode.EQUAL, listOf(share(-1, selected = false), share(2, selected = false))))
    }
}
