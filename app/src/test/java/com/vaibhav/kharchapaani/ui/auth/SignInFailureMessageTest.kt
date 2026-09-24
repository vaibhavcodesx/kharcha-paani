package com.vaibhav.kharchapaani.ui.auth

import com.vaibhav.kharchapaani.data.auth.GoogleSignInFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every way sign-in can end without signing in says something on screen.
 *
 * The login bug of 2026-09-23 was a tap that ended in silence: Google cancelled the picker on its
 * own and the app mapped "cancelled" to showing nothing. There is no quiet outcome any more.
 */
class SignInFailureMessageTest {

    @Test
    fun `every failure has its own message`() {
        val messages = GoogleSignInFailure.entries.map { it.message() }

        assertTrue(messages.all { it.isNotBlank() })
        assertEquals(messages.size, messages.toSet().size)
    }

    @Test
    fun `a blocked picker says to wait, not that something is broken`() {
        assertTrue("wait" in GoogleSignInFailure.PickerBlocked.message())
    }
}
