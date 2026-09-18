package com.vaibhav.kharchapaani.ui.auth

import com.vaibhav.kharchapaani.data.auth.AuthState
import com.vaibhav.kharchapaani.data.auth.Profile
import org.junit.Assert.assertEquals
import org.junit.Test

class AuthUiStateTest {

    private val signedIn = AuthState.SignedIn(userId = "u1", email = "vaibhav@example.com", isRefreshPending = false)

    @Test
    fun `account initial prefers the profile display name`() {
        val state = AuthUiState(authState = signedIn, profile = ProfileState.Loaded(Profile(id = "u1", displayName = "rahul")))

        assertEquals("R", state.accountAvatar)
    }

    @Test
    fun `blank display name falls back to the email`() {
        val state = AuthUiState(authState = signedIn, profile = ProfileState.Loaded(Profile(id = "u1", displayName = " ")))

        assertEquals(null, state.displayName)
        assertEquals("V", state.accountAvatar)
    }

    @Test
    fun `a chosen emoji replaces the initial`() {
        val state = AuthUiState(authState = signedIn, profile = ProfileState.Loaded(Profile(id = "u1", displayName = "Rahul", avatarEmoji = "🦊")))

        assertEquals("🦊", state.accountAvatar)
    }

    @Test
    fun `no name or email yet shows a placeholder initial`() {
        assertEquals("?", AuthUiState().accountAvatar)
    }
}
