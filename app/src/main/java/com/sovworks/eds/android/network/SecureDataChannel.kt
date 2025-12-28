package com.sovworks.eds.android.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.webrtc.DataChannel
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import java.nio.ByteBuffer
import java.util.ArrayDeque

class SecureDataChannel(
    private val channel: DataChannel,
    isInitiator: Boolean,
    localIdentityKey: Ed25519PrivateKeyParameters,
    remoteIdentityKey: Ed25519PublicKeyParameters,
    private val onTextMessage: (String) -> Unit,
    private val onBinaryMessage: (ByteArray) -> Unit
) : DataChannel.Observer {
    private val pfsSession = PfsSession(isInitiator, localIdentityKey, remoteIdentityKey)
    private val pendingText = ArrayDeque<String>()
    private val pendingBinary = ArrayDeque<ByteArray>()
    
    private val _bufferedAmountFlow = MutableStateFlow(0L)
    val bufferedAmountFlow: StateFlow<Long> = _bufferedAmountFlow.asStateFlow()

    init {
        channel.registerObserver(this)
    }

    fun bufferedAmount(): Long = channel.bufferedAmount()

    fun sendText(message: String) {
        if (!pfsSession.isReady()) {
            pendingText.addLast(message)
            ensureHandshake()
            return
        }
        val encrypted = pfsSession.encryptText(message) ?: return
        sendControl("${PfsSession.PFS_MESSAGE_PREFIX}$encrypted")
    }

    fun sendBinary(data: ByteArray) {
        if (!pfsSession.isReady()) {
            pendingBinary.addLast(data)
            ensureHandshake()
            return
        }
        val encrypted = pfsSession.encryptBinary(data) ?: return
        channel.send(DataChannel.Buffer(ByteBuffer.wrap(encrypted), true))
    }

    override fun onBufferedAmountChange(previousAmount: Long) {
        _bufferedAmountFlow.value = channel.bufferedAmount()
    }

    override fun onStateChange() {
        if (channel.state() == DataChannel.State.OPEN) {
            ensureHandshake()
        }
    }

    override fun onMessage(buffer: DataChannel.Buffer) {
        if (!buffer.binary) {
            val message = bufferToString(buffer.data)
            if (pfsSession.handleControl(message, ::sendControl)) {
                if (pfsSession.isReady()) {
                    flushPending()
                }
                return
            }
            if (!message.startsWith(PfsSession.PFS_MESSAGE_PREFIX)) {
                return
            }
            val payload = message.removePrefix(PfsSession.PFS_MESSAGE_PREFIX)
            val plaintext = pfsSession.decryptText(payload) ?: return
            onTextMessage(plaintext)
            return
        }

        if (!pfsSession.isReady()) {
            return
        }
        val bytes = bufferToBytes(buffer.data)
        val plaintext = pfsSession.decryptBinary(bytes) ?: return
        onBinaryMessage(plaintext)
    }

    private fun ensureHandshake() {
        pfsSession.ensureHello(::sendControl)
    }

    private fun flushPending() {
        while (pendingText.isNotEmpty()) {
            sendText(pendingText.removeFirst())
        }
        while (pendingBinary.isNotEmpty()) {
            sendBinary(pendingBinary.removeFirst())
        }
    }

    private fun sendControl(message: String) {
        val buffer = ByteBuffer.wrap(message.toByteArray(Charsets.UTF_8))
        channel.send(DataChannel.Buffer(buffer, false))
    }

    private fun bufferToString(buffer: ByteBuffer): String {
        val bytes = bufferToBytes(buffer)
        return String(bytes, Charsets.UTF_8)
    }

    private fun bufferToBytes(buffer: ByteBuffer): ByteArray {
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return bytes
    }
}
