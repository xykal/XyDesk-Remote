/* Staged PWA shell. API, RDP, auth and downloads always bypass this cache. */
const CACHE = 'xydesk-next-shell-v1';
const CORE = ['/', '/favicon.svg', '/icon-192.png', '/icon-512.png'];
self.addEventListener('install', event => {
  event.waitUntil(caches.open(CACHE).then(cache => cache.addAll(CORE)));
});
self.addEventListener('activate', event => {
  event.waitUntil(caches.keys().then(keys => Promise.all(keys.filter(k => k.startsWith('xydesk-next-shell-') && k !== CACHE).map(k => caches.delete(k)))).then(() => self.clients.claim()));
});
self.addEventListener('fetch', event => {
  const req = event.request;
  const url = new URL(req.url);
  if (req.method !== 'GET' || url.origin !== self.location.origin) return;
  if (req.mode === 'navigate') {
    // Offline fallback is deliberately limited to the landing page.
    if (url.pathname !== '/' && url.pathname !== '/index.html') return;
    event.respondWith(fetch(req).then(async res => {
      if (res.ok) await caches.open(CACHE).then(c => c.put('/', res.clone()));
      return res;
    }).catch(() => caches.open(CACHE).then(c => c.match('/')).then(res => res || Response.error())));
    return;
  }
  // Only static, same-origin shell assets are eligible. Never cache API calls,
  // authentication, WebSockets, APK aliases, host scripts or RDP session data.
  if (!url.pathname.startsWith('/_next/static/') && !url.pathname.startsWith('/screenshots/') && !CORE.includes(url.pathname)) return;
  event.respondWith(caches.open(CACHE).then(async cache => {
    const hit = await cache.match(req);
    if (hit) return hit;
    const res = await fetch(req);
    if (res.ok && res.type === 'basic') await cache.put(req, res.clone());
    return res;
  }));
});
