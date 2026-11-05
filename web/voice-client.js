export class VoiceClient {
  constructor(onStatus) {this.onStatus = onStatus; this.nextId = 0; this.worker = null; this.current = null;}
  reset(error = Error('Speech engine stopped. The recording is kept for retry.')) {
    this.worker?.terminate(); this.worker = null;
    if (this.current) {clearTimeout(this.current.timeout); this.current.reject(error); this.current = null;}
  }
  request(type, audio) {
    if (this.current) return Promise.reject(Error('The speech engine is busy. Wait for the current operation.'));
    return new Promise((resolve, reject) => {
      const id = ++this.nextId;
      this.current = {id, resolve, reject, timeout: setTimeout(() => this.reset(Error('Speech processing exceeded 5 minutes. Close other apps and retry the saved recording.')), 300000)};
      try {
        if (!this.worker) {
          this.worker = new Worker(new URL('./stt-worker.js', import.meta.url), {type: 'module'});
          this.worker.onmessage = ({data}) => {
            if (data.id !== this.current?.id) return;
            if (['status', 'progress'].includes(data.type)) {this.onStatus(data); return;}
            const current = this.current; clearTimeout(current.timeout); this.current = null;
            if (data.type === 'error') {const error = Error(data.message); error.workerStack = data.stack; current.reject(error); this.worker.terminate(); this.worker = null;}
            else current.resolve(data);
          };
          this.worker.onerror = event => this.reset(Error(`Speech worker failed: ${event.message || 'runtime could not start'}`));
          this.worker.onmessageerror = () => this.reset(Error('Could not read the speech worker response.'));
        }
        // Transfer a COPY: keep the retry buffer intact in memory and IndexedDB.
        const copy = audio?.slice(); this.worker.postMessage({id, type, audio: copy}, copy ? [copy.buffer] : []);
      } catch (error) {this.reset(error);}
    });
  }
}
