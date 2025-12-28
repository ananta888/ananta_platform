package com.sovworks.eds.android.network

import android.content.Context
import com.google.gson.Gson
import com.sovworks.eds.android.identity.IdentityManager
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class SearchManager private constructor(private val context: Context) {
    private val gson = Gson()
    private val processedRequests = Collections.synchronizedSet(mutableSetOf<String>())
    private val searchListeners = mutableListOf<(SearchResponse) -> Unit>()
    
    // Multiplexer wird später gesetzt oder über eine Methode bezogen um zirkuläre Abhängigkeiten zu vermeiden
    private var multiplexer: DataChannelMultiplexer? = null

    fun setMultiplexer(mux: DataChannelMultiplexer) {
        this.multiplexer = mux
    }

    fun addSearchListener(listener: (SearchResponse) -> Unit) {
        searchListeners.add(listener)
    }

    fun removeSearchListener(listener: (SearchResponse) -> Unit) {
        searchListeners.remove(listener)
    }

    fun search(query: String) {
        val identity = IdentityManager.loadIdentity(context) ?: return
        val request = SearchRequest(
            requestId = UUID.randomUUID().toString(),
            query = query,
            senderId = identity.getFingerprint()
        )
        processedRequests.add(request.requestId)
        
        val msg = DiscoveryMessage("search", gson.toJson(request))
        multiplexer?.broadcastDiscoveryMessage(gson.toJson(msg))
    }

    fun requestFile(peerId: String, fileName: String) {
        multiplexer?.sendFileMessage(peerId, "FILE_REQUEST:$fileName")
    }

    fun onMessageReceived(peerId: String, json: String) {
        try {
            val msg = gson.fromJson(json, DiscoveryMessage::class.java)
            when (msg.type) {
                "search" -> handleSearchRequest(peerId, gson.fromJson(msg.payload, SearchRequest::class.java))
                "response" -> handleSearchResponse(gson.fromJson(msg.payload, SearchResponse::class.java))
            }
        } catch (e: Exception) {
            // Log error
        }
    }

    private fun handleSearchRequest(peerId: String, request: SearchRequest) {
        if (processedRequests.contains(request.requestId)) return
        processedRequests.add(request.requestId)

        // 1. Lokal suchen
        val results = SharedFileManager.getInstance(context).searchFiles(request.query)
        if (results.isNotEmpty()) {
            val identity = IdentityManager.loadIdentity(context)
            val response = SearchResponse(
                requestId = request.requestId,
                results = results,
                peerId = identity?.getFingerprint() ?: "unknown"
            )
            val msg = DiscoveryMessage("response", gson.toJson(response))
            multiplexer?.sendDiscoveryMessage(peerId, gson.toJson(msg))
        }

        // 2. Weiterleiten wenn TTL > 0
        if (request.ttl > 0) {
            val forwardedRequest = request.copy(ttl = request.ttl - 1)
            val msg = DiscoveryMessage("search", gson.toJson(forwardedRequest))
            val jsonMsg = gson.toJson(msg)
            
            // An alle außer den Absender senden
            // Hier bräuchten wir eine Liste aller verbundenen Peers im Multiplexer
            // Der Einfachheit halber broadcasten wir es, der Multiplexer filtert nicht.
            // Aber wir haben processedRequests, also ist ein Loop-Schutz da.
            multiplexer?.broadcastDiscoveryMessage(jsonMsg)
        }
    }

    private fun handleSearchResponse(response: SearchResponse) {
        searchListeners.forEach { it(response) }
    }

    companion object {
        @Volatile
        private var instance: SearchManager? = null

        fun getInstance(context: Context): SearchManager {
            return instance ?: synchronized(this) {
                instance ?: SearchManager(context).also { instance = it }
            }
        }
    }
}
