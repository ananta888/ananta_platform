package com.sovworks.eds.android.network

import java.util.concurrent.CopyOnWriteArrayList

object SignalingRelayBus {
    private val listeners = CopyOnWriteArrayList<(String, String) -> Unit>()

    fun addListener(listener: (String, String) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (String, String) -> Unit) {
        listeners.remove(listener)
    }

    fun dispatch(fromPeerId: String, payload: String) {
        listeners.forEach { it(fromPeerId, payload) }
    }
}
