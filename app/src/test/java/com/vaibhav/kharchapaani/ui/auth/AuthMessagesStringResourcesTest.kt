package com.vaibhav.kharchapaani.ui.auth

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.vaibhav.kharchapaani.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AuthMessagesStringResourcesTest {

    private val context: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun `auth hue error strings resolve the exact expected English text`() {
        assertEquals(
            "You're offline, so your colour wasn't saved. Try again when you're connected.",
            context.getString(R.string.auth_hue_error_offline),
        )
        assertEquals(
            "Someone in your groups has a colour too close to that one. Pick one further away.",
            context.getString(R.string.auth_hue_error_conflict),
        )
    }

    @Test
    fun `auth delete account error strings resolve the exact expected English text`() {
        assertEquals(
            "You're offline. Connect and try again; nothing was deleted.",
            context.getString(R.string.auth_delete_account_error_offline),
        )
        assertEquals(
            "Couldn't delete your account just now. Nothing was deleted; try again in a minute.",
            context.getString(R.string.auth_delete_account_error_server),
        )
    }
}
