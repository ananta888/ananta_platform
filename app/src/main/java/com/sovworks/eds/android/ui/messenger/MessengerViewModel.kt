package com.sovworks.eds.android.ui.messenger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class MessengerViewModel : ViewModel() {
    private val _chatId = MutableStateFlow<String?>(null)
    val chatId: StateFlow<String?> = _chatId
    private var isGroup = false
    private var lastPeerId: String? = null

    val messages: StateFlow<List<ChatMessage>> = combine(
        _chatId,
        MessengerRepository.messages
    ) { id, allMessages ->
        allMessages[id] ?: emptyList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setChat(id: String?, isGroupChat: Boolean = false, relayOnly: Boolean = false) {
        if (!isGroup && lastPeerId != null && lastPeerId != id) {
            MessengerRepository.setRelayOnly(lastPeerId, false)
        }
        _chatId.value = id
        isGroup = isGroupChat
        if (!isGroupChat) {
            MessengerRepository.setRelayOnly(id, relayOnly)
            lastPeerId = id
        } else {
            lastPeerId = null
        }
    }

    fun sendMessage(text: String) {
        val id = _chatId.value ?: return
        if (text.isNotBlank()) {
            if (isGroup) {
                MessengerRepository.sendGroupMessage(id, text)
            } else {
                MessengerRepository.sendMessage(id, text)
            }
        }
    }
}
