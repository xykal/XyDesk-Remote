# XyDesk Next.js — migration preview

This is a **staged migration**, not a replacement for the production Worker. The homepage is implemented in Next.js; the other pages in `public/` are legacy HTML with a shared responsive mobile nav. Existing RDP, API, download aliases, service-worker behaviour, localisation, release stats, and SEO must be parity-tested before cutover. The historical screenshots are real existing assets and may contain outdated desktop content.

## Development

Node.js 22+, `npm ci`, `npm run dev`. Preview is a standalone app: links to APK downloads use the production download aliases; dynamic API endpoints and RDP gateway depend on the existing production Worker and gateway and are not emulated here. No release APK, key or gateway configuration is changed.

## Cloudflare build

`npm run cf:build` builds Next.js through OpenNext, with the separate staging Worker configuration in `wrangler.jsonc`. This config intentionally does **not** replace the existing production `wrangler.jsonc` or route its domains. Production integration needs a tested routing/service-binding strategy preserving the existing Worker, domain separation, API routes, static asset aliases and security headers. Do not run `wrangler deploy` against production until parity, routing, security and rollback have been reviewed.

## Preview limitations

The legacy pages remain plain HTML (not yet Next/React pages). In the isolated preview `/api/*` is not connected to the production Worker; release stats and other dynamic calls will return 404. The two main APK links intentionally point to the currently public production download aliases, **not** the draft release. The old screenshots are historical and can display legacy games. The next step is integration testing against an isolated Worker with a dedicated service binding and ensuring language/SEO/PWA/feature parity, then a staged cutover; this preview is not production-ready.

The repository-level `integration/` directory now contains a local two-Worker router, which keeps legacy API/download routes working while routing the selected UI paths to OpenNext. See `integration/README.md`. It is still not production configuration.

## Language and offline status

The Next homepage offers Indonesian and English plus 18 API-translated languages. Translation requests contain only fixed interface strings; unavailable translations fall back to English with a notice. Browser storage is used for selection and completed translation batches. The preview-only service worker caches the homepage shell and static assets; API, RDP, authentication and APK downloads stay network-only. It is deliberately not registered on production domains until rollback and PWA parity are approved. Existing pages remain legacy HTML.
