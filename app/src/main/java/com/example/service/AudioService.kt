package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Network
import android.net.wifi.WifiManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.audio.AudioCaptureEngine
import com.example.audio.AudioPlaybackEngine
import com.example.model.ServiceStatus
import com.example.network.UdpVoiceSocket
import com.example.util.NetworkUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * Foreground Service that maintains real-time P2P UDP audio streaming even when the screen is locked.
 * Manages AudioCaptureEngine, AudioPlaybackEngine, and UdpVoiceSocket.
 */
class AudioService : Service() {

    companion object {
        private const val TAG = "AudioService"
        const val CHANNEL_ID = "lan_talkie_ptt_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.example.service.ACTION_START"
        const val ACTION_STOP = "com.example.service.ACTION_STOP"
        const val EXTRA_PORT = "EXTRA_PORT"
        const val EXTRA_REMOTE_IP = "EXTRA_REMOTE_IP"
        const val EXTRA_REMOTE_PORT = "EXTRA_REMOTE_PORT"
        const val EXTRA_BIND_WIFI = "EXTRA_BIND_WIFI"
    }

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var captureEngine: AudioCaptureEngine? = null
    private var playbackEngine: AudioPlaybackEngine? = null
    private var voiceSocket: UdpVoiceSocket? = null

    private var wakeLock: PowerManager.WakeLock? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    // Audio routing state (restored when the service stops)
    private var routedAudioManager: AudioManager? = null
    private var previousAudioMode: Int = AudioManager.MODE_NORMAL
    private var previousSpeakerphone: Boolean = false

    private var statsJob: Job? = null
    private var selfTestJob: Job? = null

    @Volatile
    private var selfTestResult: String = ""
    private var boundToWifi: Boolean = false

    private val _status = MutableStateFlow(ServiceStatus.IDLE)
    val status: StateFlow<ServiceStatus> = _status.asStateFlow()

    private val _localIp = MutableStateFlow<String?>(null)
    val localIp: StateFlow<String?> = _localIp.asStateFlow()

    private val _remoteIp = MutableStateFlow("")
    val remoteIp: StateFlow<String> = _remoteIp.asStateFlow()

    private val _port = MutableStateFlow(8888)
    val port: StateFlow<Int> = _port.asStateFlow()

    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    // One-shot events (a SharedFlow, so the same message is shown again if the problem repeats)
    private val _errorMessage = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val errorMessage: SharedFlow<String> = _errorMessage.asSharedFlow()

    // Live packet counters, shown on screen to see where a link breaks
    private val _diagnostics = MutableStateFlow("")
    val diagnostics: StateFlow<String> = _diagnostics.asStateFlow()

