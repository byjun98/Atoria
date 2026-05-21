package com.ssafy.culture

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.ssafy.culture.data.auth.AuthTokenStore
import com.ssafy.culture.data.auth.OAuthEvent
import com.ssafy.culture.data.auth.OAuthEventBus
import com.ssafy.culture.domain.model.AuthSession
import com.ssafy.culture.domain.model.AuthUser
import com.ssafy.culture.ui.CultureApp
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var authTokenStore: AuthTokenStore

    @Inject
    lateinit var oauthEventBus: OAuthEventBus

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_Culture)
        super.onCreate(savedInstanceState)
        handleOAuthDeepLink(intent)
        setContent {
            CultureApp()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOAuthDeepLink(intent)
    }

    private fun handleOAuthDeepLink(intent: Intent?) {
        val data: Uri = intent?.data ?: return
        if (data.scheme != OAUTH_SCHEME || data.host != OAUTH_HOST) return

        val error = data.getQueryParameter("error")
        if (!error.isNullOrBlank()) {
            oauthEventBus.emit(OAuthEvent.Failure(error))
            return
        }

        val accessToken = data.getQueryParameter("accessToken")?.takeIf(String::isNotBlank)
        val refreshToken = data.getQueryParameter("refreshToken")?.takeIf(String::isNotBlank)
        val userId = data.getQueryParameter("userId")?.toLongOrNull()
        val nickname = data.getQueryParameter("nickname")?.takeIf(String::isNotBlank)

        if (accessToken == null || refreshToken == null || userId == null || nickname == null) {
            oauthEventBus.emit(OAuthEvent.Failure("OAuth 응답이 올바르지 않습니다."))
            return
        }

        val session = AuthSession(
            accessToken = accessToken,
            refreshToken = refreshToken,
            user = AuthUser(userId = userId, nickname = nickname),
        )
        authTokenStore.updateSession(session)
        authTokenStore.setDevAccessAllowed(false)
        oauthEventBus.emit(OAuthEvent.Success(session))
    }

    private companion object {
        const val OAUTH_SCHEME = "culture"
        const val OAUTH_HOST = "oauth"
    }
}
