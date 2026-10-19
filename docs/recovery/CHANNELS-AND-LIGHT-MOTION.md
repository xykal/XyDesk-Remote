# Requested additions — implementation/validation status

Owner requested lightweight app-wide UX motion/performance, official guide/info/
announcement channels with founder/admin/team-only posting, #channel navigation
from chat/feed, smooth reader-respecting global-chat autoscroll and sent sound.

Implemented in this source increment:
- Three canonical channels: #PanduanPengguna, #Informasi, #Announcement (also
  #Pengumuman). Shared text renderer recognizes known tags with boundaries;
  they open an in-app channel screen from chat/feed/floating chat, not a browser.
- Public paginated read, explicit publish acknowledgements, retained history,
  idempotent retries, bounded body/response/rate limits and 15s first-page cache.
  Backend never fabricates an empty successful channel on a storage error.
- Server permission checks use provider-verified numeric GitHub IDs. Founder ID
  297800307 was verified via GET /users/xykal. Body nickname, role, author_id and
  claimed github_login cannot grant staff powers. Admin/team default to nobody.
  Additional staff IDs require explicit owner configuration; do not invent staff.
- Configure optional Worker secret CHAT_CHANNEL_ROLE_IDS as JSON such as
  {"admin":[<verified numeric GitHub ID>],"team":[<verified numeric GitHub ID>]}.
  This is deployment configuration only, NOT an API accepting client role claims.
  Use a secret to preserve it across deployments; never paste access tokens there.
- Read UI follows backend can_post; backend always rechecks. Failed send preserves
  draft + nonce. expected_login rejects sending an old-account draft with a new
  account's token. These channels do not replace existing chat/feed data.
- 140ms finite single-tree home-route fades, system reduce-motion + battery/lifecycle gates.
  No duplicate chat/media trees during transitions, no globally enabled infinite
  shimmer/float loops; no native/APK-size stripping.
- Initial chat scrolls to newest once; far jumps skip intermediate row animations
  and sending no longer waits for scrolling to finish; later arrivals/snapshots do not pull readers
  away from older messages. A new-message badge/jump control remains available.
- A short system sent tone occurs on a correlated positive server ACK only, with
  bounded ID dedupe for HTTP/WS/history races. Respects silent setting, volume,
  DND and lifecycle; reusable generator is released on screen/account disposal.

Validation: Worker tests 123/123 pass. Android Kotlin/core/app gates passed
f40a3f2 (39327147211) and fc4f181 (39327719803). Single-tree motion refinement passed
run 39328956477 at 3938631; later changes are Worker-only. Production Worker is now
68b7fc17-ed1a-4083-babc-529d417d6228: channel catalogue and empty guide posts
return 200; anonymous forged-founder POST returns 401; unknown channel returns
404. Positive staff publishing and populated-channel reads are not tested live.
Existing global history still returns 503/quota/retry300; this has NOT recovered.
The first deployment exposed a live DO routing 404 missed by stub-only tests; an
edge-to-real-GlobalChat regression test and explicit routing headers were added.
Official channel traffic is now isolated in xydesk-official-channels-v1 within
the existing binding. Existing global chat still uses xydesk-global-chat-v1; no
existing messages, namespaces or migrations were reset/changed. No public test
messages were created. Assets/config retained current-main 5990eb9 unchanged.

Not a release-completion claim: target-device
runtime/performance/accessibility tests are required. No new signed recovery APK,
release/tag, staff grant or storage migration has occurred. Live storage quota may still block content
reads/writes; no paid upgrade or namespace/data reset is allowed. All earlier full
outbox/history/media, genuine controller, secure BOTH Direct, mic/provisioning and
connection/runtime requirements remain open and are not replaced by these additions.
