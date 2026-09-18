package com.vaibhav.kharchapaani.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileEditTest {

    @Test
    fun `a name is saved with its spaces tidied`() {
        assertEquals("Priya Sharma", ProfileEdit.cleanName("  Priya   Sharma "))
    }

    @Test
    fun `a blank name can't be saved`() {
        assertNull(ProfileEdit.cleanName("   "))
    }

    @Test
    fun `a name longer than 40 characters can't be saved, counting emoji as one`() {
        assertEquals("a".repeat(40), ProfileEdit.cleanName("a".repeat(40)))
        assertNull(ProfileEdit.cleanName("a".repeat(41)))
        assertEquals("🦊".repeat(40), ProfileEdit.cleanName("🦊".repeat(40)))
    }

    @Test
    fun `only offered emoji are kept`() {
        assertEquals("🦊", ProfileEdit.cleanEmoji("🦊"))
        assertNull(ProfileEdit.cleanEmoji("hello"))
        assertNull(ProfileEdit.cleanEmoji(null))
    }

    @Test
    fun `every offered emoji fits the server's limit`() {
        assertTrue(ProfileEdit.avatarEmoji.all { it.codePointCount(0, it.length) in 1..8 })
        assertEquals(ProfileEdit.avatarEmoji.size, ProfileEdit.avatarEmoji.toSet().size)
    }

    @Test
    fun `changes are what would actually be saved differently`() {
        assertFalse(ProfileEdit.hasChanges("Priya", null, " Priya ", null))
        assertTrue(ProfileEdit.hasChanges("Priya", null, "Priya S", null))
        assertTrue(ProfileEdit.hasChanges("Priya", null, "Priya", "🦊"))
        assertFalse(ProfileEdit.hasChanges("Priya", "🦊", "Priya", "🦊"))
    }
}
