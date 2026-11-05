import {splitAudio, describeError} from './speech-utils.js';
let enginePromise;
const post = message => self.postMessage(message);
async function load(id) {
  if (!enginePromise) enginePromise = (async () => {
    post({id, type: 'status', message: 'Loading the local speech runtime…'});
    const {pipeline, env} = await import('./vendor/transformers.min.js');
    env.allowLocalModels = false;
    env.useBrowserCache = false; // The service worker caches model responses.
    env.backends.onnx.wasm.numThreads = 1;
    env.backends.onnx.wasm.proxy = false;
    env.backends.onnx.wasm.wasmPaths = new URL('./vendor/', import.meta.url).href;
    const engine = await pipeline('automatic-speech-recognition', 'onnx-community/moonshine-tiny-ONNX', {
      revision: 'a6da124', device: 'wasm', dtype: {encoder_model: 'fp32', decoder_model_merged: 'q8'},
      progress_callback: progress => post({id, type: 'progress', progress}),
    });
    post({id, type: 'status', message: 'Preparing English voice recognition…'});
    await engine(new Float32Array(16000), {max_new_tokens: 16});
    return engine;
  })().catch(error => {enginePromise = null; throw error;});
  return enginePromise;
}
// Every request is serialized, including initialization and inference.
let queue = Promise.resolve();
self.onmessage = ({data}) => {
  queue = queue.then(async () => {
    const {id, type, audio} = data;
    try {
      const engine = await load(id);
      if (type === 'load') return post({id, type: 'ready'});
      if (!(audio instanceof Float32Array) || !audio.length || audio.length > 16000 * 121) throw Error('Invalid recording: expected up to 2 minutes of mono 16 kHz PCM.');
      const chunks = splitAudio(audio); const texts = [];
      for (let i = 0; i < chunks.length; i++) {
        post({id, type: 'status', message: `Transcribing part ${i+1} of ${chunks.length} on this device…`});
        const {text} = await engine(chunks[i], {max_new_tokens: Math.max(16, Math.min(256, Math.ceil(chunks[i].length / 16000 * 8)))});
        if (text.trim()) texts.push(text.trim());
      }
      post({id, type: 'result', text: texts.join(' ')});
    } catch (error) {post({id: data.id, type: 'error', message: describeError(error), stack: error?.stack?.slice(0, 4000)});}
  });
};
