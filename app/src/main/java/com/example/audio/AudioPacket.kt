package com.example.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Constants and framing helpers for real-time 20ms UDP audio packets.
 *
 * Format:
 * [0..3]: 4-byte Int sequence number (big-endian)
 * [4..643]: 640 bytes raw linear PCM (16000 Hz, 16-bit mono)
 */
object AudioPacketConstants {
    const val SAMPLE_RATE = 16000
    const val FRAME_DURATION_MS = 20
    const val BYTES_PER_SAMPLE = 2 // 16-bit PCM
    const val CHANNELS = 1 // Mono

    // 16000 samples/s * 1 channel * 2 bytes/sample * 0.02s = 640 bytes
    const val PCM_FRAME_SIZE = (SAMPLE_RATE * BYTES_PER_SAMPLE * CHANNELS * FRAME_DURATION_MS) / 1000
    const val HEADER_SIZE = 4 // 4-byte Int sequence number
    const val PACKET_SIZE = HEADER_SIZE + PCM_FRAME_SIZE // 644 bytes
}

data class AudioPacket(
    val sequenceNumber: Int,
    val pcmData: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as AudioPacket
        if (sequenceNumber != other.sequenceNumber) return false
        return pcmData.contentEquals(other.pcmData)
    }

    override fun hashCode(): Int {
        var result = sequenceNumber
        result = 31 * result + pcmData.contentHashCode()
        return result
    }

    /**
     * Serializes this packet into a 644-byte byte array ready for DatagramSocket transmission.
     */
    fun toByteArray(): ByteArray {
        val buffer = ByteBuffer.allocate(AudioPacketConstants.PACKET_SIZE)
        buffer.order(ByteOrder.BIG_ENDIAN)
        buffer.putInt(sequenceNumber)
        val copyLen = minOf(pcmData.size, AudioPacketConstants.PCM_FRAME_SIZE)
        buffer.put(pcmData, 0, copyLen)
        // If shorter, pad with zero
        if (copyLen < AudioPacketConstants.PCM_FRAME_SIZE) {
            val padding = ByteArray(AudioPacketConstants.PCM_FRAME_SIZE - copyLen)
            buffer.put(padding)
        }
        return buffer.array()
    }

    companion object {
        /**
         * Parses a received UDP datagram buffer into an AudioPacket.
         */
        fun fromByteArray(data: ByteArray, length: Int): AudioPacket? {
            if (length < AudioPacketConstants.HEADER_SIZE) return null
            val buffer = ByteBuffer.wrap(data, 0, length)
            buffer.order(ByteOrder.BIG_ENDIAN)
            val seq = buffer.int
            val audioLength = length - AudioPacketConstants.HEADER_SIZE
            val pcm = ByteArray(audioLength)
            buffer.get(pcm)
            return AudioPacket(sequenceNumber = seq, pcmData = pcm)
        }
    }
}
