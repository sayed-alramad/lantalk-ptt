package com.example.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

/**
 * Handles capturing 16000 Hz, 16-bit mono PCM microphone audio via AudioRecord.
 * Automatically engages AcousticEchoCanceler and NoiseSuppressor hardware effects if supported.
 * Produces discrete 20ms frames (640 bytes) tagged with incremental 4-byte sequence numbers.
 */
class AudioCaptureEngine(
    private val onPacketReady: (AudioPacket) -> Unit,
    private val onAmplitudeChanged: ((Float) -> Unit)? = null
) {
    companion object {
        private const val TAG = "AudioCaptureEngine"
    }

    private var audioRecord: AudioRecord? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null

    private var captureJob: Job? = null
    private var sequenceNumber: Int = 0

    @Volatile
    private var isRecording: Boolean = false

    /**
     * Initializes the AudioRecord instance and hardware acoustic effects.
     */
    @SuppressLint("MissingPermission")
    fun initialize(): Boolean {
        release()

        val sampleRate = AudioPacketConstants.SAMPLE_RATE
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT

        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
            Log.e(TAG, "Invalid AudioRecord buffer size calculation: $minBufferSize")
            return false
        }

        // Buffer size configured to at least 2x minBufferSize as required
        val bufferSize = maxOf(minBufferSize * 2, AudioPacketConstants.PCM_FRAME_SIZE * 4)

        return try {
            val record = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord failed to initialize")
                record.release()
                return false
            }

            val sessionId = record.audioSessionId

            // Enable Acoustic Echo Canceler if hardware effect is available
            if (AcousticEchoCanceler.isAvailable()) {
                try {
                    echoCanceler = AcousticEchoCanceler.create(sessionId)?.apply {
                        enabled = true
                    }
                    Log.d(TAG, "AcousticEchoCanceler initialized and enabled: ${echoCanceler?.enabled}")
                } catch (e: Exception) {
                    Log.w(TAG, "Could not enable AcousticEchoCanceler: ${e.message}")
                }
            }

            // Enable Noise Suppressor if hardware effect is available
            if (NoiseSuppressor.isAvailable()) {
                try {
                    noiseSuppressor = NoiseSuppressor.create(sessionId)?.apply {
                        enabled = true
                    }
                    Log.d(TAG, "NoiseSuppressor initialized and enabled: ${noiseSuppressor?.enabled}")
                } catch (e: Exception) {
                    Log.w(TAG, "Could not enable NoiseSuppressor: ${e.message}")
                }
            }

            audioRecord = record
            sequenceNumber = 0
            true
        } catch (e: Exception) {
            Log.e(TAG, "Exception initializing AudioRecord: ${e.message}", e)
            false
        }
    }

    /**
     * Starts continuous recording and dispatches packets.
     */
    fun startCapturing(scope: CoroutineScope): Boolean {
        if (isRecording) return true

        val record = audioRecord ?: run {
            if (!initialize()) return false
            audioRecord ?: return false
        }

        try {
            record.startRecording()
            if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                Log.e(TAG, "AudioRecord did not enter RECORDING state (microphone busy?)")
                return false
            }
            isRecording = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start AudioRecord: ${e.message}", e)
            return false
        }

        captureJob = scope.launch(Dispatchers.IO) {
            val pcmBuffer = ByteArray(AudioPacketConstants.PCM_FRAME_SIZE)

            while (isActive && isRecording) {
                var bytesRead = 0
                while (bytesRead < AudioPacketConstants.PCM_FRAME_SIZE && isActive && isRecording) {
                    val read = record.read(
                        pcmBuffer,
                        bytesRead,
                        AudioPacketConstants.PCM_FRAME_SIZE - bytesRead
                    )
                    if (read > 0) {
                        bytesRead += read
                    } else if (read < 0) {
                        Log.w(TAG, "AudioRecord read error: $read")
                        break
                    }
                }

                if (bytesRead == AudioPacketConstants.PCM_FRAME_SIZE) {
                    val packet = AudioPacket(
                        sequenceNumber = sequenceNumber++,
                        pcmData = pcmBuffer.copyOf()
                    )
                    onPacketReady(packet)

                    // Compute normalized peak amplitude for VU meter
                    onAmplitudeChanged?.let { callback ->
                        var maxAmp = 0
                        val bb = ByteBuffer.wrap(pcmBuffer).order(ByteOrder.LITTLE_ENDIAN)
                        while (bb.remaining() >= 2) {
                            val sample = abs(bb.short.toInt())
                            if (sample > maxAmp) maxAmp = sample
                        }
                        val normalized = (maxAmp / 32767f).coerceIn(0f, 1f)
                        callback(normalized)
                    }
                }
            }
        }
        return true
    }

    /**
     * Stops capturing microphone audio.
     */
    fun stopCapturing() {
        isRecording = false
        captureJob?.cancel()
        captureJob = null
        try {
            audioRecord?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping AudioRecord: ${e.message}")
        }
        onAmplitudeChanged?.invoke(0f)
    }

    /**
     * Fully releases all hardware audio resources and audio effects.
     */
    fun release() {
        stopCapturing()
        try {
            echoCanceler?.release()
        } catch (_: Exception) {}
        echoCanceler = null

        try {
            noiseSuppressor?.release()
        } catch (_: Exception) {}
        noiseSuppressor = null

        try {
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null
    }
}
