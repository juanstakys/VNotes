package app.vnotes

import org.junit.Assert.*
import org.junit.Test
import java.util.ArrayDeque
import java.util.concurrent.CancellationException
import java.util.concurrent.Executor

class LiveSpeechSessionTest {
    private class SerialQueue : Executor {
        val tasks = ArrayDeque<Runnable>()
        override fun execute(command: Runnable) { tasks.add(command) }
        fun next() { tasks.removeFirst().run() }
        fun drain() { while(tasks.isNotEmpty())next() }
    }
    private class FakeEngine : LiveSpeech {
        val events = ArrayList<String>(); val samples = ArrayList<Float>()
        var onText: ((String)->Unit)? = null
        var failFeed = false
        override fun start(onText: (String) -> Unit) { events.add("start"); this.onText=onText }
        override fun feed(audio: FloatArray) {
            events.add("feed");if(failFeed)error("stream error")
            samples.addAll(audio.toList());onText?.invoke("partial ${samples.size}")
        }
        override fun finish(): String { events.add("finish");onText?.invoke("final words");return "final words" }
        override fun abort() { events.add("abort") }
    }
    @Test fun textBuildsBeforeStopAndFinalFlushFollowsEverySample() {
        val q=SerialQueue();val engine=FakeEngine();val words=ArrayList<String>();var final:Result<String>?=null
        val session=LiveSpeechSession(q,engine,{words.add(it)},{throw it})
        val audio=floatArrayOf(1f,2f);session.offer(audio);audio[0]=99f
        assertTrue(words.isEmpty());q.drain()
        assertEquals(listOf("partial 2"),words);assertEquals(listOf(1f,2f),engine.samples)
        session.offer(floatArrayOf(3f));session.finish{final=it};session.offer(floatArrayOf(4f));q.drain()
        assertEquals(listOf("start","feed","feed","finish"),engine.events)
        assertEquals(listOf(1f,2f,3f),engine.samples);assertEquals("final words",final!!.getOrThrow())
        engine.onText?.invoke("late words");assertEquals("final words",words.last())
        session.cancel();q.drain();assertFalse(engine.events.contains("abort"))
    }
    @Test fun cancellingSkipsQueuedRecognitionAndAllLateText() {
        val q=SerialQueue();val engine=FakeEngine();val words=ArrayList<String>()
        val session=LiveSpeechSession(q,engine,{words.add(it)},{throw it})
        q.next();session.offer(floatArrayOf(1f));session.cancel();session.offer(floatArrayOf(2f));q.drain()
        engine.onText?.invoke("late words")
        assertTrue(words.isEmpty());assertTrue(engine.samples.isEmpty());assertTrue(engine.events.contains("abort"))
    }
    @Test fun cancelDuringFinishingStillCompletesWithoutSavingText() {
        val q=SerialQueue();val engine=FakeEngine();var result:Result<String>?=null
        val session=LiveSpeechSession(q,engine,{},{throw it});q.next()
        session.offer(floatArrayOf(1f));session.finish{result=it};session.cancel();q.drain()
        assertTrue(result!!.exceptionOrNull() is CancellationException)
        assertFalse(engine.events.contains("finish"));assertTrue(engine.events.contains("abort"))
    }
    @Test fun liveFailureReturnsARecoverableResultForTheSavedAudioFallback() {
        val q=SerialQueue();val engine=FakeEngine().apply{failFeed=true};val errors=ArrayList<Throwable>();var result:Result<String>?=null
        val session=LiveSpeechSession(q,engine,{}, {errors.add(it)})
        session.offer(floatArrayOf(1f));session.offer(floatArrayOf(2f));session.finish{result=it};q.drain()
        assertEquals(1,errors.size);assertEquals("stream error",result!!.exceptionOrNull()!!.message)
        assertEquals(1,engine.events.count{it=="feed"});assertTrue(engine.events.contains("abort"))
    }
    @Test fun queueHasTheSameHardDurationLimitAsCapture() {
        val q=SerialQueue();val engine=FakeEngine();val errors=ArrayList<Throwable>();var result:Result<String>?=null
        val session=LiveSpeechSession(q,engine,{}, {errors.add(it)},maxSamples=2)
        session.offer(floatArrayOf(1f,2f));session.offer(floatArrayOf(3f));session.finish{result=it};q.drain()
        assertEquals(1,errors.size);assertTrue(result!!.isFailure);assertTrue(engine.samples.isEmpty())
    }
    @Test fun hypothesesReplaceWordsRatherThanAppendingDuplicates() {
        val transcript=LiveTranscript()
        transcript.update(1,0f,"buy some");transcript.update(1,0f,"buy some milk")
        transcript.update(2,2f,"and bread");transcript.update(1,0f,"Buy milk")
        assertEquals("Buy milk and bread",transcript.text)
        transcript.update(2,2f,"");assertEquals("Buy milk",transcript.text)
        assertEquals("",LiveTranscript().text)
    }
}
