import legacy from '../cloudflare/worker.mjs';

// Migration router: API/download aliases and unknown paths remain owned by the
// existing Worker. Only explicitly enumerated UI routes go to Next. This file
// and its wrangler config are for isolated local/staging use, not production.
const pages = new Set(['/','/index.html','/about','/about.html','/abi','/abi.html','/host','/host.html','/rdp','/rdp.html','/status','/status.html','/privacy','/privacy.html','/terms','/terms.html','/licenses','/licenses.html']);
export function isNextPath(path) {
  return pages.has(path) || path.startsWith('/_next/') || path.startsWith('/screenshots/') || path === '/legacy-nav.css' || path === '/legacy-nav.js';
}
export function isWebHost(host) {
  return host === 'localhost' || host === '127.0.0.1' || host.endsWith('.e2b.app') || host === 'www.xydeskremote.biz.id';
}
export function securityHeaders(response, preview = false) {
  const out = new Response(response.body, response);
  out.headers.set('X-Content-Type-Options','nosniff');
  if (!preview) out.headers.set('X-Frame-Options','SAMEORIGIN');
  out.headers.set('Referrer-Policy','strict-origin-when-cross-origin');
  out.headers.set('Permissions-Policy','camera=(), microphone=(), geolocation=()');
  if ((out.headers.get('content-type') || '').includes('text/html')) {
    const base = "default-src 'self'; script-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net; style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; font-src 'self' data: https://fonts.gstatic.com; img-src 'self' data: blob: https:; connect-src 'self' https://*.xydeskremote.biz.id wss://*.xydeskremote.biz.id https://api.github.com; base-uri 'self'; object-src 'none'";
    out.headers.set('Content-Security-Policy', base + (preview ? '' : "; frame-ancestors 'self'"));
  }
  return out;
}
export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);
    if (isWebHost(url.hostname) && isNextPath(url.pathname) && env.NEXT_SITE) {
      // index.html is an old alias; Next's canonical home is '/'.
      const req = url.pathname === '/index.html' ? new Request(new URL('/' + url.search, url), request) : request;
      return securityHeaders(await env.NEXT_SITE.fetch(req), url.hostname.endsWith('.e2b.app'));
    }
    return legacy.fetch(request, env, ctx);
  }
};
