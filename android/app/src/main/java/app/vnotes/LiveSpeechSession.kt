package app.vnotes

import java.util.concurrent.CancellationException
import java.util.concurrent.Executor

interface LiveSpeech {
    fun start(onText: (String) -> Unit)
    fun feed(audio: FloatArray)
    /** Flush the trailing phrase and release this take’s native resources. */
    fun finish(): String
    fun abort()
}

/**
 * Capture only enqueues immutable PCM. All model work stays on one serial executor.
 * At most one 120-second take can be queued; cancellation skips queued inference immediately.
 */
class LiveSpeechSession(
    private val executor: Executor,
    private val engine: LiveSpeech,
    private val onText: (String) -> Unit,
    private val onError: (Throwable) -> Unit,
    private val maxSamples: Int = 16000 * 120
) {
    private val lock = Any()
    @Volatile private var cancelled = false
    @Volatile private var failure: Throwable? = null
    @Volatile private var finished = false
    private var ending = false
    private var samples = 0
    init {
        executor.execute {
            if (!cancelled) try {
                engine.start { text -> if (!cancelled && !finished && failure == null) onText(text) }
            } catch (e: Throwable) { fail(e) }
        }
    }
    private fun fail(error: Throwable) {
        synchronized(lock) {
            if (failure == null && !cancelled && !finished) { failure = error; onError(error) }
        }
    }
    fun offer(audio: FloatArray) {
        synchronized(lock) {
            if (ending || cancelled || failure != null || audio.isEmpty()) return
            if (audio.size > maxSamples - samples) { fail(IllegalArgumentException("Live recording exceeds two minutes")); return }
            samples += audio.size
            val copy = audio.copyOf()
            executor.execute {
                if (!cancelled && failure == null) try { engine.feed(copy) } catch (e: Throwable) { fail(e) }
            }
        }
    }
    fun finish(onComplete: (Result<String>) -> Unit) {
        synchronized(lock) {
            if (ending) return
            ending = true
            executor.execute {
                val result = try {
                    if (cancelled) throw CancellationException("Live transcription cancelled")
                    failure?.let { throw it }
                    Result.success(engine.finish().also { check(it.isNotBlank()) { "No words detected in live recording" } })
                } catch (e: Throwable) {
                    try { engine.abort() } catch (_: Throwable) {}
                    Result.failure(e)
                }
                finished = true
                onComplete(if (cancelled) Result.failure(CancellationException("Live transcription cancelled")) else result)
            }
        }
    }
    fun cancel() {
        synchronized(lock) {
            if (cancelled || finished) return
            cancelled = true; ending = true
            executor.execute { try { engine.abort() } catch (_: Throwable) {} finally { finished = true } }
        }
    }
}
