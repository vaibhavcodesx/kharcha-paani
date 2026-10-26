package com.vaibhav.kharchapaani.data.auth

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.ClearCredentialException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class AuthRepository(
    private val supabase: SupabaseClient,
    private val googleWebClientId: String,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val auth get() = supabase.auth

    val authState: Flow<AuthState> = authStates(
        auth.sessionStatus,
        storedUser = { withContext(Dispatchers.IO) { auth.sessionManager.loadSessionOrNull()?.user } },
    )

    /** [activityContext] must be an Activity: Credential Manager anchors its account picker to it. */
    suspend fun signInWithGoogle(activityContext: Context): GoogleSignInResult {
        if (!isUsableWebClientId(googleWebClientId)) {
            return GoogleSignInResult.Failure(GoogleSignInFailure.NotConfigured)
        }
        val nonce = GoogleNonce.generate()

        val idToken = when (val token = requestGoogleIdToken(activityContext, nonce)) {
            is GoogleTokenResult.Token -> token.idToken
            is GoogleTokenResult.Done -> return token.result
        }

        return try {
            auth.signInWith(IDToken) {
                this.idToken = idToken
                provider = Google
                this.nonce = nonce.raw
            }
            GoogleSignInResult.Success
        } catch (e: RestException) {
            Log.w(TAG, "Supabase rejected the Google ID token: ${e.error}")
            GoogleSignInResult.Failure(GoogleSignInFailure.Rejected)
        } catch (e: IOException) {
            GoogleSignInResult.Failure(GoogleSignInFailure.Network)
        }
    }

    private suspend fun requestGoogleIdToken(
        activityContext: Context,
        nonce: GoogleNonce,
    ): GoogleTokenResult {
        val credentialManager = CredentialManager.create(activityContext)
        suspend fun ask(request: GetCredentialRequest): Credential =
            credentialManager.getCredential(activityContext, request).credential

        val started = clock()
        val credential = try {
            ask(accountPickerRequest(nonce))
        } catch (e: NoCredentialException) {
            // No Google account on the device yet. The Sign in with Google flow can add one,
            // which the account picker above can't.
            return askButtonFlow(::ask, nonce, hadNoAccount = true)
        } catch (e: GetCredentialCancellationException) {
            // Every cancel is logged: Google reports misconfiguration and its own cooldown the same
            // way as a person closing the picker, and the message and timing are the only clues.
            Log.w(TAG, "Account picker cancelled after ${clock() - started} ms (${e.type}): ${e.message}")
            if (PickerCancellation.wasRefusedByGoogle(e.message)) {
                return GoogleTokenResult.Done(GoogleSignInResult.Failure(GoogleSignInFailure.AppNotAccepted))
            }
            if (!PickerCancellation.wasSilent(clock() - started, e.message)) {
                return GoogleTokenResult.Done(GoogleSignInResult.Cancelled)
            }
            // Nobody saw a picker: Google is holding the bottom sheet back after earlier
            // dismissals. The button flow is Google's own dialog and isn't under that cooldown.
            Log.w(TAG, "Account picker cancelled without being shown (${e.message}); trying the button flow")
            return askButtonFlow(::ask, nonce, hadNoAccount = false)
        } catch (e: GetCredentialException) {
            return GoogleTokenResult.Done(mapCredentialFailure(e, hadNoAccount = false))
        }

        return tokenFrom(credential)
    }

    private fun tokenFrom(credential: Credential): GoogleTokenResult =
        extractIdToken(credential)
            ?.let { GoogleTokenResult.Token(it) }
            ?: GoogleTokenResult.Done(GoogleSignInResult.Failure(GoogleSignInFailure.GoogleUnavailable))

    private suspend fun askButtonFlow(
        ask: suspend (GetCredentialRequest) -> Credential,
        nonce: GoogleNonce,
        hadNoAccount: Boolean,
    ): GoogleTokenResult {
        val started = clock()
        return try {
            tokenFrom(ask(addAccountRequest(nonce)))
        } catch (e: GetCredentialCancellationException) {
            Log.w(TAG, "Sign in with Google dialog cancelled after ${clock() - started} ms (${e.type}): ${e.message}")
            if (PickerCancellation.wasRefusedByGoogle(e.message)) {
                return GoogleTokenResult.Done(GoogleSignInResult.Failure(GoogleSignInFailure.AppNotAccepted))
            }
            val silent = PickerCancellation.wasSilent(clock() - started, e.message)
            if (silent) Log.w(TAG, "Sign in with Google dialog also cancelled unseen: ${e.message}")
            GoogleTokenResult.Done(
                if (silent) GoogleSignInResult.Failure(GoogleSignInFailure.PickerBlocked)
                else GoogleSignInResult.Cancelled
            )
        } catch (e: GetCredentialException) {
            GoogleTokenResult.Done(mapCredentialFailure(e, hadNoAccount))
        }
    }

    private fun accountPickerRequest(nonce: GoogleNonce) = GetCredentialRequest.Builder()
        .addCredentialOption(
            GetGoogleIdOption.Builder()
                .setServerClientId(googleWebClientId)
                .setFilterByAuthorizedAccounts(false)
                .setNonce(nonce.hashed)
                .build()
        )
        .build()

    private fun addAccountRequest(nonce: GoogleNonce) = GetCredentialRequest.Builder()
        .addCredentialOption(
            GetSignInWithGoogleOption.Builder(serverClientId = googleWebClientId)
                .setNonce(nonce.hashed)
                .build()
        )
        .build()

    private fun extractIdToken(credential: Credential): String? {
        if (credential !is CustomCredential ||
            credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            Log.w(TAG, "Unexpected credential type: ${credential.type}")
            return null
        }
        return try {
            GoogleIdTokenCredential.createFrom(credential.data).idToken
        } catch (e: GoogleIdTokenParsingException) {
            Log.w(TAG, "Couldn't parse Google ID token credential", e)
            null
        }
    }

    private fun mapCredentialFailure(e: GetCredentialException, hadNoAccount: Boolean): GoogleSignInResult {
        if (e is GetCredentialCancellationException) return GoogleSignInResult.Cancelled
        // Logged because a misregistered Android OAuth client surfaces only as a generic
        // GetCredentialException, and the type/message is the one clue to what's wrong.
        Log.w(TAG, "Credential Manager failed (${e.type}): ${e.message}")
        val reason = if (hadNoAccount || e is NoCredentialException) {
            GoogleSignInFailure.NoGoogleAccount
        } else {
            GoogleSignInFailure.GoogleUnavailable
        }
        return GoogleSignInResult.Failure(reason)
    }

    /**
     * Always ends signed out locally. If the server can't be reached, the refresh token stays
     * valid server-side until it expires; that's accepted so sign-out works offline.
     */
    /**
     * Deletes this account on the server (decision D1): its private rows and sign-in go, shared
     * history stays under the name chosen here or "Former member". Throws on failure; nothing is
     * deleted then.
     */
    suspend fun deleteAccount(keepName: Boolean) {
        supabase.postgrest.rpc("delete_my_account", buildJsonObject { put("p_keep_name", keepName) })
    }

    suspend fun signOut(context: Context) {
        try {
            auth.signOut()
        } catch (e: IOException) {
            auth.clearSession()
        } catch (e: RestException) {
            auth.clearSession()
        }
        try {
            // Without this, Credential Manager may silently reuse the previous Google account.
            CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
        } catch (e: ClearCredentialException) {
            Log.w(TAG, "Couldn't clear Credential Manager state", e)
        }
    }

    /** Returns null when the row doesn't exist, which means the profile trigger didn't run. */
    suspend fun fetchProfile(userId: String): Profile? =
        supabase.from("profiles")
            .select { filter { eq("id", userId) } }
            .decodeSingleOrNull<Profile>()

    /** Saves this account's name and emoji (profiles_update_own) and returns the stored row. */
    suspend fun updateProfile(userId: String, displayName: String, avatarEmoji: String?): Profile =
        supabase.from("profiles")
            .update(
                buildJsonObject {
                    put("display_name", displayName)
                    put("avatar_emoji", avatarEmoji)
                }
            ) {
                select()
                filter { eq("id", userId) }
            }
            .decodeSingle<Profile>()

    /**
     * Publishes this account's UPI handle so groupmates settling up can see where to pay.
     *
     * [upiId] must already be through `UpiLink.cleanId`; the server's `profiles_upi_id_format`
     * check constraint will reject anything else anyway, which is the point of having both.
     * Null clears it, which is how "remove my UPI ID" reaches everyone else.
     */
    suspend fun updateUpiId(userId: String, upiId: String?): Profile =
        supabase.from("profiles")
            .update(buildJsonObject { put("upi_id", upiId) }) {
                select()
                filter { eq("id", userId) }
            }
            .decodeSingle<Profile>()

    /**
     * Sets this account's colour (profiles_update_own; migration profile_hue checks it). The server
     * refuses (23514) a hue within 25 degrees of someone this account shares a group with, or in the
     * amber kept for "You". Null lets the app choose.
     */
    suspend fun updateHue(userId: String, hue: Int?): Profile =
        supabase.from("profiles")
            .update(buildJsonObject { put("hue", hue) }) {
                select()
                filter { eq("id", userId) }
            }
            .decodeSingle<Profile>()

    private sealed interface GoogleTokenResult {
        data class Token(val idToken: String) : GoogleTokenResult
        data class Done(val result: GoogleSignInResult) : GoogleTokenResult
    }

    companion object {
        private const val TAG = "AuthRepository"
        private const val PLACEHOLDER_PREFIX = "YOUR_"
        private const val GOOGLE_CLIENT_ID_SUFFIX = ".apps.googleusercontent.com"

        /** Rejects the .env.example placeholder, which the Secrets plugin falls back to. */
        internal fun isUsableWebClientId(clientId: String): Boolean =
            clientId.endsWith(GOOGLE_CLIENT_ID_SUFFIX) && !clientId.startsWith(PLACEHOLDER_PREFIX)
    }
}
