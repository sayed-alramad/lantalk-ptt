package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.audio.AudioPacket
import com.example.audio.AudioPacketConstants
import com.example.util.NetworkUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("LAN Talkie", appName)
    }

    @Test
    fun `validate ipv4 addresses`() {
        assertTrue(NetworkUtils.isValidIpv4("192.168.1.1"))
        assertTrue(NetworkUtils.isValidIpv4("10.0.0.1"))
        assertTrue(NetworkUtils.isValidIpv4("172.16.0.50"))
        assertFalse(NetworkUtils.isValidIpv4("256.0.0.1"))
        assertFalse(NetworkUtils.isValidIpv4("abc.def.ghi.jkl"))
        assertFalse(NetworkUtils.isValidIpv4(""))
    }

    @Test
    fun `validate audio packet framing`() {
        val pcm = ByteArray(AudioPacketConstants.PCM_FRAME_SIZE) { 42 }
        val packet = AudioPacket(sequenceNumber = 105, pcmData = pcm)
        val serialized = packet.toByteArray()

        assertEquals(AudioPacketConstants.PACKET_SIZE, serialized.size)

        val deserialized = AudioPacket.fromByteArray(serialized, serialized.size)
        assertNotNull(deserialized)
        assertEquals(105, deserialized!!.sequenceNumber)
        assertEquals(AudioPacketConstants.PCM_FRAME_SIZE, deserialized.pcmData.size)
        assertEquals(42.toByte(), deserialized.pcmData[0])
    }
}
