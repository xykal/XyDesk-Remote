# Local migration integration — not a production deployment

Two local workers demonstrate the intended routing contract:

1. `cd web-next && npm ci && npm run cf:build && npx wrangler dev --local --ip 0.0.0.0 --port 3000`
2. From the repo root, `web-next/node_modules/.bin/wrangler dev --config integration/wrangler.jsonc --local --ip 0.0.0.0 --port 3001`
3. Use port 3001. `node --test integration/router.test.mjs` checks the route isolation contract.

The router forwards a narrowly enumerated UI path list to OpenNext via a local service binding, while API paths, APK aliases, redirects, and other endpoints still go to the legacy Worker. UI HTML gets equivalent security headers, except iframe restrictions are relaxed on `*.e2b.app` solely for the Arena preview. Production use requires removing that preview exception and adding production HSTS and the tested production binding to a reviewed deployment configuration.

No production secrets, KV, D1 or production routes are configured here. GitHub's public API may be queried by the existing Worker for stats/download redirects. This setup is not a deployment recipe: before cutover, configure a separate staged Worker and test service bindings, cache keys, account-specific resources, rollback, compatibility with all domains, translations, PWA, SEO and the original site's functions. Legacy pages remain HTML; this is an incremental migration, not full React conversion.

The local assets configuration uses `html_handling: none` to preserve `/host.html` as HTML while `/host` remains the legacy PowerShell endpoint. Production integration must explicitly preserve this route contract rather than copying this staging configuration blindly.
