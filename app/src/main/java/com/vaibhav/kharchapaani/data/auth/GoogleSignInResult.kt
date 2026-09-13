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
}
