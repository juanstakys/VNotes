import {EnergyVAD} from './vad.js';
export async function record({silenceSeconds, threshold = 0.01, onTick, onStopping = () => {}, onStop, onError = () => {}, getStream = constraints => navigator.mediaDevices.getUserMedia(constraints)}) {
  if (!globalThis.isSecureContext) throw Error('Microphone needs HTTPS or http://localhost. Use adb reverse for USB testing.');
  if (!navigator.mediaDevices?.getUserMedia) throw Error('This browser does not provide microphone capture. Try current Chrome for Android.');
  // Create/resume inside the original tap, before the permission promise loses activation.
  let context;
  try {context = new AudioContext({sampleRate: 16000, latencyHint: 'interactive'});}
  catch {context = new AudioContext({latencyHint: 'interactive'});}
  const resumed = context.resume();
  let stream, source, capture, mute, timer, flushResolve;
  let stopping = null, cancelled = false, finished = false, chunks = [], length = 0;
  let lastBlock = performance.now();
  const cleanup = async () => {
    clearInterval(timer);
    if (capture) {capture.port.onmessage = null; capture.disconnect();}
    source?.disconnect(); mute?.disconnect();
    stream?.getTracks().forEach(t => {t.onended = null; t.stop();});
    if (context.state !== 'closed') await context.close();
  };
  try {
    await resumed;
    stream = await getStream({audio: {channelCount: 1, sampleRate: 16000, echoCancellation: true, noiseSuppression: true, autoGainControl: true}});
    if (context.state !== 'running') await context.resume();
    await context.audioWorklet.addModule(new URL('./capture-worklet.js', import.meta.url));
    const vad = new EnergyVAD({sampleRate: context.sampleRate, silenceSeconds, threshold});
    source = context.createMediaStreamSource(stream);
    capture = new AudioWorkletNode(context, 'capture', {channelCount: 1, channelCountMode: 'explicit'});
    mute = context.createGain(); mute.gain.value = 0;
    const started = performance.now();
    const stop = (cancel = false, reason = 'manual') => {
      cancelled ||= cancel;
      if (stopping) return stopping;
      onStopping(reason);
      stopping = (async () => {
        clearInterval(timer);
        // Flush partial worklet blocks before closing the graph. A timeout handles suspended contexts.
        await new Promise(resolve => {
          const timeout = setTimeout(resolve, 250);
          flushResolve = () => {clearTimeout(timeout); resolve();};
          capture.port.postMessage('flush');
        });
        await cleanup(); finished = true;
        if (cancelled) {chunks = []; return;}
        if (!length) throw Error('No audio samples arrived from the microphone. Check microphone permission and try again.');
        const pcm = new Float32Array(length); let offset = 0;
        for (const chunk of chunks) {pcm.set(chunk, offset); offset += chunk.length;} chunks = [];
        let audio = pcm;
        if (context.sampleRate !== 16000) {
          const offline = new OfflineAudioContext(1, Math.ceil(length * 16000 / context.sampleRate), 16000);
          const buffer = offline.createBuffer(1, length, context.sampleRate); buffer.copyToChannel(pcm, 0);
          const player = offline.createBufferSource(); player.buffer = buffer; player.connect(offline.destination); player.start();
          audio = (await offline.startRendering()).getChannelData(0);
        }
        // Manual stop permits quiet speech below the VAD threshold, but suppress true silence.
        const usable = vad.heard || (reason === 'manual' && vad.peak > 0.0005 && vad.totalMs >= 150);
        await onStop(audio, usable, {sampleRate: context.sampleRate, duration: length / context.sampleRate, peak: vad.peak, speechMs: vad.speechMs, reason});
      })().catch(async error => {await cleanup().catch(() => {}); finished = true; onError(error);});
      return stopping;
    };
    capture.port.onmessage = ({data}) => {
      if (data.type === 'flushed') {flushResolve?.(); return;}
      if (finished || data.type !== 'chunk') return;
      chunks.push(data.buffer); length += data.buffer.length; lastBlock = performance.now();
      const shouldStop = vad.push(data.level, data.buffer.length);
      if (!stopping && shouldStop) void stop(false, 'silence');
    };
    source.connect(capture).connect(mute).connect(context.destination);
    timer = setInterval(() => {
      onTick(Math.floor((performance.now() - started) / 1000), vad);
      if (performance.now() - lastBlock > 8000 && !stopping) {
        void stop(true, 'interrupted').then(() => onError(Error('Microphone stopped delivering audio. Keep VNotes in the foreground and try again.')));
      } else if (performance.now() - started >= 120000) void stop(false, 'limit');
    }, 100);
    stream.getTracks().forEach(t => {t.onended = () => void stop(false, 'interrupted');});
    return {stop};
  } catch (error) {await cleanup().catch(() => {}); throw error;}
}

/** Settings-only level monitor: no audio buffers retained and no transcription. */
export async function testMicrophone({onLevel, onError, getStream = constraints => navigator.mediaDevices.getUserMedia(constraints)}) {
  if (!globalThis.isSecureContext || !navigator.mediaDevices?.getUserMedia) throw Error('Microphone testing needs HTTPS or http://localhost and microphone permission.');
  let context;
  try {context = new AudioContext({sampleRate: 16000, latencyHint: 'interactive'});}
  catch {context = new AudioContext();}
  const resumed = context.resume();
  let stream, source, capture, mute, stopped = false;
  const stop = async () => {
    if(stopped)return;
    stopped = true;
    if (capture) {capture.port.onmessage = null; capture.disconnect();}
    source?.disconnect(); mute?.disconnect();
    stream?.getTracks().forEach(track => {track.onended = null; track.stop();});
    if (context.state !== 'closed') await context.close();
  };
  try {
    await resumed;
    stream = await getStream({audio: {channelCount: 1, sampleRate: 16000, echoCancellation: true, noiseSuppression: true, autoGainControl: true}});
    await context.audioWorklet.addModule(new URL('./capture-worklet.js', import.meta.url));
    if (context.state !== 'running') await context.resume();
    source = context.createMediaStreamSource(stream);
    capture = new AudioWorkletNode(context, 'capture', {channelCount: 1, channelCountMode: 'explicit'});
    mute = context.createGain(); mute.gain.value = 0;
    capture.port.onmessage = ({data}) => {if (!stopped && data.type === 'chunk') onLevel(data.level);};
    stream.getTracks().forEach(track => {track.onended = () => {void stop(); onError(Error('Microphone test interrupted. Tap Test microphone to restart.'));};});
    source.connect(capture).connect(mute).connect(context.destination);
    return {stop};
  } catch (error) {await stop().catch(() => {}); throw error;}
}
