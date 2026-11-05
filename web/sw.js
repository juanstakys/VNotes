const SHELL = 'vnotes-shell-v6';
const VOICE = 'vnotes-voice-v2';
const FILES = ['./', 'index.html', 'style.css', 'app.js', 'db.js', 'audio.js', 'vad.js', 'sensitivity.js', 'offline.js', 'voice-client.js', 'speech-utils.js', 'stt-worker.js', 'capture-worklet.js', 'manifest.webmanifest', 'icon.svg', 'icon-192.png', 'icon-512.png'];
self.addEventListener('install', event => event.waitUntil((async () => {
  const cache = await caches.open(SHELL);
  // Bypass the HTTP cache when upgrading older installations.
  await Promise.all(FILES.map(file => cache.add(new Request(file, {cache: 'reload'}))));
  await self.skipWaiting();
})()));
self.addEventListener('activate', event => event.waitUntil(self.clients.claim()));
self.addEventListener('fetch', event => {
  const url = new URL(event.request.url);
  if (event.request.method !== 'GET') return;
  const voice = url.hostname === 'huggingface.co' || (url.origin === location.origin && url.pathname.includes('/vendor/'));
  if (url.origin !== location.origin && !voice) return;
  event.respondWith((async () => {
    const cache = await caches.open(voice ? VOICE : SHELL);
    const hit = await cache.match(event.request);
    if (voice && hit) return hit;
    try {
      const response = await fetch(event.request);
      if (response.ok) {
        // A cache failure must be visible during voice setup: offline readiness would otherwise be false.
        if (voice) await cache.put(event.request, response.clone());
        else await cache.put(event.request, response.clone()).catch(() => {});
      }
      return response;
    } catch (error) {
      if (hit) return hit;
      if (event.request.mode === 'navigate') return (await cache.match('./')) || Response.error();
      throw error;
    }
  })());
});
