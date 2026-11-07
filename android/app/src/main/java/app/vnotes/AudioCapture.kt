package app.vnotes

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.math.sqrt

/** Native mono PCM capture. Test mode never retains PCM or invokes the model. */
class AudioCapture {
    private val running = AtomicBoolean(false)
    private val cancelled = AtomicBoolean(false)
    @Volatile private var recorder: AudioRecord? = null
    @Volatile private var reason = "manual"
    val active get() = running.get()
    @SuppressLint("MissingPermission")
    fun start(test: Boolean, silence: Double, threshold: Double,
              onLevel: (Double, Double, Double) -> Unit,
              onDone: (FloatArray?, Boolean, String) -> Unit, onError: (Throwable) -> Unit,
              onAudio: (FloatArray) -> Unit = {}) {
        check(!running.get()) { "Microphone is already running" }
        val minimum = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        check(minimum > 0) { "16 kHz microphone input is unavailable ($minimum)" }
        val input = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, RATE,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minimum * 2, 4096))
        if (input.state != AudioRecord.STATE_INITIALIZED) { input.release(); error("Android could not initialize the microphone") }
        recorder = input; cancelled.set(false); reason = "manual"
        try { input.startRecording(); check(input.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "Microphone could not start" } }
        catch (e: Throwable) { input.release(); recorder = null; throw e }
        running.set(true)
        thread(name = "VNotes microphone") {
            val vad = EnergyVad(RATE, silence, threshold)
            val pcm = if (test) null else FloatArray(RATE * 120)
            val block = ShortArray(512); var size = 0; var failure: Throwable? = null
            try {
                while (running.get()) {
                    val count = input.read(block, 0, block.size, AudioRecord.READ_BLOCKING)
                    if (!running.get()) break
                    check(count > 0) { "Microphone read failed ($count). Check Android microphone access." }
                    val previousSize = size
                    var energy = 0.0
                    for (i in 0 until count) {
                        val value = block[i] / 32768f; energy += value * value
                        if (pcm != null && size < pcm.size) pcm[size++] = value
                    }
                    if (!test && size > previousSize) onAudio(pcm!!.copyOfRange(previousSize, size))
                    val rms = sqrt(energy / count); val autoStop = vad.push(rms, count)
                    onLevel(rms, vad.totalMs / 1000, vad.quietMs / 1000)
                    if (!test && (autoStop || size == pcm!!.size)) { reason = if (autoStop) "silence" else "limit"; running.set(false) }
                }
            } catch (e: Throwable) { if (running.get()) failure = e }
            finally {
                running.set(false)
                try { input.stop() } catch (_: Exception) {}
                input.release(); recorder = null
            }
            if (failure != null) onError(failure)
            else {
                val usable = vad.heard || (reason == "manual" && vad.peak > .0005 && vad.totalMs >= 150)
                onDone(if (cancelled.get() || test) null else pcm!!.copyOf(size), usable, reason)
            }
        }
    }
    fun stop(cancel: Boolean = false, cause: String = "manual") {
        if (cancel) cancelled.set(true)
        reason = cause; running.set(false)
        try { recorder?.stop() } catch (_: Exception) {}
    }
    companion object { const val RATE = 16000 }
}
