package au.com.shiftyjelly.pocketcasts.account.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import au.com.shiftyjelly.pocketcasts.analytics.AnalyticsEvent
import au.com.shiftyjelly.pocketcasts.analytics.AnalyticsTrackerWrapper
import au.com.shiftyjelly.pocketcasts.repositories.podcast.PodcastManager
import au.com.shiftyjelly.pocketcasts.repositories.subscription.SubscriptionManager
import au.com.shiftyjelly.pocketcasts.servers.account.SignInResult
import au.com.shiftyjelly.pocketcasts.servers.account.SignInSource
import au.com.shiftyjelly.pocketcasts.servers.account.SyncAccountManager
import au.com.shiftyjelly.pocketcasts.servers.sync.SyncServerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext

@HiltViewModel
class OnboardingLogInViewModel @Inject constructor(
    private val syncAccountManager: SyncAccountManager,
    private val analyticsTracker: AnalyticsTrackerWrapper,
    private val subscriptionManager: SubscriptionManager,
    private val syncServerManager: SyncServerManager,
    private val podcastManager: PodcastManager
) : ViewModel(), CoroutineScope {

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Default

    private val _state = MutableStateFlow(LogInState())
    val state: StateFlow<LogInState> = _state

    fun updateEmail(email: String) {
        _state.update { it.copy(email = email.trim()) }
    }

    fun updatePassword(password: String) {
        _state.update { it.copy(password = password) }
    }

    fun logIn(onSuccessfulLogin: () -> Unit) {
        _state.update { it.copy(hasAttemptedLogIn = true) }

        val state = state.value
        if (!state.isEmailValid || !state.isPasswordValid) {
            return
        }

        _state.update {
            it.copy(
                isCallInProgress = true,
                serverErrorMessage = null,
            )
        }

        subscriptionManager.clearCachedStatus()

        viewModelScope.launch {
            val result = syncAccountManager.signInWithEmailAndPassword(
                email = state.email,
                password = state.password,
                syncServerManager = syncServerManager,
                signInSource = SignInSource.Onboarding
            )
            when (result) {
                is SignInResult.Success -> {
                    podcastManager.refreshPodcastsAfterSignIn()
                    onSuccessfulLogin()
                }

                is SignInResult.Failed -> {
                    _state.update {
                        it.copy(
                            isCallInProgress = false,
                            serverErrorMessage = result.message,
                        )
                    }
                }
            }
        }
    }

    fun onShown() {
        analyticsTracker.track(AnalyticsEvent.SIGNIN_SHOWN)
    }

    fun onBackPressed() {
        analyticsTracker.track(AnalyticsEvent.SIGNIN_DISMISSED)
    }
}

data class LogInState(
    val email: String = "",
    val password: String = "",
    val serverErrorMessage: String? = null,
    private val isCallInProgress: Boolean = false,
    private val hasAttemptedLogIn: Boolean = false,
) {
    val isEmailValid = AccountViewModel.isEmailValid(email)
    val isPasswordValid = AccountViewModel.isPasswordValid(password)

    val showEmailError = hasAttemptedLogIn && !isEmailValid
    val showPasswordError = hasAttemptedLogIn && !isPasswordValid

    val enableSubmissionFields = !isCallInProgress
}
