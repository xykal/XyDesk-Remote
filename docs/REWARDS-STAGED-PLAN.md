# Rewards + quality: staged execution

User requested all tracks, incrementally. No APK assembly, deployment, version or host/wire contract changes authorized. Rules below are design proposals, not active grants or an approved API contract.

## Evidence and order
1. Existing podium badge/frame: gate39159270582 @dab6624 SUCCESS. This is Kotlin/JVM evidence only. Still needs device layout and live ranking tests.
2. Helpful contribution: design below; implementation blocked on authenticated identity and approved reward rules. Never trust `github_login` sent by the client as the grant principal.
3. Returning participation: design below; no permanent local-only claim or cosmetic unlock yet.
4. Full-app audit continues in parallel with safe local fixes, separate commits and evidence. Shared RDP control still needs explicit host consent, view-only defaults and coordination before protocols change.

## Sobat Penolong — candidate flow
- Recipient creates a help request; helper accepts; recipient alone can confirm resolved help. A helper cannot self-confirm or confirm on the recipient's behalf.
- Pending, confirmed, withdrawn and revoked are distinct states. A confirmation is not a grant; the server evaluates eligibility separately.
- Deduplicate confirmation by request, record authenticated actors, UTC event time and policy revision; retrying a confirmation must not add credit twice.
- Credit at most one pair contribution per policy window. Limit each actor's confirmations and require distinct recipients for an award. Exact caps, windows, trust-age rules and award thresholds must be agreed before enabling.
- Suspected reciprocal farming enters review, not automatic public accusations or punishment. Provide reason, report, appeal and reversible grant records.
- Reward: separate, optional Sobat Penolong badge and friendly profile accent. Never moderation powers or immunity. Preserve legitimate report/appeal access.
- Do not publish private conversations as proof. Store minimal event identifiers and confirmation, with a retention policy to be approved.

## Returning participation — candidate flow
- Track qualifying participation on distinct days within a week; cap daily credit. Do not use minutes online, message count or passive idle sessions to grant progress.
- A qualifying event must be authenticated and confirmed server-side, not a client clock/claimed duration. The event definition and daily/weekly thresholds remain to be approved; until then, grant nothing.
- Display progress and the week boundary clearly in local UI. Proposed week boundary: Monday00:00 UTC, consistently calculated by the server. Explain local equivalent; do not silently switch boundaries with device timezone.
- Missed days do not erase owned cosmetics. No daily-streak punishment, infinite progression, paid shortcuts or incentive to leave a session open overnight.
- Reward: cosmetic palette/frame collection with stable item IDs and server-authoritative ownership. A candidate cosmetic is not presented as owned before a confirmed grant. Duration frames remain temporary and distinct from earned permanent items.
- Offline progress is pending/not verified, never fabricated as unlocked. Retry submissions idempotently after reconnect; logout/account switches must not leak progress/ownership.

## Backend/security prerequisites before either feature can activate
- Resolve an authenticated principal on the server; do not accept claimed display names, GitHub logins, founder flags or reported time as proof.
- Persist deduplicated qualifying events and auditable grant/revoke records transactionally. Publish a versioned policy and approved rule values; separate cosmetic ownership from privileged roles.
- Agree API/storage/retention design before implementing new contracts. Host sibling remains untouched. Review coordinator issues/PRs before shared contract changes.
- Required tests: self-confirmation; duplicate retry; repeated pair; different recipients; replay/cross-account attempt; week boundary/timezone; offline pending; failed grant transaction; revocation; logout/switch isolation; hiding cosmetic; large font/320dp/RTL; abuse-report appeal.
- No UI claiming an active contribution/reward economy until these prerequisites pass. No server production deploy in this phase.

## Concrete audit finding alongside this design
PC stream virtual pad previously guarded both press and release with `enabled()`. Entering edit mode while held could skip the release; pointer coroutine cancellation also bypassed release without `finally`. Edit entry did not reset analog state.

Local fix: one press/release callback transaction with `finally`, current enabled/action callbacks at gesture start, edit state read directly and neutral state sent on edit entry. Four JVM cases cover normal release, disabled-while-held, cancellation propagation and failed-down cleanup. These tests do not prove host delivery or device multi-touch behavior. Real disconnect/background/stick-neutralization checks remain pending.

