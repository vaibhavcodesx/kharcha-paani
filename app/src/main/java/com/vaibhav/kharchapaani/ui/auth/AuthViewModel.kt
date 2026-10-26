package com.vaibhav.kharchapaani.ui.auth

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vaibhav.kharchapaani.ui.session.AccountDeletion
import com.vaibhav.kharchapaani.BuildConfig
import com.vaibhav.kharchapaani.data.auth.AuthRepository
import com.vaibhav.kharchapaani.data.auth.AuthState
import com.vaibhav.kharchapaani.data.auth.GoogleSignInFailure
import com.vaibhav.kharchapaani.data.auth.GoogleSignInResult
import com.vaibhav.kharchapaani.data.remote.SupabaseClientProvider
import io.github.jan.supabase.exceptions.RestException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.vaibhav.kharchapaani.domain.ProfileEdit
import java.io.IOException

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AuthRepository(
        supabase = SupabaseClientProvider.get(application),
        googleWebClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID,
    )

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private var profileJob: Job? = null

    init {
        viewModelScope.launch {
            repository.authState.collect { authState ->
                val previousUserId = (_uiState.value.authState as? AuthState.SignedIn)?.userId
                _uiState.update { it.copy(authState = authState) }
                when (authState) {
                    is AuthState.SignedIn -> {
                        val cameBackOnline = !authState.isRefreshPending &&
                            _uiState.value.profile == ProfileState.Unavailable
                        val newAccount = authState.userId != previousUserId
                        when {
                            // No live token yet: a request would go out without one and RLS would
                            // answer "no profile". The refresh landing comes back as cameBackOnline.
                            newAccount && authState.isRefreshPending -> {
                                profileJob?.cancel()
                                _uiState.update { it.copy(profile = ProfileState.Unavailable) }
                            }
                            newAccount || cameBackOnline -> loadProfile(authState.userId)
                        }
                    }
                    AuthState.SignedOut -> {
                        profileJob?.cancel()
                        _uiState.update { it.copy(profile = ProfileState.Idle) }
                    }
                    AuthState.Initializing -> Unit
                }
            }
        }
    }

    /** [activityContext] is only held for the duration of the account picker. */
    fun signInWithGoogle(activityContext: Context) {
        if (_uiState.value.isSigningIn) return
        _uiState.update { it.copy(isSigningIn = true, signInFailure = null) }
        viewModelScope.launch {
            val result = repository.signInWithGoogle(activityContext)
            _uiState.update {
                it.copy(
                    isSigningIn = false,
                    signInFailure = when (result) {
                        is GoogleSignInResult.Failure -> result.reason
                        GoogleSignInResult.Cancelled -> GoogleSignInFailure.Dismissed
                        GoogleSignInResult.Success -> null
                    },
                )
            }
        }
    }

    fun dismissSignInFailure() {
        _uiState.update { it.copy(signInFailure = null) }
    }

    fun retryProfileLoad() {
        loadProfile((_uiState.value.authState as? AuthState.SignedIn)?.userId)
    }

    /** Saves a new name and emoji. Needs the cloud profile: names are what groupmates see. */
    fun saveProfile(name: String, emoji: String?) {
        val userId = (_uiState.value.authState as? AuthState.SignedIn)?.userId ?: return
        val cleanName = ProfileEdit.cleanName(name) ?: return
        if (_uiState.value.isSavingProfile) return
        _uiState.update { it.copy(isSavingProfile = true, profileSaveFailed = false) }
        viewModelScope.launch {
            val saved = try {
                repository.updateProfile(userId, cleanName, ProfileEdit.cleanEmoji(emoji))
            } catch (e: IOException) {
                null
            } catch (e: RestException) {
                Log.w(TAG, "Profile update rejected: ${e.error}")
                null
            }
            _uiState.update {
                it.copy(
                    isSavingProfile = false,
                    profileSaveFailed = saved == null,
                    profile = saved?.let(ProfileState::Loaded) ?: it.profile,
                )
            }
        }
    }

    /**
     * Saves this account's colour. [automatic] is the app picking one for someone who never chose:
     * a refusal then just waits for the next try instead of showing an error they didn't cause.
     */
    fun saveHue(hue: Int?, automatic: Boolean = false) {
        val userId = (_uiState.value.authState as? AuthState.SignedIn)?.userId ?: return
        _uiState.update { it.copy(hueSaveError = null) }
        viewModelScope.launch {
            val error: String? = try {
                val saved = repository.updateHue(userId, hue)
                _uiState.update { it.copy(profile = ProfileState.Loaded(saved)) }
                null
            } catch (e: IOException) {
                "You're offline, so your colour wasn't saved. Try again when you're connected."
            } catch (e: RestException) {
                Log.w(TAG, "Colour rejected: ${e.error}")
                // 23514: the server's rule (too close to a groupmate's colour, or the app's amber accent).
                "Someone in your groups has a colour too close to that one. Pick one further away."
            }
            if (!automatic) _uiState.update { it.copy(hueSaveError = error) }
        }
    }

    fun signOut(context: Context) {
        viewModelScope.launch { repository.signOut(context) }
    }

    /**
     * Deletes the signed-in account (plan T1.1, decision D1), then signs out and removes everything
     * of it from this phone. The server goes first: if it refuses, nothing here is touched.
     */
    fun deleteAccount(context: Context, keepName: Boolean) {
        val userId = (_uiState.value.authState as? AuthState.SignedIn)?.userId ?: return
        if (_uiState.value.isDeletingAccount) return
        _uiState.update { it.copy(isDeletingAccount = true, deleteAccountError = null) }
        viewModelScope.launch {
            try {
                repository.deleteAccount(keepName)
            } catch (e: IOException) {
                _uiState.update { it.copy(isDeletingAccount = false, deleteAccountError = "You're offline. Connect and try again; nothing was deleted.") }
                return@launch
            } catch (e: RestException) {
                Log.w(TAG, "Account deletion refused (${e.statusCode})")
                _uiState.update { it.copy(isDeletingAccount = false, deleteAccountError = "Couldn't delete your account just now. Nothing was deleted; try again in a minute.") }
                return@launch
            }
            repository.signOut(context)
            AccountDeletion.wipeLocal(context, userId)
            _uiState.update { it.copy(isDeletingAccount = false) }
        }
    }

    fun dismissDeleteAccountError() {
        _uiState.update { it.copy(deleteAccountError = null) }
    }

    private fun loadProfile(userId: String?) {
        profileJob?.cancel()
        if (userId == null) {
            _uiState.update { it.copy(profile = ProfileState.Unavailable) }
            return
        }
        _uiState.update { it.copy(profile = ProfileState.Loading) }
        profileJob = viewModelScope.launch {
            val profileState = try {
                repository.fetchProfile(userId)
                    ?.let { ProfileState.Loaded(it) }
                    ?: ProfileState.Missing
            } catch (e: IOException) {
                ProfileState.Unavailable
            } catch (e: RestException) {
                Log.w(TAG, "Profile request rejected: ${e.error}")
                ProfileState.Unavailable
            }
            _uiState.update { it.copy(profile = profileState) }
        }
    }

    private companion object {
        const val TAG = "AuthViewModel"
    }
}
