# Chat/offline/push/code and shared-control delivery plan

## Implemented, pending Android runtime validation
- Encrypted per-account offline text snapshot, up to 80 confirmed messages. No
  view-once messages, attachment bytes or unsent outbox. Read/write on IO;
  debounced atomic writes under noBackupFilesDir with device Keystore AES-GCM.
- Individual message notification tags; chat, mentions, replies, announcements
  and updates routed separately. High importance is a default, not permission to
  override DND or user channel settings. Feed remains non-urgent. Mark-read currently
  clears all Global Chat notifications, not unrelated social/update notifications.
- Explicit Markdown fenced code blocks: lightweight lexical color hints,
  selection, Copy and Android share sheet. No script evaluation. Not a full parser.
- Foreground WS/history no longer competes with notifier's 5-second polling.

## Required acceptance checks (not yet performed)
1. Load chat online, wait for cache write, kill process, airplane mode, reopen:
   cached text renders with no dependency on network; no fake sent/online state.
2. Switch account/logout: no old-account text/pins; hide/delete then reopen:
   removed text does not return from cache. Verify server deletion reconciliation.
3. View-once/media: no bytes persisted; missing cached media clearly labelled.
4. Verify Keystore invalidation, corrupt/oversized snapshot, storage full and app
   reinstall fail closed without crash. Validate cache ownership after auth races.
5. Multiple normal/mention/reply/announcement/update messages: separate content,
   no overwrites, opening correct destination, reply/read actions, tray bounded;
   test notification permission denied, Android channel overrides, DND and Doze.
6. Code fences, unclosed fences, CRLF, long lines, touch selection and share sheet.
   Current message-length limits remain; long-script/file sharing is NOT solved.
7. Throttle network, record bytes to first text, first-render latency, main-thread
   work and background traffic. Current improvements are not measured speed claims.

## Next: UI consistency
Use existing typography/spacing/radius tokens rather than independent per-screen
values. Review compact-overlay and full-screen modes separately. Validate 320dp
width, large system font, RTL, light/dark, keyboard/IME and safe-area insets before
claiming layout completion. No blanket replacement of every dimension.

## Next: metadata-first history and media
Requires versioned/capability-negotiated server/client design; preserve old clients.
Do not strip attachment_data before a compatible download path exists. Add delta
history, bounded encrypted disk media policy and identity-scoped cache invalidation.
No backend deployment authorized by these client-only changes.

## Shared RDP control (NOT implemented or enabled)
Must coordinate protocol/host contracts in repository coordination issues before
changes. Design requires authenticated invitation, short expiry, explicit host
consent, view-only default, separately granted input control, conspicuous active
indicator, instant local revoke, disconnect/session-expiry revoke and audit events.
Keep one input owner or explicit arbitration; never silently forward clipboard,
files, microphone or credentials. Validate authorization server-side/host-side,
not only via a hidden UI button. Feature cannot honestly be promised without host
support, security tests and physical-device session tests. No release build now;
future release must remain a draft.