## Authentication feasibility audit — rollout still blocked
Source review of `cloudflare/worker.mjs`:
- OAuth callback/exchange branch has embedded fallback client secrets, including the alternate dashboard client. Treat embedded secrets as exposed; rotate them and migrate all supported client secrets to managed server configuration. Never include their values in reports, commits or tests. Removing a fallback alone does not revoke an exposed credential or erase history.
- Callback/exchange reads state and uses it to derive a polling session ID; no state-cookie equality or one-time pending-session verification was observed in that branch. A random-looking state value alone is not CSRF/session-binding proof.
- Initial source finding: public `/api/github/oauth/complete` accepted a token and claimed login without provider verification. Prepared source hardening now verifies GitHub `/user`, requires a matching canonical login and stable positive provider ID, and persists/forwards provider metadata only. A shared two-second deadline covers fetch and body parsing; unauthorized/mismatch rejects401, provider failure/timeout503, before session writes. Session IDs must match the existing allowed character/length format, not be silently rewritten. This does not bind the caller to a pending polling session or prevent login CSRF. No production deploy.
- `/api/leaderboard` POST still accepts claimed identity and reported total; it must not be reused as verified grant evidence.

Safe next work: agree how APK polling, browser callback and dashboard bridge share a one-time session binding; validate provider identity server-side, avoid bearer tokens in URLs, reject replay/mismatched principals and fail closed when managed credentials are missing. Existing bridge consumers must be coordinated before token-delivery or session contracts change. Both `/complete` and callback profile lookup now verify provider identity in source. Callback session/state binding, dashboard bridge token delivery, secret/configuration, deployment and reward grants remain unresolved. The legacy unsigned cookie fast-path has now been removed in source; HTML callback expires that cookie rather than minting a 30-day bearer-token container. Existing token return/deep-link fields remain unchanged. These are source findings, not a live exploit or production configuration test.


## Legacy cookie retirement (source only)
- `/api/github/oauth/login` and `/api/auth/login` no longer decode/trust `xy_gh_session`, create a polling session from its contents, or return its token through a deep link. Any old cookie proceeds through provider authorization, same as no cookie.
- Normal HTML callback expires the old cookie (same path/security attributes, Max-Age0); no new long-lived bearer cookie is created. This is not provider token revocation. Dashboard and JSON callback branches do not use the old shortcut; previously issued cookies can remain until normal expiry if the browser does not visit the HTML callback.
- Tests include forged/malformed/absent cookies, both login aliases, no polling write/provider lookup from the cookie, empty poll result and callback expiry with existing app return format intact.
- This intentionally removes an insecure convenience path. It is not a signed browser session replacement and does not solve callback state binding.

## Session-binding design constraint — coordinate before changing consumers
The default authorization redirect in this source goes to a different registrable domain from the API login route. Simply adding a cookie equality check to every callback can break legitimate cross-domain login; a cookie set by one domain is not available to the other. Never weaken verification to hide that mismatch.

Preferred design to agree: a single configured callback origin and server-created one-time pending authorization, browser-state binding on that origin, short-lived opaque code handoff to the approved APK/dashboard consumer, and a separate high-entropy polling capability bound to that pending flow. Store consumption transactionally (e.g. DO); plain eventual KV read/delete is not proof of single-use. Validate provider identity on every completion path, reject expired/replayed/mismatched flow/principal, and never allow a completion payload to choose an unrelated polling session. PKCE or HMAC state alone is not proof of possession of an arbitrary session ID. Specify migration for existing APK polling and dashboard bridge before implementing or deploying this contract. No new endpoints/keys/secrets or consumer changes are introduced here.


## Callback profile failure handling (prepared source, not deployed)
The callback/exchange branch now uses the same bounded provider resolver as `/complete`. A failed, unauthorized or malformed `/user` response returns an error before edge/KV/DO session persistence, dashboard redirect, JSON token response or APK deep link. All aliases sharing that branch receive the same check. Normal successful profile and return field names remain unchanged. Profile lookup has a two-second total fetch/body deadline; token exchange itself still has separate unbounded external calls and embedded fallback credential issues, not solved by this step.

Regression evidence: unauthorized/unavailable/missing ID/missing login/broken JSON/network failure across APK and dashboard flows; assert no writes, redirect, cookie or token output and no edge polling session. Successful JSON/storage/dashboard shape and existing HTML callback compatibility tests pass. These tests intentionally use the current state format; they are not evidence that callback state is bound, replay-proof or safe to use for reward grants.
