package com.vaibhav.kharchapaani.ui.auth

import com.vaibhav.kharchapaani.data.auth.AuthState
import com.vaibhav.kharchapaani.data.auth.GoogleSignInFailure
import com.vaibhav.kharchapaani.data.auth.Profile

data class AuthUiState(
    val authState: AuthState = AuthState.Initializing,
    val isSigningIn: Boolean = false,
    val signInFailure: GoogleSignInFailure? = null,
    val profile: ProfileState = ProfileState.Idle,
) {
    val displayName: String?
        get() = (profile as? ProfileState.Loaded)?.profile?.displayName?.takeIf { it.isNotBlank() }

    val email: String?
        get() = (authState as? AuthState.SignedIn)?.email

    val accountInitial: String
        get() = (displayName ?: email)?.firstOrNull()?.uppercase() ?: "?"
}

sealed interface ProfileState {
    data object Idle : ProfileState
    data object Loading : ProfileState
    data class Loaded(val profile: Profile) : ProfileState

    /** Signed in, but no `profiles` row: the on_auth_user_created trigger didn't run. */
    data object Missing : ProfileState

    /** Couldn't reach Supabase; the local ledger still works. */
    data object Unavailable : ProfileState
}
