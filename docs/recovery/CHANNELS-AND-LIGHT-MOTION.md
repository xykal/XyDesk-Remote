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
- 140ms finite home-route fades, system reduce-motion + battery/lifecycle gates.
  No globally enabled infinite shimmer/float loops; no native/APK-size stripping.
- Initial chat scrolls to newest once; later arrivals/snapshots do not pull readers
  away from older messages. A new-message badge/jump control remains available.
- A short system sent tone occurs on a correlated positive server ACK only, with
  bounded ID dedupe for HTTP/WS/history races. Respects silent setting, volume,
  DND and lifecycle; reusable generator is released on screen/account disposal.

Not a release-completion claim: Android CI compile/unit tests and target-device
runtime/performance/accessibility tests are required. No new signed recovery APK,
release/tag, staff grant, storage migration or production channel deployment has
occurred just from writing this source. Live storage quota may still block content
reads/writes; no paid upgrade or namespace/data reset is allowed. All earlier full
outbox/history/media, genuine controller, secure BOTH Direct, mic/provisioning and
connection/runtime requirements remain open and are not replaced by these additions.
