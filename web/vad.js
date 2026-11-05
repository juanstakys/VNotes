/** Energy-only VAD. Durations come from audio samples, never timer scheduling. */
export class EnergyVAD {
  constructor({sampleRate, silenceSeconds = 2, threshold = 0.01, minSpeechMs = 300}) {
    this.sampleRate = sampleRate;
    this.silenceMs = silenceSeconds * 1000;
    this.threshold = threshold;
    this.minSpeechMs = minSpeechMs;
    this.speechMs = 0;
    this.quietMs = 0;
    this.totalMs = 0;
    this.peak = 0;
    this.level = 0;
  }
  push(level, samples) {
    const ms = samples * 1000 / this.sampleRate;
    this.level = level;
    this.peak = Math.max(this.peak, level);
    this.totalMs += ms;
    if (level >= this.threshold) {this.speechMs += ms; this.quietMs = 0;}
    else this.quietMs += ms;
    return this.heard && this.quietMs >= this.silenceMs;
  }
  get heard() {return this.speechMs >= this.minSpeechMs;}
  get remainingSeconds() {return Math.max(0, (this.silenceMs - this.quietMs) / 1000);}
}
