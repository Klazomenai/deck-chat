package dev.klazomenai.deckchat

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OnboardingViewModel(
    private val application: Application,
    private val storage: SecureStorage,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val loginAction: suspend (url: String, username: String, password: String) -> Unit = { url, username, password ->
        val client = RustMatrixClient(application, storage)
        client.login(url, username, password)
    },
) : ViewModel() {

    sealed class LoginState {
        object Idle : LoginState()
        object InProgress : LoginState()
        data class Error(val message: String) : LoginState()
        object Success : LoginState()
    }

    private val _loginState = MutableStateFlow<LoginState>(LoginState.Idle)
    val loginState: StateFlow<LoginState> = _loginState.asStateFlow()

    fun validateAndLogin(url: String, username: String, password: String, roomId: String) {
        viewModelScope.launch {
            _loginState.value = LoginState.InProgress
            try {
                withContext(ioDispatcher) {
                    Log.d(TAG, "Starting login for ${android.net.Uri.parse(url).host ?: "unknown"}")
                    loginAction(url, username, password)
                    Log.d(TAG, "Login successful")
                }
                storage.roomId = roomId
                _loginState.value = LoginState.Success
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Login failed: ${e.javaClass.name}", e)
                _loginState.value = LoginState.Error(e.message ?: "Login failed (${e.javaClass.simpleName})")
            }
        }
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val storage = (application as DeckChatApplication).secureStorage
            return OnboardingViewModel(application, storage) as T
        }
    }

    companion object {
        private const val TAG = "DeckChat.Onboarding"
    }
}