    inner class LocalBinder : Binder() {
        fun getService(): AudioService = this@AudioService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireLocks()
        refreshLocalIp()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopPttService()
                stopSelf()
            }
            ACTION_START -> {
                val listenPort = intent.getIntExtra(EXTRA_PORT, 8888)
                val targetIp = intent.getStringExtra(EXTRA_REMOTE_IP) ?: ""
                val targetPort = intent.getIntExtra(EXTRA_REMOTE_PORT, listenPort)
                val bindWifi = intent.getBooleanExtra(EXTRA_BIND_WIFI, false)
                startPttService(listenPort, targetIp, targetPort, bindWifi)
            }
        }
        return START_NOT_STICKY
    }

    fun refreshLocalIp() {
        val detected = NetworkUtils.getPreferredLocalIpv4()
        _localIp.value = detected
    }

    private fun postError(message: String) {
        _errorMessage.tryEmit(message)
    }

    /**
     * Starts listening on the specified UDP port and begins foreground execution.
     */
    fun startPttService(listenPort: Int, targetIp: String, targetPort: Int, bindWifi: Boolean = false) {
        if (_status.value.isActive) {
            stopPttService()
        }

        _port.value = listenPort
        _remoteIp.value = targetIp

        startForegroundNotification("Listening on port $listenPort")

        // 0. Send received voice to the loudspeaker (not the earpiece) at a usable volume
        routeAudioToSpeaker()

        // 1. Initialize Playback Engine
        playbackEngine = AudioPlaybackEngine(
            onPlaybackActivityChanged = { isReceiving ->
                if (_status.value != ServiceStatus.TRANSMITTING) {
                    val newStatus = if (isReceiving) ServiceStatus.RECEIVING else ServiceStatus.LISTENING
                    _status.value = newStatus
                    updateNotification(if (isReceiving) "Receiving audio..." else "Listening on port $listenPort")
                }
            },
            onAmplitudeChanged = { amp ->
                if (_status.value == ServiceStatus.RECEIVING) {
                    _amplitude.value = amp
                }
            }
        ).apply {
            if (!initialize()) {
                postError("Failed to initialize audio playback track.")
                stopPttService()
                return
            }
            startPlayback(serviceScope)
        }

        // 2. Initialize UDP Socket (bound to the Wi-Fi network when this phone is a Wi-Fi client)
        val wifiNetwork = NetworkUtils.findWifiNetwork(this)
        boundToWifi = bindWifi && wifiNetwork != null

        voiceSocket = UdpVoiceSocket(
            localPort = listenPort,
            network = if (bindWifi) wifiNetwork else null,
            onPacketReceived = { packet ->
                // Feed incoming voice packets directly into the jitter buffer
                serviceScope.launch {
                    playbackEngine?.enqueuePacket(packet)
                }
            },
            onError = { err ->
                postError(err)
                stopPttService()
            }
        ).apply {
            if (!start(serviceScope)) {
                postError("Failed to bind UDP port $listenPort. It may be in use.")
                stopPttService()
                return
            }
        }

        // 3. Initialize Capture Engine (ready for Push-to-Talk)
        captureEngine = AudioCaptureEngine(
            onPacketReady = { packet ->
                val ip = _remoteIp.value
                val p = _port.value
                if (NetworkUtils.isValidIpv4(ip)) {
                    voiceSocket?.sendPacket(packet, ip, p)
                }
            },
            onAmplitudeChanged = { amp ->
                if (_status.value == ServiceStatus.TRANSMITTING) {
                    _amplitude.value = amp
                }
            }
        ).apply {
            initialize()
        }

        startStatsTicker()
        runSelfTest(listenPort, wifiNetwork)

        _status.value = ServiceStatus.LISTENING
        updateNotification("Listening on port $listenPort")
    }

    /**
     * Updates the remote endpoint for sending.
     */
    fun updateRemoteEndpoint(targetIp: String, targetPort: Int) {
        _remoteIp.value = targetIp
        _port.value = targetPort
    }

    /**
     * Activates or deactivates microphone capture during Push-to-Talk button press/release.
     */
    fun setTransmitting(active: Boolean) {
        if (!_status.value.isActive) return

        if (active) {
            val target = _remoteIp.value
            if (!NetworkUtils.isValidIpv4(target)) {
                postError("Enter a valid remote IPv4 address before transmitting.")
                return
            }
            val started = captureEngine?.startCapturing(serviceScope) ?: false
            if (!started) {
                postError("Could not start the microphone. Close other apps that use the mic (calls, recorders) and try again.")
                return
            }
            _status.value = ServiceStatus.TRANSMITTING
            updateNotification("Transmitting to $target:${_port.value}...")
        } else {
            captureEngine?.stopCapturing()
            _amplitude.value = 0f
            _status.value = ServiceStatus.LISTENING
            updateNotification("Listening on port ${_port.value}")
        }
    }

    /**
     * Stops the audio service and releases all resources.
     */
    fun stopPttService() {
        statsJob?.cancel()
        statsJob = null
        selfTestJob?.cancel()
        selfTestJob = null
        selfTestResult = ""
        _diagnostics.value = ""

        captureEngine?.release()
        captureEngine = null

        playbackEngine?.release()
        playbackEngine = null

        voiceSocket?.close()
        voiceSocket = null

        restoreAudioRouting()

        _status.value = ServiceStatus.IDLE
        _amplitude.value = 0f

        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    /**
     * Publishes TX/RX packet counters twice a second.
     * TX = packets this phone sent, RX = packets that arrived from the other phone.
     */
    private fun startStatsTicker() {
        statsJob?.cancel()
        statsJob = serviceScope.launch {
            while (isActive) {
                val socket = voiceSocket
                _diagnostics.value = if (socket == null) {
                    ""
                } else {
                    buildString {
                        append("TX ${socket.packetsSent}   RX ${socket.packetsReceived}")
                        socket.lastPeer?.let { append("   from $it") }
                        append("\nListening on: ${socket.localAddressText}")
                        append("\nRoute: ")
                        append(if (boundToWifi) "Wi-Fi (bound)" else "default")
                        if (selfTestResult.isNotEmpty()) append("\nSelf-test: $selfTestResult")
                        socket.lastSendError?.let { append("\nSend error: $it") }
                        socket.lastReceiveError?.let { append("\nReceive error: $it") }
                    }
                }
                delay(500)
            }
        }
    }

    /**
     * Sends a 1-byte test datagram to this phone's own listening port and checks whether the
     * main socket receives it. Tells apart "app cannot receive" from "network drops the packets":
     *  - loopback  : 127.0.0.1, never touches Wi-Fi
     *  - own-IP    : this phone's Wi-Fi address, normal routing
     *  - own-IP(wifi): same, but the test socket is bound to the Wi-Fi network
     */
    private fun runSelfTest(port: Int, wifi: Network?) {
        selfTestJob?.cancel()
        selfTestResult = "running..."
        selfTestJob = serviceScope.launch(Dispatchers.IO) {
            delay(500)
            val main = voiceSocket ?: return@launch
            val parts = mutableListOf<String>()
            parts.add("loopback " + probeSelf(main, "127.0.0.1", port, null))
            val ownIp = NetworkUtils.getPreferredLocalIpv4()
            if (ownIp != null) {
                parts.add("own-IP " + probeSelf(main, ownIp, port, null))
                if (wifi != null) {
                    parts.add("own-IP(wifi) " + probeSelf(main, ownIp, port, wifi))
                }
            } else {
                parts.add("no local IP")
            }
            selfTestResult = parts.joinToString("  |  ")
        }
    }

    private suspend fun probeSelf(main: UdpVoiceSocket, ip: String, port: Int, network: Network?): String {
        val testSocket = DatagramSocket()
        return try {
            val before = main.packetsReceived
            if (network != null) {
                network.bindSocket(testSocket)
            }
            val data = byteArrayOf(0x7F)
            testSocket.send(DatagramPacket(data, data.size, InetAddress.getByName(ip), port))
            delay(400)
            if (main.packetsReceived > before) "OK" else "FAIL"
        } catch (e: Exception) {
            "ERR(${e.javaClass.simpleName})"
        } finally {
            testSocket.close()
        }
    }

    /**
     * USAGE_VOICE_COMMUNICATION audio is sent to the quiet earpiece unless the phone is in
     * communication mode with the speaker forced on. Do that, and make sure the voice-call
     * volume is not near zero.
     */
    private fun routeAudioToSpeaker() {
        try {
            val am = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            routedAudioManager = am
            previousAudioMode = am.mode
            am.mode = AudioManager.MODE_IN_COMMUNICATION

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val speaker = am.availableCommunicationDevices.firstOrNull {
                    it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                }
                if (speaker != null) {
                    am.setCommunicationDevice(speaker)
                }
            } else {
                @Suppress("DEPRECATION")
                previousSpeakerphone = am.isSpeakerphoneOn
                @Suppress("DEPRECATION")
                am.isSpeakerphoneOn = true
            }

            val maxVolume = am.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
            val currentVolume = am.getStreamVolume(AudioManager.STREAM_VOICE_CALL)
            val minimumVolume = (maxVolume * 0.7f).toInt().coerceAtLeast(1)
            if (currentVolume < minimumVolume) {
                am.setStreamVolume(AudioManager.STREAM_VOICE_CALL, minimumVolume, 0)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not route audio to speaker: ${e.message}")
        }
    }

    private fun restoreAudioRouting() {
        val am = routedAudioManager ?: return
        routedAudioManager = null
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                am.clearCommunicationDevice()
            } else {
                @Suppress("DEPRECATION")
                am.isSpeakerphoneOn = previousSpeakerphone
            }
            am.mode = previousAudioMode
        } catch (e: Exception) {
            Log.w(TAG, "Could not restore audio routing: ${e.message}")
        }
    }

    private fun acquireLocks() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = powerManager?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "LANTalkie::VoiceStreamWakeLock"
            )?.apply {
                acquire(24 * 60 * 60 * 1000L) // 24 hours max safeguard
            }

            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            multicastLock = wifiManager?.createMulticastLock("LANTalkie::MulticastLock")?.apply {
                setReferenceCounted(true)
                acquire()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to acquire wake/multicast lock: ${e.message}")
        }
    }

    private fun releaseLocks() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
            wakeLock = null

            if (multicastLock?.isHeld == true) {
                multicastLock?.release()
            }
            multicastLock = null
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing wake/multicast locks: ${e.message}")
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "LAN Talkie Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Provides continuous background peer-to-peer LAN audio transmission and playback."
            setShowBadge(false)
        }
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager?.createNotificationChannel(channel)
    }

    private fun buildNotification(statusText: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("LAN Talkie Active")
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun startForegroundNotification(statusText: String) {
        val notification = buildNotification(statusText)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(statusText: String) {
        if (!_status.value.isActive) return
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager?.notify(NOTIFICATION_ID, buildNotification(statusText))
    }

    override fun onDestroy() {
        super.onDestroy()
        stopPttService()
        releaseLocks()
        serviceScope.cancel()
    }
}
