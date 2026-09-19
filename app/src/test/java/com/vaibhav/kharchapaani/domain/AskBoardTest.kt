package com.vaibhav.kharchapaani.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The reported bug: "when one of the three tabs errors, all three tabs show the same error".
 *
 * They were three independent requests sharing one answer string and one `failed` flag, so the
 * result on screen was whichever finished last, whatever chip you were looking at. These are the
 * cases that were wrong.
 */
class AskBoardTest {

    private val month = AiPrompt.MonthInReview
    private val over = AiPrompt.Overspending
    private val change = AiPrompt.WhatToChange

    @Test
    fun `one question failing leaves the others exactly as they were`() {
        val board = AskBoard()
            .asking(month).answered(month, "August was steady.")
            .asking(change).failed(change)

        assertEquals(AskState.Answered("August was steady."), board.stateOf(month))
        assertEquals(AskState.Failed, board.stateOf(change))
        assertEquals(AskState.Idle, board.stateOf(over))
    }

    @Test
    fun `going back to a failed question and then a working one shows the working one's answer`() {
        // The whole bug, in one sequence: ask A (works), ask C (fails), tap back to A.
        val board = AskBoard()
            .asking(month).answered(month, "August was steady.")
            .asking(change).failed(change)
            .select(month)

        assertEquals(AskState.Answered("August was steady."), board.selectedState)
    }

    @Test
    fun `an answer already in hand is not asked for again`() {
        val board = AskBoard().asking(month).answered(month, "August was steady.")

        assertFalse(board.needsAsking(month))
        assertTrue(board.needsAsking(over))
    }

    @Test
    fun `a failed question is asked again when you tap it`() {
        assertTrue(AskBoard().asking(month).failed(month).needsAsking(month))
    }

    @Test
    fun `asking selects the question being asked, so the chip matches the spinner`() {
        assertEquals(over, AskBoard().asking(month).asking(over).selected)
    }

    @Test
    fun `selecting shows a question without starting it`() {
        val board = AskBoard().asking(month).answered(month, "x").select(over)

        assertEquals(over, board.selected)
        assertEquals(AskState.Idle, board.selectedState)
        assertFalse(board.isAsking)
    }

    @Test
    fun `the board knows when anything is in flight`() {
        assertFalse(AskBoard().isAsking)
        assertTrue(AskBoard().asking(month).isAsking)
        assertFalse(AskBoard().asking(month).answered(month, "x").isAsking)
        assertFalse(AskBoard().asking(month).failed(month).isAsking)
    }

    @Test
    fun `an answer that arrives for a question you are no longer looking at is still kept`() {
        // It was paid for and it is about this month; it belongs under its own chip.
        val board = AskBoard().asking(month).select(over).answered(month, "August was steady.")

        assertEquals(over, board.selected)
        assertEquals(AskState.Answered("August was steady."), board.stateOf(month))
    }

    @Test
    fun `a new month starts every question over`() {
        // An answer about August under a September chip would be worse than no answer.
        val board = AskBoard().asking(month).answered(month, "August was steady.").cleared()

        assertEquals(AskState.Idle, board.stateOf(month))
        assertEquals(null, board.selected)
    }
}
