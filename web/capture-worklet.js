// Batch 512 mono frames and calculate RMS here, away from the UI thread.
class Capture extends AudioWorkletProcessor {
  constructor() {
    super(); this.buffer = new Float32Array(512); this.used = 0; this.stopped = false;
    this.port.onmessage = ({data}) => {
      if (data === 'flush') {
        this.stopped = true;
        if (this.used) this.send();
        this.port.postMessage({type: 'flushed'});
      }
    };
  }
  send() {
    const buffer = this.buffer.slice(0, this.used);
    let energy = 0; for (const value of buffer) energy += value * value;
    this.port.postMessage({type: 'chunk', buffer, level: Math.sqrt(energy / buffer.length)}, [buffer.buffer]);
    this.used = 0;
  }
  process(inputs) {
    if (this.stopped) return false;
    const channels = inputs[0];
    if (channels?.length) {
      for (let i = 0; i < channels[0].length; i++) {
        let value = 0; for (const channel of channels) value += channel[i] / channels.length;
        this.buffer[this.used++] = value;
        if (this.used === this.buffer.length) this.send();
      }
    }
    return true;
  }
}
registerProcessor('capture', Capture);
