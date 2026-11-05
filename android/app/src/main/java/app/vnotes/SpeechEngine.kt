package app.vnotes

import android.content.Context
import ai.moonshine.voice.AssetDownloader
import ai.moonshine.voice.JNI
import ai.moonshine.voice.ModelSpec
import ai.moonshine.voice.Transcriber
import java.io.File
import kotlin.math.min

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
        val transcriber=Transcriber()
        try {transcriber.loadFromFiles(root.absolutePath,JNI.MOONSHINE_MODEL_ARCH_TINY)}catch(e:Throwable){transcriber.close();throw e}
        engine=transcriber;return transcriber
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
    fun close(){engine?.close();engine=null}
}
