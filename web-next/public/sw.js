/* XyDesk Remote — service worker (PWA + offline ringan). */
var CACHE = "xydesk-web-v2.0.2";
var CORE = [
  "/", "/index.html", "/rdp.html", "/abi.html", "/about.html",
  "/status.html", "/host.html", "/privacy.html", "/terms.html",
  "/licenses.html", "/manifest.json", "/favicon.svg", "/icon-192.png",
  "/icon-512.png", "/apple-touch-icon.png", "/lang.js"
];

self.addEventListener("install", function (e) {
  e.waitUntil(caches.open(CACHE).then(function (c) { return c.addAll(CORE); }).then(function () { return self.skipWaiting(); }));
});

self.addEventListener("activate", function (e) {
  e.waitUntil(
    caches.keys().then(function (keys) {
      return Promise.all(keys.filter(function (k) { return k !== CACHE; }).map(function (k) { return caches.delete(k); }));
    }).then(function () { return self.clients.claim(); })
  );
});

self.addEventListener("fetch", function (e) {
  var url = new URL(e.request.url);
  if (e.request.method !== "GET") return;
  // API & signaling selalu jaringan dulu.
  if (url.pathname.indexOf("/api/") === 0 || url.pathname.indexOf("/ws/") === 0) return;
  // Halaman: jaringan dulu, fallback cache (offline).
  if (e.request.mode === "navigate") {
    e.respondWith(
      fetch(e.request).then(function (res) {
        var copy = res.clone();
        caches.open(CACHE).then(function (c) { c.put(e.request, copy); });
        return res;
      }).catch(function () {
        return caches.match(e.request).then(function (m) { return m || caches.match("/index.html"); });
      })
    );
    return;
  }
  // Aset statis: cache dulu, lalu jaringan.
  e.respondWith(
    caches.match(e.request).then(function (hit) {
      if (hit) return hit;
      return fetch(e.request).then(function (res) {
        if (res.ok && url.origin === location.origin) {
          var copy = res.clone();
          caches.open(CACHE).then(function (c) { c.put(e.request, copy); });
        }
        return res;
      });
    })
  );
});
