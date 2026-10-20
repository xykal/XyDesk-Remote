# GitHub login: canonical API callback, not the legacy RdpFree client

Owner report: Android login goes to rdpfree.projectkal instead of api.xydeskremote.
Confirmed by source AND a non-authenticated live redirect inspection on 2026-10-20:
Android already started at `/api/github/oauth/login` on the correct API host, but
the Worker deliberately chose the legacy RdpFree OAuth client/callback unless
`client=xydesk` was supplied. The exchange handler also tried both clients and
contained source-embedded fallback secrets. These were not required to keep old
saved sessions or RdpFree device provisioning functional.

## Changes

- Default login and `/api/auth/login` alias use ONLY the deployment-configured
  GITHUB_OAUTH_CLIENT_ID + GITHUB_OAUTH_CLIENT_SECRET. Both must be configured;
  missing configuration is explicit 503/oauth_unconfigured, not a legacy fallback.
- GitHub authorization remains on github.com. The fixed redirect_uri is exactly
  `https://api.xydeskremote.biz.id/api/github/oauth/callback`, independent of query
  client/redirect selectors or the incoming hostname.
- Callback/exchange aliases send that SAME redirect_uri and ONE configured client
  to GitHub token exchange; rejection never falls back to the legacy OAuth app.
  No source-embedded fallback secrets remain in this Worker flow. Existing provider
  identity verification before persisting/returning a session remains in place.
- Android explicitly selects client=xydesk to work even during old edge rollout;
  one pure endpoint policy validates session ID before building its URL. New login
  no longer polls a legacy dashboard or accepts its cookie as a fresh login.
- Embedded browser starts at the API login URL in web-redirect mode, rather than
  always starting on GitHub's device-code page. The explicit device-flow mode still
  starts on github.com/login/device, with its original public client ID.
- No saved credential/session/data clear, no OAuth grant/permission acceptance,
  no account logout, no RDP provisioning API/domain migration. RdpFree's existing
  device/setup/connection endpoints and already-stored dashboard sessions remain.
  Explicit web-dashboard return handling is separate from default Android login.

## Deployment / evidence

Worker-only production `c532dfc8-8abd-4c9b-b7e4-d90157da999c`, Worker source aa5afe6;
final Android source 7abb70d. Original main5990 assets/config/bindings/migrations
retained, zero changed assets. No new namespace, reset, release/tag or paid upgrade.
Both OAuth configuration bindings already exist as Cloudflare secret bindings;
only their NAMES/TYPES were inspected. Public configured client ID was confirmed
through the existing custom-client login redirect, without reading secret values.

Live default, auth alias, client=xydesk AND client=legacy each returned 302 to
GitHub with the canonical API callback and intended public client ID. No code,
access token or real GitHub authorization was used in the probe. Health/channels
200; anonymous outbox capability401; history200/99. See GITHUB-LOGIN-VALIDATION.json.

154 Worker tests pass (six new OAuth route/config/source regressions); two new
Android endpoint policy tests. Five baseline guards retain the existing explicit
outbox-only JobService manifest exception; this fix changes no protected connection,
render/native/build/manifest inputs. Exact final Android/JVM/compile CI is recorded
separately in GITHUB-LOGIN-VALIDATION.json, not assumed from Worker tests.
Final Android Kotlin/core/app JVM gate SUCCESS 39378247936 at 7abb70d;
Web Worker gate SUCCESS 39378248447.

## Acceptance and security limits

An actual owner sign-in -> authorize -> API callback -> Android verified account
round trip, OAuth app settings/callback registration and device/browser behavior
have NOT been verified by these redirect checks. Backend URL correctness is not a
claim that a missing/incorrect GitHub app registration or invalid client secret is
fixed. Existing permission scopes and final app-return mechanism are unchanged;
this is not a completed OAuth/session-security audit or signed APK acceptance.

Historical source-embedded OAuth secrets remain in Git history and should be
reviewed/rotated by the OAuth app owner. Removing fallback literals is NOT a secret
rotation. No new secret was invented, copied from source into environment, printed
from Cloudflare secret storage, or silently rotated in this step.

For the existing app, close the OLD browser login tab and start login again from
the app so it makes a fresh API login request (no-store). Do not uninstall or clear
storage. If GitHub itself rejects redirect_uri, the app owner must verify that
GitHub OAuth app's registered callback exactly matches the canonical API URL.
