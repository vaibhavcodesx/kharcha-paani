package com.vaibhav.kharchapaani.ui.session

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewModelScope
import com.vaibhav.kharchapaani.data.auth.AuthState
import com.vaibhav.kharchapaani.data.remote.SupabaseClientProvider
import com.vaibhav.kharchapaani.data.settings.LedgerMode
import com.vaibhav.kharchapaani.ui.auth.AuthViewModel
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

sealed interface LocalSignInState {
    data object Idle : LocalSignInState
    data object SigningIn : LocalSignInState
    data object Succeeded : LocalSignInState
    data object Failed : LocalSignInState
}

/** The owner exists locally, but no auth ViewModel or cloud client exists until explicitly needed. */
class CloudAuthOwner(application: Application) : AndroidViewModel(application) {
    private var authStore = ViewModelStore()
    private val transition = Mutex()
    private var cleared = false
    private val _localSignIn = MutableStateFlow<LocalSignInState>(LocalSignInState.Idle)
    val localSignIn = _localSignIn.asStateFlow()

    /** Called only by cloud composition or the explicit Google sign-in action. */
    fun authViewModel(): AuthViewModel {
        check(!cleared) { "Cloud auth owner has ended" }
        val owner = object : ViewModelStoreOwner { override val viewModelStore = authStore }
        return ViewModelProvider(owner, ViewModelProvider.AndroidViewModelFactory.getInstance(getApplication()))[AuthViewModel::class.java]
    }

    /** Close the old client without signing out or removing the stored account's tokens. */
    suspend fun clearAndClose() = withContext(NonCancellable) {
        transition.withLock {
            val jobs = authStore.keys().mapNotNull { authStore[it]?.viewModelScope?.coroutineContext?.get(Job) }
            authStore.clear()
            // Recreate only after cancellation has finished, so old profile work cannot reach a new client.
            jobs.joinAll()
            if (!cleared) {
                SupabaseClientProvider.closeExisting()
                authStore = ViewModelStore()
            }
        }
    }

    fun signInFromLocal(activityContext: Context) {
        if (_localSignIn.value == LocalSignInState.SigningIn) return
        _localSignIn.value = LocalSignInState.SigningIn
        viewModelScope.launch {
            var succeeded = false
            try {
                val auth = authViewModel()
                // Even if a stored account exists, the person explicitly gets Google's picker.
                auth.signInWithGoogle(activityContext)
                val result = auth.uiState.first { !it.isSigningIn }
                if (result.signInFailure == null) {
                    val userId = SupabaseClientProvider.get(getApplication()).auth.currentUserOrNull()?.id
                    if (userId != null) {
                        withTimeout(15_000) {
                            auth.uiState.first {
                                val signedIn = it.authState as? AuthState.SignedIn
                                signedIn != null && signedIn.userId == userId && !signedIn.isRefreshPending
                            }
                        }
                        succeeded = true
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // The same local ledger remains selected; no exception may silently select Cloud.
            } finally {
                if (succeeded) {
                    _localSignIn.value = LocalSignInState.Succeeded
                } else {
                    if (!cleared) clearAndClose()
                    if (!cleared) _localSignIn.value = LocalSignInState.Failed
                }
            }
        }
    }

    fun consumeLocalSignInResult() {
        if (_localSignIn.value != LocalSignInState.SigningIn) _localSignIn.value = LocalSignInState.Idle
    }

    override fun onCleared() {
        cleared = true
        val jobs = authStore.keys().mapNotNull { authStore[it]?.viewModelScope?.coroutineContext?.get(Job) }
        authStore.clear()
        // Finishing a local sign-in screen must not leave the SDK refreshing while the app is local.
        if (LedgerMode(getApplication()).choice == LedgerMode.Choice.LOCAL) {
            CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch(start = CoroutineStart.UNDISPATCHED) {
                // Detach now, before a replacement Activity can create a new client. A delayed
                // closeExisting after join could otherwise close that replacement's client.
                SupabaseClientProvider.closeExisting()
                jobs.joinAll()
            }
        }
    }
}
