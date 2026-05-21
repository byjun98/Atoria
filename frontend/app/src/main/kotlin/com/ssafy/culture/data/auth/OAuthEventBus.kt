package com.ssafy.culture.data.auth

import com.ssafy.culture.domain.model.AuthSession
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

@Singleton
class OAuthEventBus @Inject constructor() {
    private val _events = MutableSharedFlow<OAuthEvent>(
        replay = 1,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<OAuthEvent> = _events.asSharedFlow()

    fun emit(event: OAuthEvent) {
        _events.tryEmit(event)
    }
}

sealed class OAuthEvent {
    data class Success(val session: AuthSession) : OAuthEvent()
    data class Failure(val message: String) : OAuthEvent()
}
