package com.vaibhav.kharchapaani.data.auth

import io.github.jan.supabase.auth.status.RefreshFailureCause
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import kotlin.time.ExperimentalTime

/**
 * Opening the app with no internet. supabase-kt 3.6 refreshes an expired access token before it
 * reports anything, and while that refresh can't reach the server it reports [SessionStatus.Initializing]
 * and then [SessionStatus.RefreshFailure], neither of which carries the user. The app used to read the
 * user from supabase-kt's current session, which is empty in both, so it never opened the ledger and
 * sat on a blank screen until the network came back.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)
class OfflineLaunchAuthTest {

    private val user = UserInfo(aud = "authenticated", id = "user-1", email = "friend@example.com")
    private val pending = AuthState.SignedIn(userId = "user-1", email = "friend@example.com", isRefreshPending = true)
    private val online = AuthState.SignedIn(userId = "user-1", email = "friend@example.com", isRefreshPending = false)

    @Suppress("DEPRECATION")
    private val offline = SessionStatus.RefreshFailure(RefreshFailureCause.NetworkError(IOException("offline")))

    @Test
    fun `an expired session that can't be refreshed offline still opens the app as the stored user`() = runTest {
        val statuses = MutableStateFlow<SessionStatus>(SessionStatus.Initializing)
        val seen = collect(statuses, storedUser = user)

        statuses.value = offline
        runCurrent()

        assertEquals(pending, seen.last())
    }

    @Test
    fun `a refresh that hangs opens the app after a moment instead of a blank screen`() = runTest {
        // The last fifth of a token's life: supabase-kt refreshes early and, offline, reports
        // nothing until the token has actually expired, up to 12 minutes later.
        val statuses = MutableStateFlow<SessionStatus>(SessionStatus.Initializing)
        val seen = collect(statuses, storedUser = user)

        advanceTimeBy(INITIALIZING_GRACE_MS - 1)
        assertEquals(listOf<AuthState>(AuthState.Initializing), seen)

        advanceTimeBy(2)
        assertEquals(listOf(AuthState.Initializing, pending), seen)
    }

    @Test
    fun `nobody stored means initializing waits for supabase-kt to say signed out`() = runTest {
        val statuses = MutableStateFlow<SessionStatus>(SessionStatus.Initializing)
        val seen = collect(statuses, storedUser = null)

        advanceTimeBy(INITIALIZING_GRACE_MS * 10)
        assertEquals(listOf<AuthState>(AuthState.Initializing), seen)

        statuses.value = SessionStatus.NotAuthenticated(isSignOut = false)
        runCurrent()
        assertEquals(AuthState.SignedOut, seen.last())
    }

    @Test
    fun `online the stored session never shows as pending`() = runTest {
        val statuses = MutableStateFlow<SessionStatus>(SessionStatus.Initializing)
        val seen = collect(statuses, storedUser = user)

        advanceTimeBy(INITIALIZING_GRACE_MS / 2)
        statuses.value = SessionStatus.Authenticated(session())
        advanceTimeBy(INITIALIZING_GRACE_MS * 2)

        assertEquals(listOf(AuthState.Initializing, online), seen)
    }

    @Test
    fun `going to the background doesn't take the signed-in app away`() = runTest {
        // supabase-kt sets Initializing whenever the app is stopped (androidMain setupPlatform.kt).
        val statuses = MutableStateFlow<SessionStatus>(SessionStatus.Authenticated(session()))
        val seen = collect(statuses, storedUser = user)

        statuses.value = SessionStatus.Initializing
        advanceTimeBy(INITIALIZING_GRACE_MS * 2)

        assertEquals(listOf(online), seen)
    }

    @Test
    fun `a refresh failing while the app is open keeps the same account`() = runTest {
        val statuses = MutableStateFlow<SessionStatus>(SessionStatus.Authenticated(session()))
        val seen = collect(statuses, storedUser = user)

        statuses.value = offline
        runCurrent()
        statuses.value = SessionStatus.Authenticated(session())
        runCurrent()

        assertEquals(listOf(online, pending, online), seen)
    }

    @Test
    fun `a revoked session still signs out`() = runTest {
        val statuses = MutableStateFlow<SessionStatus>(SessionStatus.Initializing)
        val seen = collect(statuses, storedUser = user)

        statuses.value = offline
        runCurrent()
        statuses.value = SessionStatus.NotAuthenticated(isSignOut = false)
        runCurrent()

        assertEquals(AuthState.SignedOut, seen.last())
    }

    private fun kotlinx.coroutines.test.TestScope.collect(
        statuses: MutableStateFlow<SessionStatus>,
        storedUser: UserInfo?,
    ): List<AuthState> {
        val seen = mutableListOf<AuthState>()
        backgroundScope.launch {
            authStates(statuses, storedUser = { storedUser }).collect { seen += it }
        }
        runCurrent()
        return seen
    }

    private fun session() = UserSession(
        accessToken = "access",
        refreshToken = "refresh",
        expiresIn = 3600,
        tokenType = "bearer",
        user = user,
    )
}
