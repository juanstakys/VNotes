export const VOICE_CACHE = 'vnotes-voice-v2';
let registrationPromise;
export async function ensureOffline() {
  if (!globalThis.isSecureContext) throw Error('Open VNotes over HTTPS or http://localhost. For USB, run adb reverse tcp:5173 tcp:5173.');
  if (!navigator.serviceWorker) throw Error('This browser cannot install the offline app. Use Chrome for Android.');
  if (!registrationPromise) registrationPromise = (async () => {
    const registration = await navigator.serviceWorker.register(new URL('./sw.js', import.meta.url), {updateViaCache: 'none'});
    await Promise.race([navigator.serviceWorker.ready, new Promise((_, reject) => setTimeout(() => reject(Error('Offline installation timed out. Reload while connected.')), 20000))]);
    if (!navigator.serviceWorker.controller) await new Promise((resolve, reject) => {
      const timeout = setTimeout(() => {navigator.serviceWorker.removeEventListener('controllerchange', changed); reject(Error('Offline installation needs one reload.'));}, 10000);
      function changed() {clearTimeout(timeout); resolve();}
      navigator.serviceWorker.addEventListener('controllerchange', changed, {once: true});
      if (navigator.serviceWorker.controller) changed();
    });
    return registration;
  })().catch(error => {registrationPromise = null; throw error;});
  return registrationPromise;
}
export async function voiceManifest() {
  const cache = await caches.open(VOICE_CACHE); const requests = await cache.keys();
  const urls = requests.map(r => r.url);
  const required = ['transformers.min.js', '.wasm', 'encoder_model', 'decoder_model', 'tokenizer'];
  if (!required.every(part => urls.some(url => url.includes(part)))) throw Error('Voice files did not finish caching. Keep the app open and run setup again.');
  return urls;
}
export async function hasVoice(manifest) {
  if (!Array.isArray(manifest) || !manifest.length) return false;
  try {const cache = await caches.open(VOICE_CACHE); return (await Promise.all(manifest.map(url => cache.match(url)))).every(Boolean);} catch {return false;}
}
