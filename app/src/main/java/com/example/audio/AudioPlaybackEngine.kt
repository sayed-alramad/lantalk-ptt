package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.PriorityQueue
import kotlin.math.abs

/**
 * Handles low-latency audio playback using AudioTrack in streaming mode.
 * Features an adaptive 3-5 packet jitter buffer that drops late or out-of-order frames
 * and recovers gracefully when new transmission sessions begin.
 */
class AudioPlaybackEngine(
    private val onPlaybackActivityChanged: ((Boolean) -> Unit)? = null,
    private val onAmplitudeChanged: ((Float) -> Unit)? = null
) {
    companion object {
        private const val TAG = "AudioPlaybackEngine"
        private const val PREBUFFER_THRESHOLD = 3
        private const val MAX_JITTER_BUFFER = 5
        private const val INACTIVITY_TIMEOUT_MS = 350L
    }

    private var audioTrack: AudioTrack? = null
    private var playbackJob: Job? = null

    private val jitterBuffer = PriorityQueue<AudioPacket>(compareBy { it.sequenceNumber })
    private val bufferMutex = Mutex()

    private var lastPlayedSequence: Int? = null
    private var lastReceivedTimestamp: Long = 0L

    @Volatile
    private var isPlaying: Boolean = false

    /**
     * Initializes the low-latency AudioTrack.
     */
    fun initialize(): Boolean {
        release()

        val sampleRate = AudioPacketConstants.SAMPLE_RATE
        val channelConfig = AudioFormat.CHANNEL_OUT_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT

        val minBufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        if (minBufferSize <= 0) {
            Log.e(TAG, "Invalid AudioTrack buffer size: $minBufferSize")
            return false
        }

        val bufferSize = maxOf(minBufferSize * 2, AudioPacketConstants.PCM_FRAME_SIZE * 4)

        return try {
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            val format = AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setChannelMask(channelConfig)
                .setEncoding(audioFormat)
                .build()

            val track = AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(format)
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .build()

            if (track.state != AudioTrack.STATE_INITIALIZED) {
                Log.e(TAG, "AudioTrack failed to initialize")
                track.release()
                return false
            }

            audioTrack = track
            true
        } catch (e: Exception) {
            Log.e(TAG, "Exception initializing AudioTrack: ${e.message}", e)
            false
        }
    }

    /**
     * Starts the playback consumer loop.
     */
    fun startPlayback(scope: CoroutineScope) {
        if (isPlaying) return

        val track = audioTrack ?: run {
            if (!initialize()) return
            audioTrack ?: return
        }

        try {
            track.play()
            isPlaying = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start AudioTrack playback: ${e.message}", e)
            return
        }

        playbackJob = scope.launch(Dispatchers.IO) {
            var isReceivingActive = false

            while (isActive && isPlaying) {
                val now = SystemClock.elapsedRealtime()
                val isTimedOut = (now - lastReceivedTimestamp) > INACTIVITY_TIMEOUT_MS

                if (isTimedOut && isReceivingActive) {
                    isReceivingActive = false
                    onPlaybackActivityChanged?.invoke(false)
                    onAmplitudeChanged?.invoke(0f)
                    bufferMutex.withLock {
                        jitterBuffer.clear()
                        lastPlayedSequence = null
                    }
                }

                var packetToPlay: AudioPacket? = null
                bufferMutex.withLock {
                    val currentSize = jitterBuffer.size
                    // Wait until we have at least PREBUFFER_THRESHOLD packets to start,
                    // or immediately consume if transmission is already active
                    if (currentSize >= PREBUFFER_THRESHOLD || (isReceivingActive && currentSize > 0)) {
                        packetToPlay = jitterBuffer.poll()
                    }
                }

                if (packetToPlay != null) {
                    if (!isReceivingActive) {
                        isReceivingActive = true
                        onPlaybackActivityChanged?.invoke(true)
                    }

                    val pcm = packetToPlay!!.pcmData
                    track.write(pcm, 0, pcm.size)
                    lastPlayedSequence = packetToPlay!!.sequenceNumber

                    // Compute normalized peak amplitude
                    onAmplitudeChanged?.let { callback ->
                        var maxAmp = 0
                        val bb = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN)
                        while (bb.remaining() >= 2) {
                            val sample = abs(bb.short.toInt())
                            if (sample > maxAmp) maxAmp = sample
                        }
                        val normalized = (maxAmp / 32767f).coerceIn(0f, 1f)
                        callback(normalized)
                    }
                } else {
                    // Small yield when buffer is empty to avoid busy-spinning
                    delay(5)
                }
            }
        }
    }

    /**
     * Enqueues a newly arrived network packet into the jitter buffer.
     * Drops late or out-of-order packets.
     */
    suspend fun enqueuePacket(packet: AudioPacket) {
        lastReceivedTimestamp = SystemClock.elapsedRealtime()

        bufferMutex.withLock {
            val last = lastPlayedSequence

            if (last != null) {
                val diff = packet.sequenceNumber - last

                // If sequence difference is negative, packet arrived late unless the sender
                // started a new transmission session with reset sequence number
                if (diff <= 0) {
                    val isNewSession = diff < -200 || (packet.sequenceNumber < 10 && last > 50)
                    if (isNewSession) {
                        Log.d(TAG, "Detected new sender transmission session. Resetting jitter buffer.")
                        jitterBuffer.clear()
                        lastPlayedSequence = null
                    } else {
                        // Late packet: drop to preserve low-latency audio sync
                        return@withLock
                    }
                }
            }

            // Enqueue packet
            jitterBuffer.add(packet)

            // Cap jitter buffer size at MAX_JITTER_BUFFER (drop oldest) to avoid latency lag
            while (jitterBuffer.size > MAX_JITTER_BUFFER) {
                jitterBuffer.poll()
            }
        }
    }

    /**
     * Stops playback and clears buffers.
     */
    fun stopPlayback() {
        isPlaying = false
        playbackJob?.cancel()
        playbackJob = null

        try {
            audioTrack?.pause()
            audioTrack?.flush()
        } catch (e: Exception) {
            Log.w(TAG, "Error pausing AudioTrack: ${e.message}")
        }

        jitterBuffer.clear()
        lastPlayedSequence = null
        onPlaybackActivityChanged?.invoke(false)
        onAmplitudeChanged?.invoke(0f)
    }

    /**
     * Releases AudioTrack hardware resources.
     */
    fun release() {
        stopPlayback()
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (_: Exception) {}
        audioTrack = null
    }
}
