# XyDesk Remote website: source and deployment

## Source of truth

The public site is served at `https://rdp.xydesk.my.id/`. Its HTML and public assets live in `web/`; the Cloudflare Worker adapter lives in `cloudflare/worker.mjs`, with deployment settings in `wrangler.jsonc`.

The current production Worker was previously maintained as an embedded HTML script outside this repository. A Git push alone does **not** deploy the website. These changes have not been deployed. Do not run the production deploy command below until the project owner explicitly approves the live deployment and the Cloudflare target is confirmed.

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

The Worker retains the current production `GH_TOKEN` behavior for both XyDesk Remote and legacy XyDesk asset aliases: it requests the stored release-asset ID, then falls back to the same filename in recent releases if the ID is stale. `/SHA256SUMS.txt` is a new pinned public-release redirect. Before/after deployment, confirm the `GH_TOKEN` binding remains available or those asset aliases will return 404. Set it through Wrangler's secret prompt if required; never commit its value.

`GET /api/stats` sums the `download_count` values of `.apk` assets across currently available, non-draft GitHub Releases. It caches the aggregate at the Worker edge for up to 15 minutes; `GH_TOKEN` is optional for the public GitHub API request. The displayed number is asset downloads, not unique people, and deleted release assets cannot be counted. This endpoint does not measure active RDP sessions. The Android app connects directly to user-selected hosts, so a real connected-session count would require new app-side heartbeat telemetry and a corresponding privacy disclosure; do not display a fabricated zero or infer sessions from website visits.

## Production acceptance checks

```sh
curl -i https://rdp.xydesk.my.id/robots.txt
curl -i https://rdp.xydesk.my.id/sitemap.xml
curl -i https://rdp.xydesk.my.id/googleb2a7847179613098.html
curl -i https://rdp.xydesk.my.id/release-state.json
curl -i https://rdp.xydesk.my.id/api/health
curl -i https://rdp.xydesk.my.id/api/status
curl -i https://rdp.xydesk.my.id/api/stats
curl -I https://rdp.xydesk.my.id/arm64-v8a.apk
curl -i https://rdp.xydesk.my.id/definitely-not-a-real-page
```

Expected: robots and sitemap are their own files; the Google verification path returns the exact verification text (not the homepage); release-state and all three API routes return JSON; `/api/status` keeps the Android client's legacy fields but reports `published`, not an expired countdown; `/api/stats` reports APK asset download counts from available GitHub Releases (not unique users or active RDP sessions); each APK alias redirects to the matching official GitHub asset; an unknown route returns HTTP 404 and the custom 404 page.
