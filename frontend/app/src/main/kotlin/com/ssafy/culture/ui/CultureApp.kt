package com.ssafy.culture.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.compose.rememberNavController
import com.ssafy.culture.BuildConfig
import com.ssafy.culture.data.auth.AuthRepository
import com.ssafy.culture.data.auth.AuthTokenStore
import com.ssafy.culture.data.dev.MockApiConfig
import com.ssafy.culture.data.preferences.AppPreferenceStore
import com.ssafy.culture.data.user.UserRepository
import com.ssafy.culture.ui.dev.DevNavOverlay
import com.ssafy.culture.ui.navigation.AppStartDestination
import com.ssafy.culture.ui.navigation.CultureNavGraph
import com.ssafy.culture.ui.theme.CultureTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class CultureAppViewModel @Inject constructor(
    private val appPreferenceStore: AppPreferenceStore,
    private val authTokenStore: AuthTokenStore,
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
) : ViewModel() {
    private val _authBootstrapState = MutableStateFlow(AuthBootstrapState.Checking)
    val authBootstrapState: StateFlow<AuthBootstrapState> = _authBootstrapState.asStateFlow()

    val darkModeEnabled: StateFlow<Boolean> = appPreferenceStore.darkModeEnabled.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = false,
    )

    val devAccessAllowed: StateFlow<Boolean> = authTokenStore.devAccessAllowedFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = authTokenStore.isDevAccessAllowed(),
    )

    init {
        viewModelScope.launch {
            val isAuthenticated = restoreSavedSession()
            _authBootstrapState.value = if (isAuthenticated) {
                AuthBootstrapState.Authenticated
            } else {
                AuthBootstrapState.Unauthenticated
            }
        }
    }

    fun setDarkModeEnabled(isEnabled: Boolean) {
        viewModelScope.launch {
            appPreferenceStore.setDarkModeEnabled(isEnabled)
        }
    }

    private suspend fun restoreSavedSession(): Boolean {
        if (!authTokenStore.isAutoLoginEnabled()) {
            authTokenStore.clear()
            return false
        }

        val accessToken: String? = authTokenStore.getAccessToken()?.takeIf(String::isNotBlank)
        val refreshToken: String? = authTokenStore.getRefreshToken()?.takeIf(String::isNotBlank)
        if (accessToken == null && refreshToken == null) {
            authTokenStore.clear()
            return false
        }

        if (authTokenStore.isDevAccessAllowed()) {
            MockApiConfig.enabled = true
            return true
        }

        if (accessToken == null && refreshToken != null) {
            val refreshed = refreshAccessToken(refreshToken)
            if (!refreshed) {
                authTokenStore.clear()
                return false
            }
        }

        val currentUserLoaded = runCatching {
            userRepository.refreshCurrentUser()
        }.isSuccess
        if (currentUserLoaded) {
            return true
        }

        if (refreshToken != null && refreshAccessToken(refreshToken)) {
            return runCatching {
                userRepository.refreshCurrentUser()
            }.isSuccess.also { isRestored ->
                if (!isRestored) {
                    authTokenStore.clear()
                }
            }
        }

        authTokenStore.clear()
        return false
    }

    private suspend fun refreshAccessToken(refreshToken: String): Boolean =
        runCatching {
            authRepository.refreshToken(refreshToken)
        }.isSuccess
}

enum class AuthBootstrapState {
    Checking,
    Authenticated,
    Unauthenticated,
}

@Composable
fun CultureApp(
    viewModel: CultureAppViewModel = hiltViewModel(),
) {
    val darkModeEnabled: Boolean by viewModel.darkModeEnabled.collectAsStateWithLifecycle()
    val authBootstrapState: AuthBootstrapState by viewModel.authBootstrapState.collectAsStateWithLifecycle()
    val devAccessAllowed: Boolean by viewModel.devAccessAllowed.collectAsStateWithLifecycle()

    CultureTheme(darkTheme = darkModeEnabled) {
        val navController = rememberNavController()
        Box(modifier = Modifier.fillMaxSize()) {
            when (authBootstrapState) {
                AuthBootstrapState.Checking -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                AuthBootstrapState.Authenticated,
                AuthBootstrapState.Unauthenticated,
                -> {
                    val startDestination = when (authBootstrapState) {
                        AuthBootstrapState.Authenticated -> AppStartDestination.PermissionOnboarding
                        AuthBootstrapState.Unauthenticated -> AppStartDestination.Login
                        AuthBootstrapState.Checking -> AppStartDestination.Login
                    }
                    CultureNavGraph(
                        navController = navController,
                        startDestination = startDestination,
                        darkModeEnabled = darkModeEnabled,
                        onDarkModeChange = viewModel::setDarkModeEnabled,
                    )
                    if (BuildConfig.DEBUG && devAccessAllowed) {
                        DevNavOverlay(navController = navController)
                    }
                }
            }
        }
    }
}

