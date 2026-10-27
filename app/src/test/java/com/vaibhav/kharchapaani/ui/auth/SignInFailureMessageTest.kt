package com.vaibhav.kharchapaani.ui.auth

import com.vaibhav.kharchapaani.data.auth.GoogleSignInFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Every way sign-in can end without signing in says something on screen.
 *
 * The login bug of 2026-09-23 was a tap that ended in silence: Google cancelled the picker on its
 * own and the app mapped "cancelled" to showing nothing. There is no quiet outcome any more.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SignInFailureMessageTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `every failure has its own message`() {
        val messages = GoogleSignInFailure.entries.map { it.message(context) }

        assertTrue(messages.all { it.isNotBlank() })
        assertEquals(messages.size, messages.toSet().size)
    }

    @Test
    fun `a blocked picker says to wait, not that something is broken`() {
        assertTrue("wait" in GoogleSignInFailure.PickerBlocked.message(context))
    }
}
