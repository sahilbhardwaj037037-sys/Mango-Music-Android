package com.mangomusic.playback

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

/** Opt-in instrumentation probe. Reads consumed PCM without changing it or saving audio. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class PcmProbeAudioSink(sink: AudioSink, private val diagnostics: AudioPlaybackDiagnostics): ForwardingAudioSink(sink) {
    private var rate=0
    private var channels=0
    private var encoding=0
    private var referenceSelection=-1
    private var referenceOffset=0L
    private var replay=false
    private val references=mutableMapOf<Int,ByteArray>()
    private var captured=ByteArray(0)
    private var differentSamples=0
    private var maxSampleDelta=0
    private var comparedSamples=0
    private var offset=0L
    private var generation=0
    private var window=0
    private var bytes=0
    private var peak=0
    private var clipped=0
    private var squares=0.0
    private val hash=MessageDigest.getInstance("SHA-256")
    override fun configure(format: Format, specifiedBufferSize: Int, outputChannels: IntArray?) {
        finish()
        rate=format.sampleRate;channels=format.channelCount;encoding=format.pcmEncoding
        super.configure(format,specifiedBufferSize,outputChannels)
    }
    override fun setOutputStreamOffsetUs(streamOffsetUs: Long) {
        finish()
        val selection=diagnostics.selectionNumber()
        if(selection!=referenceSelection) {
            references.clear();referenceSelection=selection;referenceOffset=streamOffsetUs;replay=false
        } else replay=streamOffsetUs!=referenceOffset
        offset=streamOffsetUs;generation++;window=0
        diagnostics.record("pcmStream","generation" to generation,"offsetUs" to offset)
        super.setOutputStreamOffsetUs(streamOffsetUs)
    }
    override fun handleBuffer(buffer: ByteBuffer, presentationTimeUs: Long, encodedAccessUnitCount: Int): Boolean {
        val probe=buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        val before=buffer.position()
        val handled=super.handleBuffer(buffer,presentationTimeUs,encodedAccessUnitCount)
        probe.position(before);probe.limit(buffer.position())
        if (encoding==C.ENCODING_PCM_16BIT && rate>0 && channels>0) {
            val windowBytes=rate*channels*2
            while(probe.hasRemaining()) {
                val count=minOf(probe.remaining(),windowBytes-bytes)
                val chunk=probe.slice().order(ByteOrder.LITTLE_ENDIAN).apply { limit(count) }
                hash.update(chunk.duplicate())
                // Reference PCM stays in RAM only, bounded to 180 seconds, and is never written out.
                if(!replay && window<180) {
                    if(captured.size!=windowBytes)captured=ByteArray(windowBytes)
                    chunk.duplicate().get(captured,bytes,count)
                }
                val reference=if(replay) references[window]?.let { ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN) } else null
                while(chunk.remaining()>=2) {
                    val sample=chunk.short.toInt();val magnitude=kotlin.math.abs(sample)
                    peak=maxOf(peak,magnitude);if(magnitude>=32760)clipped++
                    squares+=sample.toDouble()*sample
                    val sampleByte=bytes+chunk.position()-2
                    if(reference!=null && sampleByte+2<=reference.limit()) {
                        val delta=kotlin.math.abs(sample-reference.getShort(sampleByte).toInt())
                        comparedSamples++;if(delta>0)differentSamples++
                        maxSampleDelta=maxOf(maxSampleDelta,delta)
                    }
                }
                bytes+=count;probe.position(probe.position()+count)
                if(bytes==windowBytes)finish()
            }
        }
        return handled
    }
    override fun flush() { finish();generation++;window=0;super.flush() }
    override fun reset() { finish();super.reset() }
    override fun release() { finish();references.clear();captured=ByteArray(0);super.release() }
    private fun finish() {
        if (bytes>0) {
            diagnostics.record("pcmWindow","generation" to generation,"offsetUs" to offset,"window" to window++,
                "bytes" to bytes,"sha256" to hash.digest().joinToString("") { "%02x".format(it) },"peak" to peak,"nearFullScaleSamples" to clipped,
                "rms" to kotlin.math.sqrt(squares/(bytes/2)),"comparedSamples" to comparedSamples,
                "differentSamples" to differentSamples,"maxSampleDelta" to maxSampleDelta)
            if(!replay && window<=180 && captured.size>=bytes) references[window-1]=captured.copyOf(bytes)
        }
        hash.reset();bytes=0;peak=0;clipped=0;squares=0.0;differentSamples=0;maxSampleDelta=0;comparedSamples=0
    }
}
