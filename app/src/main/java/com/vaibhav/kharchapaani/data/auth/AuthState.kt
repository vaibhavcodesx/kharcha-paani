package com.vaibhav.kharchapaani.data.auth

import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo

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
