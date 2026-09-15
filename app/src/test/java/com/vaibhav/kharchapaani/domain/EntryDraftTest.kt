package com.vaibhav.kharchapaani.domain

import com.vaibhav.kharchapaani.data.model.ShareInput
import com.vaibhav.kharchapaani.data.model.SplitMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EntryDraftTest {

    private val blank = EntryDraft(
        type = "expense", amount = "", merchant = "", category = "Food", paymentMethod = "PopUPI", note = "",
        date = 1_000L, groupId = null, isSplit = false, splitMode = SplitMode.EQUAL, paidByPersonId = -1L,
        shares = listOf(ShareInput(-1L, "You"), ShareInput(2L, "Priya", isSelected = false)),
    )

    @Test
    fun `a new entry with nothing typed can close without asking`() {
        assertFalse(EntryDraft.isUnsaved(blank, original = null))
        assertFalse(EntryDraft.isUnsaved(blank.copy(type = "income", category = "Income (Client)"), original = null))
    }

    @Test
    fun `a new entry with an amount, name or note would lose something`() {
        assertTrue(EntryDraft.isUnsaved(blank.copy(amount = "250"), original = null))
        assertTrue(EntryDraft.isUnsaved(blank.copy(merchant = "Chai Point"), original = null))
        assertTrue(EntryDraft.isUnsaved(blank.copy(note = "office"), original = null))
    }

    @Test
    fun `whitespace alone isn't input`() {
        assertFalse(EntryDraft.isUnsaved(blank.copy(merchant = "  ", note = " "), original = null))
    }

    @Test
    fun `an untouched edit can close without asking`() {
        val original = blank.copy(amount = "34.5", merchant = "Idli")
        assertFalse(EntryDraft.isUnsaved(original, original))
        // The same amount written differently isn't a change.
        assertFalse(EntryDraft.isUnsaved(original.copy(amount = "34.50"), original))
    }

    @Test
    fun `any changed field makes an edit unsaved`() {
        val original = blank.copy(amount = "34.5", merchant = "Idli")
        assertTrue(EntryDraft.isUnsaved(original.copy(amount = "35"), original))
        assertTrue(EntryDraft.isUnsaved(original.copy(category = "Shopping"), original))
        assertTrue(EntryDraft.isUnsaved(original.copy(date = 2_000L), original))
        assertTrue(EntryDraft.isUnsaved(original.copy(isSplit = true), original))
    }

    @Test
    fun `equal split amounts recalculated for the same people aren't a change`() {
        val original = blank.copy(
            amount = "300", isSplit = true,
            shares = listOf(ShareInput(-1L, "You", amount = 150.0), ShareInput(2L, "Priya", amount = 150.0)),
        )
        val recalculated = original.copy(shares = listOf(ShareInput(-1L, "You", amount = 150.004), ShareInput(2L, "Priya", amount = 149.996)))
        assertFalse(EntryDraft.isUnsaved(recalculated, original))

        val priyaOut = original.copy(shares = listOf(ShareInput(-1L, "You", amount = 300.0), ShareInput(2L, "Priya", isSelected = false)))
        assertTrue(EntryDraft.isUnsaved(priyaOut, original))
    }

    @Test
    fun `a changed exact split amount is a change`() {
        val original = blank.copy(
            amount = "300", isSplit = true, splitMode = SplitMode.EXACT,
            shares = listOf(ShareInput(-1L, "You", amount = 100.0), ShareInput(2L, "Priya", amount = 200.0)),
        )
        val changed = original.copy(shares = listOf(ShareInput(-1L, "You", amount = 120.0), ShareInput(2L, "Priya", amount = 180.0)))
        assertTrue(EntryDraft.isUnsaved(changed, original))
    }
}
