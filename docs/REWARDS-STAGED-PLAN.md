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
