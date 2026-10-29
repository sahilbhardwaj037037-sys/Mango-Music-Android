package com.mangomusic

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.exoplayer.audio.AudioSink
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mangomusic.playback.PcmProbeAudioSink
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.reflect.Proxy
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PcmProbeTest {
    @Test fun fingerprintsPartialWritesWithoutChangingAudio() {
        val diagnostics=(InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as MangoApp).audioDiagnostics
        diagnostics.clear()
        val original=ByteArray(128) { (it*7).toByte() }
        val output=java.io.ByteArrayOutputStream()
        val delegate=Proxy.newProxyInstance(AudioSink::class.java.classLoader,arrayOf(AudioSink::class.java)) { _, method, args ->
            when(method.name) {
                "handleBuffer" -> {
                    val buffer=args!![0] as ByteBuffer
                    val count=minOf(buffer.remaining(),32)
                    val bytes=ByteArray(count);buffer.get(bytes);output.write(bytes)
                    !buffer.hasRemaining()
                }
                else -> null
            }
        } as AudioSink
        val probe=PcmProbeAudioSink(delegate,diagnostics)
        probe.configure(Format.Builder().setSampleMimeType("audio/raw").setPcmEncoding(C.ENCODING_PCM_16BIT).setSampleRate(44100).setChannelCount(2).build(),0,null)
        val input=ByteBuffer.wrap(original.copyOf()).order(ByteOrder.LITTLE_ENDIAN)
        while(input.hasRemaining())probe.handleBuffer(input,0,1)
        probe.flush()
        assertArrayEquals("PCM reaching Android must be unchanged",original,output.toByteArray())
        assertArrayEquals("Probe must not mutate PCM samples",original,input.array())
        val rows=diagnostics.snapshot()
        val window=(0 until rows.length()).map { rows.getJSONObject(it) }.single { it.getString("event")=="pcmWindow" }
        assertEquals(original.size,window.getInt("bytes"))
        assertEquals(MessageDigest.getInstance("SHA-256").digest(original).joinToString("") { "%02x".format(it) },window.getString("sha256"))
    }
    @Test fun distinguishesTinyRoundingFromLargeSampleDifferences() {
        val diagnostics=(InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as MangoApp).audioDiagnostics
        diagnostics.clear()
        val delegate=Proxy.newProxyInstance(AudioSink::class.java.classLoader,arrayOf(AudioSink::class.java)) { _, method, args ->
            if(method.name=="handleBuffer") { val b=args!![0] as ByteBuffer;b.position(b.limit());true } else null
        } as AudioSink
        val probe=PcmProbeAudioSink(delegate,diagnostics)
        probe.configure(Format.Builder().setSampleMimeType("audio/raw").setPcmEncoding(C.ENCODING_PCM_16BIT).setSampleRate(44100).setChannelCount(2).build(),0,null)
        val first=ByteBuffer.allocate(128).order(ByteOrder.LITTLE_ENDIAN).apply { repeat(64) { putShort((1000+it).toShort()) };flip() }
        probe.setOutputStreamOffsetUs(0)
        probe.handleBuffer(first,0,1)
        probe.setOutputStreamOffsetUs(1000000)
        val repeat=ByteBuffer.wrap(first.array().copyOf()).order(ByteOrder.LITTLE_ENDIAN).apply { putShort(10,1006.toShort()) }
        probe.handleBuffer(repeat,1000000,1)
        probe.flush()
        val rows=diagnostics.snapshot()
        val comparison=(0 until rows.length()).map { rows.getJSONObject(it) }.last { it.getString("event")=="pcmWindow" }
        assertEquals(64,comparison.getInt("comparedSamples"))
        assertEquals(1,comparison.getInt("differentSamples"))
        assertEquals(1,comparison.getInt("maxSampleDelta"))
    }
}
