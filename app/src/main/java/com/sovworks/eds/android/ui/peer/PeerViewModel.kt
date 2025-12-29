package com.sovworks.eds.android.ui.peer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sovworks.eds.android.trust.TrustStore
import com.sovworks.eds.android.trust.TrustedKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class PeerViewModel(application: Application) : AndroidViewModel(application) {
    private val trustStore = TrustStore.getInstance(application)
    private val _peers = MutableStateFlow<List<TrustedKey>>(emptyList())
    val peers: StateFlow<List<TrustedKey>> = _peers

    init {
        loadPeers()
    }

    fun loadPeers() {
        viewModelScope.launch {
            _peers.value = trustStore.allKeys.values.toList()
        }
    }

    fun updateTrustLevel(fingerprint: String, level: Int) {
        val key = trustStore.getKey(fingerprint)
        if (key != null) {
            key.trustLevel = level
            trustStore.save()
            loadPeers()
        }
    }

    fun removePeer(fingerprint: String) {
        trustStore.removeKey(fingerprint)
        loadPeers()
    }

    fun updateAlias(fingerprint: String, alias: String) {
        val key = trustStore.getKey(fingerprint)
        if (key != null) {
            key.name = alias
            trustStore.save()
            loadPeers()
        }
    }
}
