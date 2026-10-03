# XyDesk Remote website: source and deployment

## Source of truth

The public site is served at `https://rdp.xydesk.my.id/`. Its HTML and public assets live in `web/`; the Cloudflare Worker adapter lives in `cloudflare/worker.mjs`, with deployment settings in `wrangler.jsonc`.

The production Worker was previously maintained as an embedded HTML script outside this repository; a Git push alone does **not** deploy the website. The project owner explicitly approved this v1.0.1 full-ship deployment on 2026-10-03; that approval is limited to this release. Initial deployment version `1374c19e-2ea5-4699-935e-bac0f7a9f26c` is live on `rdp.xydesk.my.id`. Smoke tests confirmed the SEO/static pages, Search Console file, release metadata, `/api/health`, `/api/status`, and `/api/active-sessions`; they also found `/api/stats` unavailable and APK aliases returning 502. A source correction is prepared locally and must be pushed before the corrective Worker deployment. Future production changes still require explicit approval.

## Local validation

```sh
node --test cloudflare/worker.test.mjs
npx --yes wrangler@4 deploy --dry-run --config wrangler.jsonc
```

## Production deploy

A deployment replaces the code serving `rdp.xydesk.my.id`. Use a Cloudflare API token with the minimum Workers Scripts permissions; do not put the token in Git, a URL, or this file.

```sh
npx --yes wrangler@4 deploy --config wrangler.jsonc
```

Current XyDesk Remote asset aliases redirect to the validated matching URL/tag in `release-state.json`, so APK and Host Agent downloads do not depend on a GitHub API token. `/SHA256SUMS.txt` is pinned to the current public release tag. Aggregate download statistics and legacy `xykal/XyDesk` aliases try `GH_TOKEN`, then `GH_PAT` (configure it with read-only access), then the public GitHub API where available. Keep a valid least-privilege read token for stable statistics/legacy aliases; set secrets through Wrangler and never commit or print their values.

`GET /api/stats` sums the `download_count` values of `.apk` assets across currently available, non-draft GitHub Releases. It caches the aggregate at the Worker edge for up to 15 minutes; `GH_TOKEN` is optional for the public GitHub API request. The displayed number is asset downloads, not unique people, and deleted release assets cannot be counted. The release workflows retain published releases/tags so available APK releases continue contributing to the aggregate; only temporary Actions artifacts and old workflow runs are pruned.

`GET /api/active-sessions` reads the Durable Object lease count without caching. `POST /api/session` accepts a short-lived per-session random UUID from the Android app only when the user has explicitly enabled the opt-in setting. The Durable Object stores a SHA-256 key plus heartbeat time, treats leases as expired after 90 seconds, and does not retain session history or the app's host/profile/credential data. The number is an approximate count of opted-in client-reported sessions, not unique people or every RDP connection; public clients can spoof reports. The new `ActiveSessions` SQLite Durable Object and `v1` migration are configured in Wrangler and must be included in the approved production deployment. The signed v1.0.1 APK (app 0.5.35, version code 52) has now been published and includes the opt-in reporter; it is off by default. Older v1.0.0 APKs do not send heartbeats. The public count stays at zero until someone updates, explicitly opts in, and connects an RDP session; never interpret it as unique users or all sessions. The UI shows unavailable rather than a fabricated zero if the Durable Object is missing or fails.

## Production acceptance checks

```sh
curl -i https://rdp.xydesk.my.id/robots.txt
curl -i https://rdp.xydesk.my.id/sitemap.xml
curl -i https://rdp.xydesk.my.id/googleb2a7847179613098.html
curl -i https://rdp.xydesk.my.id/release-state.json
curl -i https://rdp.xydesk.my.id/api/health
curl -i https://rdp.xydesk.my.id/api/status
curl -i https://rdp.xydesk.my.id/api/stats
curl -i https://rdp.xydesk.my.id/api/active-sessions
curl -I https://rdp.xydesk.my.id/arm64-v8a.apk
curl -i https://rdp.xydesk.my.id/definitely-not-a-real-page
```

Expected: robots and sitemap are their own files; the Google verification path returns the exact verification text (not the homepage); release-state and all four GET API routes return JSON; `/api/status` keeps the Android client's legacy fields but reports `published`, not an expired countdown; `/api/stats` reports APK asset download counts from available GitHub Releases (not unique users); `/api/active-sessions` returns an uncached opt-in session count after the Durable Object migration is applied; `/api/session` accepts only bounded JSON heartbeat/end messages with random UUIDs; each APK alias redirects to the matching official GitHub asset; an unknown route returns HTTP 404 and the custom 404 page.
