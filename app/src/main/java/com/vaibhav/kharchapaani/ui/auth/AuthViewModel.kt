package com.vaibhav.kharchapaani.ui.auth

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vaibhav.kharchapaani.BuildConfig
import com.vaibhav.kharchapaani.data.auth.AuthRepository
import com.vaibhav.kharchapaani.data.auth.AuthState
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
                        if (authState.userId != previousUserId || cameBackOnline) {
                            loadProfile(authState.userId)
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
                    signInFailure = (result as? GoogleSignInResult.Failure)?.reason,
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

    fun signOut(context: Context) {
        viewModelScope.launch { repository.signOut(context) }
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
