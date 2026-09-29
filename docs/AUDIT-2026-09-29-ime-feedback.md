# Audit IME latency, clipboard, and feedback — 2026-09-29

Scope: Android soft-keyboard lag/delayed text, direct clipboard sync compared with Microsoft Remote Desktop, and the requested in-app feedback/suggestion popup plus idea backlog. Source audit only; no Android device or Windows RDP host is attached, and no local build was run.

## Prioritized findings

1. **[HIGH] Soft-IME composing text is deliberately held locally until commit.** `client/Android/Studio/freeRDPCore/src/main/java/com/freerdp/freerdpcore/presentation/ImeCompositionBuffer.java:16-35` only stores composing updates; `SessionView.java:674-694` sends text only from `commitText` or `finishComposingText`. This matches characters appearing late or together after Enter. Replace the current remote provisional text by a minimal common-prefix diff on each composing update, then make commit/finish idempotent; cover autocorrect, deletion, Unicode, and surrogate pairs in JVM tests.
2. **[MED] Text delivery loops synchronously on the UI/input callback.** `client/Android/Studio/app/src/main/kotlin/id/xydesk/remote/ui/SessionSurfaceController.kt:160-173` turns every UTF-16 unit into a separate native down/up pair. The native event queue is asynchronous, but long IME commits still do per-character JNI work on the callback thread. Keep live composing deltas small and preserve ordering with Enter/backspace; do not move input to an unordered worker.
3. **[MED] Both clipboard directions already have automatic paths, but failure visibility was incomplete.** `XyDeskSessionActivity.kt:122-158` watches Android system-clipboard changes and sends text when the profile channel is enabled and RDP is connected; `XyDeskSession.kt:343-355` puts remote text into Android's primary clipboard with an `rdp` label so it is not echoed back. The manual panel actions are fallbacks. Surface generic failures only (never clipboard contents), and validate actual Windows acceptance on-device. Gboard private history may instead be an IME text commit, not a system clipboard change.
4. **[MED] No clear user-initiated feedback route is present.** Add an accessible popup with Bug / Feature suggestion / Other categories; submit only through Android's share sheet after explicit user action. Do not silently upload, persist, or log feedback, host names, credentials, clipboard, or diagnostics.
5. **[LOW] `IDEAS.md` has only three proposed rows.** User requested at least ten feature ideas and ten non-feature improvements. Add both lists with impact/effort and keep them Proposed until prioritized.

## Fix disposition / verification

- Items 1–2 are in scope: stream only the delta between successive IME compositions so text renders while typing, suppress duplicate commit text, and keep deletes/Enter ordered. Regression tests stay in JVM CI.
- Item 3: preserve automatic both-way clipboard flows; surface generic failure notices on Android-to-remote send rejection and remote-to-Android clipboard-service failure. Ordinary Android Copy and remote clipboard application still require device/host acceptance testing. Gboard's private clipboard remains subject to Android IME/system-clipboard boundaries; never log clipboard content.
- Item 4: feedback uses an explicit Android share chooser; the app itself sends nothing to a server.
- Item 5: the two requested idea lists are proposals, not a commitment to implement all twenty.
- No local build or debug APK. Verify only through compile/JVM-test CI and signed release CI; target device and host behavior remains unverified until kall tests it.
