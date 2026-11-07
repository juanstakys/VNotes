package app.vnotes

import android.content.Context
import ai.moonshine.voice.AssetDownloader
import ai.moonshine.voice.JNI
import ai.moonshine.voice.ModelSpec
import ai.moonshine.voice.TranscriptEvent
import ai.moonshine.voice.TranscriptLine
import ai.moonshine.voice.Transcriber
import ai.moonshine.voice.TranscriberOption
import java.io.File

/** Call only on the single speech executor. Native inference never receives network audio. */
class SpeechEngine(context: Context) {
    private val root=File(context.filesDir,"models/moonshine-tiny-en")
    private val spec=ModelSpec.stt("en",JNI.MOONSHINE_MODEL_ARCH_TINY,false)
    private val downloader=AssetDownloader()
    private var engine: Transcriber?=null
    fun present() = downloader.isModelPresent(root,spec)
    fun setup(progress:(String)->Unit) {
        root.mkdirs()
        downloader.ensureModelPresent(root,spec) { name, index, total, done, bytes ->
            progress("$index/$total · $name" + if(bytes>0) " · ${done*100/bytes}%" else "")
        }
        load();progress("Voice ready offline")
    }
    private fun load():Transcriber {
        engine?.let{return it}
        check(present()){"Download the English model first"}
        val transcriber=Transcriber(listOf(TranscriberOption("decode_incomplete_lines", "true")))
        try {transcriber.loadFromFiles(root.absolutePath,JNI.MOONSHINE_MODEL_ARCH_TINY)}catch(e:Throwable){transcriber.close();throw e}
        engine=transcriber;return transcriber
    }
    private var liveTranscript: LiveTranscript? = null
    fun startLive(onText: (String) -> Unit) {
        val transcriber = load()
        val transcript = LiveTranscript(); liveTranscript = transcript
        var previous = ""
        transcriber.removeAllListeners()
        transcriber.setUpdateInterval(.5) // SDK adapts the cadence to actual inference cost.
        transcriber.addListener { event ->
            val line: TranscriptLine? = when (event) {
                is TranscriptEvent.LineStarted -> event.line
                is TranscriptEvent.LineUpdated -> event.line
                is TranscriptEvent.LineTextChanged -> event.line
                is TranscriptEvent.LineCompleted -> event.line
                is TranscriptEvent.Error -> throw event.cause
                else -> null
            }
            if (line != null) {
                transcript.update(line.id, line.startTime, line.text.orEmpty())
                val text = transcript.text
                if (text != previous) { previous = text; onText(text) }
            }
        }
        transcriber.start()
    }
    fun feedLive(audio: FloatArray) {
        check(liveTranscript != null) { "Live transcription has not started" }
        checkNotNull(engine).addAudio(audio, AudioCapture.RATE)
    }
    fun finishLive(): String {
        try {
            checkNotNull(engine).stop() // Force a final pass, including the trailing partial phrase.
            return checkNotNull(liveTranscript).text.also { check(it.isNotBlank()) { "No words detected" } }
        } finally { abortLive() }
    }
    fun abortLive() {
        liveTranscript = null
        // Close per take: SDK completed-line events retain PCM; do not accumulate them across notes.
        val old = engine; engine = null
        old?.removeAllListeners(); old?.close()
    }
    fun transcribe(audio:FloatArray, progress:(String)->Unit):String {
        val transcriber=load();val parts=ArrayList<String>();val ranges=splitAudio(audio)
        ranges.forEachIndexed { index, range ->
            progress("Transcribing ${index+1}/${ranges.size}…")
            val text=transcriber.transcribeWithoutStreaming(audio.copyOfRange(range.first,range.last+1),AudioCapture.RATE).text().trim()
            if(text.isNotBlank())parts.add(text)
        }
        return parts.joinToString(" ").also{check(it.isNotBlank()){"No words detected. Retry or discard the recording."}}
    }
    fun close(){abortLive()}
}
