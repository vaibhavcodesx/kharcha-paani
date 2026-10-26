package com.vaibhav.kharchapaani.ui.auth

import com.vaibhav.kharchapaani.data.auth.AuthState
import com.vaibhav.kharchapaani.data.auth.GoogleSignInFailure
import com.vaibhav.kharchapaani.data.auth.Profile

data class AuthUiState(
    val authState: AuthState = AuthState.Initializing,
    val isSigningIn: Boolean = false,
    val signInFailure: GoogleSignInFailure? = null,
    val profile: ProfileState = ProfileState.Idle,
    val isSavingProfile: Boolean = false,
    val profileSaveFailed: Boolean = false,
    /** Why the last colour couldn't be saved, in plain words; null when it saved or nothing's tried. */
    val hueSaveError: String? = null,
    /** True from the second confirm of "Delete my account" until it's done or has failed. */
    val isDeletingAccount: Boolean = false,
    /** Why deleting the account didn't happen, in plain words; null otherwise. */
    val deleteAccountError: String? = null,
) {
    /** This account's own colour (design item 11), or null when it hasn't got one yet. */
    val hue: Int?
        get() = (profile as? ProfileState.Loaded)?.profile?.hue

    val displayName: String?
        get() = (profile as? ProfileState.Loaded)?.profile?.displayName?.takeIf { it.isNotBlank() }

    val email: String?
        get() = (authState as? AuthState.SignedIn)?.email

    val avatarEmoji: String?
        get() = (profile as? ProfileState.Loaded)?.profile?.avatarEmoji

    /** The emoji this account picked, or the first letter of its name (or email). */
    val accountAvatar: String
        get() = avatarEmoji ?: (displayName ?: email)?.firstOrNull()?.uppercase() ?: "?"
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
