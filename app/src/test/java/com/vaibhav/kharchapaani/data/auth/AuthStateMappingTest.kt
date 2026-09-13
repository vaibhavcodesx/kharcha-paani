package com.vaibhav.kharchapaani.data.auth

import io.github.jan.supabase.auth.status.RefreshFailureCause
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.auth.user.UserSession
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import kotlin.time.ExperimentalTime

// UserSession's expiresAt is a kotlin.time.Instant, still experimental on Kotlin 2.2.
@OptIn(ExperimentalTime::class)
class AuthStateMappingTest {

    private val user = UserInfo(aud = "authenticated", id = "user-1", email = "friend@example.com")

    @Test
    fun `initializing stays initializing so neither sign-in nor the app flashes`() {
        assertEquals(AuthState.Initializing, SessionStatus.Initializing.toAuthState(currentUser = null))
    }

    @Test
    fun `not authenticated maps to signed out`() {
        assertEquals(
            AuthState.SignedOut,
            SessionStatus.NotAuthenticated(isSignOut = true).toAuthState(currentUser = user),
        )
    }

    @Test
    fun `authenticated takes the user from the session`() {
        val status = SessionStatus.Authenticated(session(user = user))

        assertEquals(
            AuthState.SignedIn(userId = "user-1", email = "friend@example.com", isRefreshPending = false),
            status.toAuthState(currentUser = null),
        )
    }

    @Test
    fun `authenticated falls back to the current user when the session has none`() {
        val status = SessionStatus.Authenticated(session(user = null))

        assertEquals(
            AuthState.SignedIn(userId = "user-1", email = "friend@example.com", isRefreshPending = false),
            status.toAuthState(currentUser = user),
        )
    }

    @Test
    fun `refresh failure while offline keeps the user signed in`() {
        @Suppress("DEPRECATION")
        val status = SessionStatus.RefreshFailure(RefreshFailureCause.NetworkError(IOException("offline")))

        assertEquals(
            AuthState.SignedIn(userId = "user-1", email = "friend@example.com", isRefreshPending = true),
            status.toAuthState(currentUser = user),
        )
    }

    private fun session(user: UserInfo?) = UserSession(
        accessToken = "access",
        refreshToken = "refresh",
        expiresIn = 3600,
        tokenType = "bearer",
        user = user,
    )
}
