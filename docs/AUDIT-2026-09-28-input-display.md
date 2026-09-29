# Audit input, clipboard, and display — 2026-09-28

Scope: Android soft-keyboard text input, mouse HUD glyphs, Windows display scale, remote-resolution acknowledgement, and phone-to-Windows clipboard. Source-only audit; no local build, RDP host, or device session was available. Findings below are confirmed source behavior or clearly marked hypotheses; no claim is made that a host-side symptom has been reproduced.

## Findings

1. **[HIGH] IME composition can be emitted more than once and visibly flicker.** `freeRDPCore/.../SessionView.java:641-713` sends each `setComposingText` update immediately, synthesizing backspaces for the prior draft, while the same `InputConnection` forwards raw key events. Soft IMEs can provide both text composition and key events. Buffer composition locally and commit once; preserve non-text keys.
2. **[HIGH] Gboard clipboard insertion is not equivalent to Windows clipboard sharing.** `XyDeskSessionActivity.java:121-151` watches Android `ClipboardManager` changes and sends them through CLIPRDR; Gboard can instead insert its private history via the input connection. The RDP client has a text/plain-to-CF_UNICODETEXT synthesizer (`winpr/libwinpr/clipboard/synthetic.c`), so source review alone does not establish a format-conversion defect or explain host rejection. Add an explicit “send this text as remote clipboard” action and retain no clipboard contents in logs.
3. **[MED] Mouse HUD containers are round, but the left/right/middle mouse glyphs are angular.** `app/.../SessionKeyLayer.kt:276-279` uses `XyIcons.ClickLeft/Right/Middle`; `app/.../components/XyIcons.kt:126-147,344-353` draws rectangular mouse outlines and click zones. Redraw those vectors with curved outlines and rounded button zones.
4. **[MED] Remote DPI is sent as Windows DesktopScaleFactor, but app cannot confirm host application.** `core-rdp/.../SessionManager.kt:373-390` invokes the DISP monitor-layout request; native `android_disp.c:78-127` sets `DesktopScaleFactor`. A successful call means the request was queued, not that Windows applied it. Distinguish this from local zoom and avoid “applied” language; host support must be tested.
5. **[MED] Resolution UI treats a queued DISP request as success.** `app/.../XyDeskSession.kt:804-827` reacts to `resizeRemote()`'s send result, while actual dimensions arrive later through telemetry. Wait for the reported width/height, then use the existing reconnect fallback and report the actual dimensions if the host refuses the requested size.

## Fix disposition and verification

- Finding 1: composition is buffered and committed once; a JVM regression suite covers composition, deletion, surrogate pairs, and duplicate soft-IME events.
- Finding 2: the text dialog now distinguishes direct typing from updating the Windows clipboard, allowing Gboard-pasted text to use CLIPRDR. End-to-end acceptance on the target host remains pending.
- Finding 3: left/right/middle mouse glyphs now use rounded geometry.
- Finding 4: session-panel copy states that DPI is a Windows DisplayScaleFactor request; no host acknowledgement is claimed.
- Finding 5: requested resolution is checked against actual telemetry before success; timeout triggers the existing reconnect path and reports the dimensions the host reports.
- CI compile and JVM tests passed: [branch run 36501234993](https://github.com/xykal/XyDesk-Remote/actions/runs/36501234993), [main merge run 36501553819](https://github.com/xykal/XyDesk-Remote/actions/runs/36501553819), [version-bump run 36501831764](https://github.com/xykal/XyDesk-Remote/actions/runs/36501831764). Debug jobs were skipped; there was no local build. Signed release build and device/host verification remain pending.

## Existing behavior and scope

- Resolution presets are passed at initial connect and can also use the DISP channel; only actual telemetry can confirm a change.
- The session panel already exposes Windows remote scale separately from local zoom; this pass improves wording/status, not a claim that every host honors the request.
- The auto clipboard path sends only when the Android system clipboard changes and sharing is enabled. The new explicit text-to-remote-clipboard action addresses IME/Gboard text that was inserted into a focused app but never entered Android's system clipboard.
- Device-side typing, right-click Paste, DPI, and resolution behavior remain runtime acceptance checks after the release build.
