package com.example.network

import android.net.Network
import android.util.Log
import com.example.audio.AudioPacket
import com.example.audio.AudioPacketConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketException

/**
 * Peer-to-peer UDP socket for voice streaming over LAN or Wi-Fi hotspot.
 * Runs on Dispatchers.IO with dedicated send and receive coroutines.
 *
 * If [network] is given (the phone's Wi-Fi network), the socket is bound to it so that packets
 * always leave through Wi-Fi even when Android prefers mobile data as the default network
 * (which happens when the Wi-Fi/hotspot has no internet).
 */
class UdpVoiceSocket(
    private val localPort: Int,
    private val network: Network?,
    private val onPacketReceived: (AudioPacket) -> Unit,
    private val onError: (String) -> Unit
) {
    companion object {
        private const val TAG = "UdpVoiceSocket"
    }

    private var socket: DatagramSocket? = null
    private var sendJob: Job? = null
    private var receiveJob: Job? = null

    // Channel for buffering outgoing audio packets without blocking the mic thread
    private val sendChannel = Channel<OutgoingDatagram>(capacity = 32)

    private data class OutgoingDatagram(
        val data: ByteArray,
        val targetIp: String,
        val targetPort: Int
    )

    @Volatile
    private var isRunning: Boolean = false

    // ---- Diagnostics (shown on screen so a failed link can be located without logcat) ----

    /** Datagrams that were actually handed to the network stack. */
    @Volatile
    var packetsSent: Long = 0L
        private set

    /** Any datagram that arrived on the listening port (even if it was not valid audio). */
    @Volatile
    var packetsReceived: Long = 0L
        private set

    /** IP address of the last peer that sent us something. */
    @Volatile
    var lastPeer: String? = null
        private set

    /** Last exception thrown by send(), e.g. "Network is unreachable". */
    @Volatile
    var lastSendError: String? = null
        private set

    /** Last exception thrown by receive(). */
    @Volatile
    var lastReceiveError: String? = null
        private set

    /** Local address/port the socket is bound to, e.g. "/0.0.0.0:8888". */
    val localAddressText: String
        get() = socket?.localSocketAddress?.toString() ?: "not bound"

    /**
     * Binds the UDP socket with SO_REUSEADDR and starts sending and receiving coroutines.
     */
    fun start(scope: CoroutineScope): Boolean {
        if (isRunning) return true

        // IMPORTANT: read the port here. Inside DatagramSocket(null).apply { ... } the name
        // "localPort" resolves to DatagramSocket.getLocalPort(), which is 0 for an unbound socket,
        // so the socket was silently bound to a random port instead of the configured one (8888).
        val portToBind = this.localPort

        return try {
            val sock = DatagramSocket(null).apply {
                reuseAddress = true
                if (network != null) {
                    try {
                        network.bindSocket(this)
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not bind socket to Wi-Fi network: ${e.message}")
                    }
                }
                bind(InetSocketAddress(portToBind))
                // Set receive buffer for UDP audio
                receiveBufferSize = AudioPacketConstants.PACKET_SIZE * 32
                sendBufferSize = AudioPacketConstants.PACKET_SIZE * 32
            }
            socket = sock
            isRunning = true

            // Coroutine 1: Continuous packet receiver
            receiveJob = scope.launch(Dispatchers.IO) {
                val recvBuffer = ByteArray(AudioPacketConstants.PACKET_SIZE)
                val datagram = DatagramPacket(recvBuffer, recvBuffer.size)

                while (isActive && isRunning) {
                    try {
                        // receive() shrinks the packet length to the size of the last datagram;
                        // reset it so one short stray packet cannot truncate all later audio.
                        datagram.length = recvBuffer.size
                        sock.receive(datagram)
                        packetsReceived++
                        lastPeer = datagram.address?.hostAddress

                        val length = datagram.length
                        if (length == AudioPacketConstants.PACKET_SIZE) {
                            val packet = AudioPacket.fromByteArray(datagram.data, length)
                            if (packet != null) {
                                onPacketReceived(packet)
                            }
                        }
                    } catch (e: SocketException) {
                        if (!isRunning || !isActive) break
                        lastReceiveError = "${e.javaClass.simpleName}: ${e.message}"
                        Log.w(TAG, "SocketException in receive loop: ${e.message}")
                    } catch (e: Exception) {
                        if (!isRunning || !isActive) break
                        lastReceiveError = "${e.javaClass.simpleName}: ${e.message}"
                        Log.e(TAG, "Error in receive loop: ${e.message}", e)
                    }
                }
            }

            // Coroutine 2: Outgoing packet dispatcher
            sendJob = scope.launch(Dispatchers.IO) {
                while (isActive && isRunning) {
                    try {
                        val outgoing = sendChannel.receive()
                        val address = InetAddress.getByName(outgoing.targetIp)
                        val packet = DatagramPacket(
                            outgoing.data,
                            outgoing.data.size,
                            address,
                            outgoing.targetPort
                        )
                        sock.send(packet)
                        packetsSent++
                    } catch (e: SocketException) {
                        if (!isRunning || !isActive) break
                        lastSendError = "${e.javaClass.simpleName}: ${e.message}"
                        Log.w(TAG, "SocketException in send loop: ${e.message}")
                    } catch (e: Exception) {
                        if (!isRunning || !isActive) break
                        lastSendError = "${e.javaClass.simpleName}: ${e.message}"
                        Log.e(TAG, "Error in send loop: ${e.message}", e)
                    }
                }
            }

            Log.d(TAG, "UdpVoiceSocket started on port $localPort (wifiBound=${network != null})")
            true
        } catch (e: Exception) {
            val msg = "Failed to bind UDP port $localPort: ${e.message}"
            Log.e(TAG, msg, e)
            onError(msg)
            close()
            false
        }
    }

    /**
     * Enqueues an audio packet to be sent via UDP to the specified destination.
     */
    fun sendPacket(packet: AudioPacket, targetIp: String, targetPort: Int) {
        if (!isRunning) return
        val raw = packet.toByteArray()
        sendChannel.trySend(OutgoingDatagram(raw, targetIp, targetPort))
    }

    /**
     * Closes the socket and cancels background coroutines.
     */
    fun close() {
        isRunning = false
        receiveJob?.cancel()
        receiveJob = null

        sendJob?.cancel()
        sendJob = null

        try {
            socket?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing DatagramSocket: ${e.message}")
        }
        socket = null
    }
}
