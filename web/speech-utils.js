/** Split long notes explicitly: Transformers 3.7 Moonshine does NOT implement Whisper's chunk_length_s. */
export function splitAudio(audio, sampleRate = 16000) {
  const segments = [];
  const maximum = 20 * sampleRate, searchFrom = 12 * sampleRate, window = Math.round(.25 * sampleRate);
  let offset = 0;
  while (audio.length - offset > maximum) {
    let best = offset + maximum, lowest = Infinity;
    for (let start = offset + searchFrom; start + window <= offset + maximum; start += window) {
      let energy = 0; for (let i = start; i < start + window; i++) energy += audio[i] * audio[i];
      if (energy <= lowest) {lowest = energy; best = start + Math.floor(window / 2);}
    }
    segments.push(audio.subarray(offset, best)); offset = best;
  }
  if (offset < audio.length) segments.push(audio.subarray(offset));
  return segments;
}
export function describeError(error) {
  if (typeof error === 'string') return error;
  if (typeof error === 'number') return `WASM runtime error ${error}. The phone may have run out of memory; close other tabs and retry.`;
  if (error?.message) return `${error.name || 'Error'}: ${error.message}`;
  try {return JSON.stringify(error) || 'Unknown speech engine error';} catch {return String(error);}
}
