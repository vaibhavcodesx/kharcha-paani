package com.vaibhav.kharchapaani.data.auth

sealed interface GoogleSignInResult {
    data object Success : GoogleSignInResult

    /** The user dismissed the account picker; not an error worth showing. */
    data object Cancelled : GoogleSignInResult

    data class Failure(val reason: GoogleSignInFailure) : GoogleSignInResult
}

enum class GoogleSignInFailure {
    /** GOOGLE_WEB_CLIENT_ID is missing from .env, so the app can't ask Google for a token. */
    NotConfigured,

    NoGoogleAccount,

    /**
     * Credential Manager couldn't produce a Google ID token: Play services unavailable, or the
     * Android OAuth client (package name + SHA-1) isn't registered for this build.
     */
    GoogleUnavailable,

    Network,

    /**
     * Google issued a token but Supabase refused it, e.g. the Web client ID isn't listed under
     * the Supabase Google provider's Client IDs.
     */
    Rejected,

    /**
     * Google cancelled the request without showing anything, twice: the bottom sheet and then
     * the button flow. Seen after the picker has been dismissed a few times in a row.
     */
    PickerBlocked,

    /**
     * Google refused to sign in to this copy of the app after an account was picked ("Account
     * reauth failed"): usually its signing certificate isn't registered with the OAuth client.
     * Nothing the person can fix, so it says so rather than implying they cancelled.
     */
    AppNotAccepted,

    /**
     * The person closed the picker themselves. Not an error, but still said out loud: a tap
     * that ends with the screen unchanged reads as a broken button.
     */
    Dismissed,
}
