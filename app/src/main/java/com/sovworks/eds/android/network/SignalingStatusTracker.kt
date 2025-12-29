package com.sovworks.eds.android.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class SignalingConnectionStatus {
    CONNECTING,
    CONNECTED,
    DISCONNECTED,
    ERROR
}

object SignalingStatusTracker {
    private val _statuses = MutableStateFlow<Map<String, SignalingConnectionStatus>>(emptyMap())
    val statuses: StateFlow<Map<String, SignalingConnectionStatus>> = _statuses.asStateFlow()

    fun update(url: String, status: SignalingConnectionStatus) {
        _statuses.value = _statuses.value.toMutableMap().apply {
            this[url] = status
        }
    }
}
