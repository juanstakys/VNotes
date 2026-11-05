package app.vnotes

import kotlin.math.*

class EnergyVad(private val sampleRate: Int, private val silenceSeconds: Double, private val threshold: Double) {
    var speechMs = 0.0; private set
    var quietMs = 0.0; private set
    var peak = 0.0; private set
    var totalMs = 0.0; private set
    val heard get() = speechMs >= 300.0
    fun push(rms: Double, count: Int): Boolean {
        val ms = count * 1000.0 / sampleRate
        totalMs += ms; peak = max(peak, rms)
        if (rms >= threshold) { speechMs += ms; quietMs = 0.0 } else quietMs += ms
        return heard && quietMs >= silenceSeconds * 1000.0
    }
    companion object {
        fun threshold(position: Int) = (.1 * 10.0.pow(-position / 250.0)).coerceIn(.00001, .1)
        fun position(threshold: Double) = (-250 * log10(threshold / .1)).roundToInt().coerceIn(0,1000)
        fun level(rms: Double) = ((20 * log10(max(.00001, rms)) + 100) / 100).coerceIn(0.0,1.0).toFloat()
    }
}

/** Prefer the quietest 250ms window between 12s and 20s; no samples overlap or disappear. */
fun splitAudio(audio:FloatArray, rate:Int=16000):List<IntRange>{
    val ranges=ArrayList<IntRange>();val maximum=20*rate;val window=rate/4;var offset=0
    while(audio.size-offset>maximum){var lowest=Double.POSITIVE_INFINITY;var best=offset+maximum
        for(start in offset+12*rate..offset+maximum-window step window){var energy=0.0;for(i in start until start+window)energy+=audio[i]*audio[i]
            if(energy<=lowest){lowest=energy;best=start+window/2}}
        ranges.add(offset until best);offset=best
    };if(offset<audio.size)ranges.add(offset until audio.size);return ranges
}
