package com.sovworks.eds.android.network

import android.content.Context
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory

object WebRTCManager {
    private var factory: PeerConnectionFactory? = null

    fun initialize(context: Context) {
        if (factory != null) return

        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)

        val encoderFactory = DefaultVideoEncoderFactory(null, true, true)
        val decoderFactory = DefaultVideoDecoderFactory(null)

        factory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()
    }

    fun createPeerConnection(
        iceServers: List<PeerConnection.IceServer>,
        observer: PeerConnection.Observer
    ): PeerConnection? {
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers)
        return factory?.createPeerConnection(rtcConfig, observer)
    }
}
