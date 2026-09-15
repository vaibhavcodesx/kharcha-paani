package com.vaibhav.kharchapaani.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class ClearOfBarSpecTest {

    // A 1000px sheet body with a 200px floating bar over its bottom: 800px are really visible.
    private val spec = ClearOfBarSpec(barHeightPx = 200f)

    @Test
    fun `something already clear of the bar doesn't scroll`() {
        assertEquals(0f, spec.calculateScrollDistance(offset = 100f, size = 100f, containerSize = 1000f))
    }

    @Test
    fun `something behind the bar scrolls up until it clears the bar, not just the screen edge`() {
        // It spans 850..950: fully on screen, but under the bar. The default would scroll 0.
        assertEquals(150f, spec.calculateScrollDistance(offset = 850f, size = 100f, containerSize = 1000f))
    }

    @Test
    fun `something above the top scrolls down to it`() {
        assertEquals(-40f, spec.calculateScrollDistance(offset = -40f, size = 100f, containerSize = 1000f))
    }

    @Test
    fun `something taller than the visible area aligns to the top`() {
        assertEquals(300f, spec.calculateScrollDistance(offset = 300f, size = 900f, containerSize = 1000f))
    }
}
