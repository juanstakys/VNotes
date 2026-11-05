package app.vnotes
import org.junit.Test
import org.junit.Assert.*

class EnergyVadTest {
    @Test fun leadingSilenceAndClicksNeverArmAutoStop(){
        val vad=EnergyVad(16000,.7,.01)
        repeat(200){assertFalse(vad.push(0.0,512))}
        assertFalse(vad.push(.5,512));repeat(100){assertFalse(vad.push(0.0,512))}
    }
    @Test fun speechArmsAndSilenceStopsAtActualSampleRate(){
        for(rate in listOf(16000,44100,48000)){
            val vad=EnergyVad(rate,.7,.01)
            repeat(30){assertFalse(vad.push(.02,rate/100))}
            assertTrue(vad.heard);repeat(69){assertFalse(vad.push(0.0,rate/100))};assertTrue(vad.push(0.0,rate/100))
        }
    }
    @Test fun speechRestartsTheCountdown(){val vad=EnergyVad(16000,2.0,.01);vad.push(.02,4800);assertFalse(vad.push(0.0,16000));vad.push(.02,512);assertEquals(0.0,vad.quietMs,0.0);assertTrue(vad.push(0.0,32000))}
    @Test fun sliderHasFullLogarithmicRange(){assertEquals(.1,EnergyVad.threshold(0),1e-10);assertEquals(.00001,EnergyVad.threshold(1000),1e-10);assertEquals(250,EnergyVad.position(.01));assertTrue(EnergyVad(16000,2.0,.00001).apply{push(.00002,4800)}.heard)}
    @Test fun longNotesSplitWithoutSampleLoss(){val audio=FloatArray(16000*65){it.toFloat()};val ranges=splitAudio(audio);assertTrue(ranges.size>1);assertEquals(audio.size,ranges.sumOf{it.count()});assertEquals(0,ranges.first().first);assertEquals(audio.lastIndex,ranges.last().last);ranges.zipWithNext().forEach{(a,b)->assertEquals(a.last+1,b.first)};assertTrue(ranges.all{it.count()<=320000})}
}
