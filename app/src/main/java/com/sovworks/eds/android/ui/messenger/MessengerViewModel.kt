package com.sovworks.eds.android.ui.messenger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class MessengerViewModel : ViewModel() {
    private val _targetPeerId = MutableStateFlow<String?>(null)
    val targetPeerId: StateFlow<String?> = _targetPeerId

    val messages: StateFlow<List<ChatMessage>> = MessengerRepository.messages
        .map { it[_targetPeerId.value] ?: emptyList() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setPeerId(peerId: String?) {
        _targetPeerId.value = peerId
    }

    fun sendMessage(text: String) {
        val peerId = _targetPeerId.value ?: return
        if (text.isNotBlank()) {
            MessengerRepository.sendMessage(peerId, text)
        }
    }
}
