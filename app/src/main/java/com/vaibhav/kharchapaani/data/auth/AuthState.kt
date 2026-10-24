package com.vaibhav.kharchapaani.data.auth

import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.transformLatest

sealed interface AuthState {

    /** The stored session hasn't been loaded yet; don't show sign-in or the app. */
    data object Initializing : AuthState

    data object SignedOut : AuthState

    /**
     * @param isRefreshPending the access token expired and couldn't be refreshed (usually no
     * network). The user stays signed in so the offline-first app keeps working; supabase-kt
     * keeps retrying in the background.
     */
    data class SignedIn(
        val userId: String?,
        val email: String?,
        val isRefreshPending: Boolean,
    ) : AuthState
}

/** How long a stored session may sit in [SessionStatus.Initializing] before the app opens anyway. */
const val INITIALIZING_GRACE_MS = 1_500L

/**
 * supabase-kt's [statuses] as the app sees them. [storedUser] is who the saved session belongs to.
 *
 * Opening the app offline must still open the ledger. supabase-kt 3.6 refreshes an expired access
 * token before it reports anything; offline it reports [SessionStatus.Initializing] until the token
 * has actually expired (up to 12 minutes, it refreshes at 80% of the hour) and then
 * [SessionStatus.RefreshFailure], and neither carries the user. So the user comes from the saved
 * session instead, and a stored session that's still initialising after [INITIALIZING_GRACE_MS]
 * opens as signed in with the refresh pending. A revoked refresh token still signs out: supabase-kt
 * reports that as [SessionStatus.NotAuthenticated].
 *
 * supabase-kt also goes back to Initializing every time the app is stopped; once signed in, that's
 * ignored so the app isn't swapped for a blank screen on the way back.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun authStates(
    statuses: Flow<SessionStatus>,
    storedUser: suspend () -> UserInfo?,
): Flow<AuthState> = flow {
    var signedIn: AuthState.SignedIn? = null
    val states = statuses.transformLatest { status ->
        when (status) {
            SessionStatus.Initializing -> {
                signedIn?.let { emit(it); return@transformLatest }
                emit(AuthState.Initializing)
                delay(INITIALIZING_GRACE_MS)
                storedUser()?.let { emit(AuthState.SignedIn(it.id, it.email, isRefreshPending = true)) }
            }
            is SessionStatus.Authenticated -> emit(status.toAuthState(status.session.user ?: storedUser()))
            is SessionStatus.RefreshFailure -> {
                val held = signedIn
                val user = storedUser()
                emit(
                    AuthState.SignedIn(
                        userId = user?.id ?: held?.userId,
                        email = user?.email ?: held?.email,
                        isRefreshPending = true,
                    )
                )
            }
            is SessionStatus.NotAuthenticated -> emit(AuthState.SignedOut)
        }
    }
    emitAll(states.onEach { signedIn = it as? AuthState.SignedIn })
}.distinctUntilChanged()

/**
 * [SessionStatus.RefreshFailure] carries no session, so the caller passes in the user it still
 * holds. supabase-kt only reports RefreshFailure for network/5xx errors; a revoked refresh token
 * clears the session and arrives as [SessionStatus.NotAuthenticated] instead.
 */
fun SessionStatus.toAuthState(currentUser: UserInfo?): AuthState = when (this) {
    SessionStatus.Initializing -> AuthState.Initializing
    is SessionStatus.NotAuthenticated -> AuthState.SignedOut
    is SessionStatus.Authenticated -> {
        val user = session.user ?: currentUser
        AuthState.SignedIn(userId = user?.id, email = user?.email, isRefreshPending = false)
    }
    is SessionStatus.RefreshFailure ->
        AuthState.SignedIn(userId = currentUser?.id, email = currentUser?.email, isRefreshPending = true)
}
