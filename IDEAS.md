# XyDesk Remote — product ideas

Product attribution: **XyVerse Technology Global**. These are proposals for prioritization, not implementation commitments. Effort is a rough planning estimate: S (small), M (medium), L (large).

## Feature ideas (20)

| # | Idea | User value | Effort | Status |
|---:|---|---|:---:|---|
| 1 | Per-device HUD layout presets (compact, touch, keyboard-heavy) | Switch interaction styles without overwriting a custom layout | M | Proposed |
| 2 | Search and quick actions for saved devices | Reach a device faster when the saved list grows | S | Proposed |
| 3 | User-defined on-screen shortcut keys/macros | Send common RDP shortcuts without a physical keyboard | M | Proposed |
| 4 | Bluetooth game-controller mapping | Use a controller for pointer and directional input | L | Proposed |
| 5 | Multi-monitor chooser and fast monitor switching | Navigate Windows desktops with several displays | L | Proposed |
| 6 | Adjustable swipe-scroll speed and acceleration | Make long documents and spreadsheets easier to navigate | M | Proposed |
| 7 | Reconnect-to-session action with saved retry policy | Recover faster after a transient network drop | M | Proposed |
| 8 | Per-session audio output and mute controls | Manage remote audio without leaving the session | M | Proposed |
| 9 | Optional clipboard image transfer with preview | Move screenshots while showing what crosses devices | L | Proposed |
| 10 | RDP drive/file transfer flow | Move files without a separate cloud service | L | Proposed |
| 11 | Per-device display presets and DPI profiles | Switch quickly between readable text and more remote workspace | M | Proposed |
| 12 | QR import for connection settings with secrets excluded | Configure a device without retyping host and non-secret options | M | Proposed |
| 13 | Optional Wake-on-LAN helper for supported networks | Start a sleeping PC before connecting | L | Proposed |
| 14 | Read-only picture-in-picture session preview | Keep remote status visible while using another phone app | L | Proposed |
| 15 | Per-device disconnect and reconnect notifications | Notice a dropped session without keeping the screen open | S | Proposed |
| 16 | Session handoff between a user's own Android devices | Resume connection setup on another personal device | L | Proposed |
| 17 | Per-profile startup application and working-directory presets | Open directly into a common remote task | M | Proposed |
| 18 | Configurable mouse acceleration and pointer sensitivity | Match pointer behavior to phone size and user preference | M | Proposed |
| 19 | Optional session timer and usage summary | Help users monitor long-running remote sessions | M | Proposed |
| 20 | Per-profile network and visual-quality presets | Choose a saved transport/quality configuration for known links | M | Proposed |

## Improvements beyond new features (20)

| # | Improvement | User value | Effort | Status |
|---:|---|---|:---:|---|
| 1 | Clear keyboard/clipboard channel state and last-result feedback | Distinguish local input from an accepted remote request | M | Proposed |
| 2 | Guided connection troubleshooting with evidence, not guessed causes | Shorten setup and support without inventing a diagnosis | M | Proposed |
| 3 | Redacted, user-controlled diagnostics export | Share useful failure details without host credentials or clipboard contents | M | Proposed |
| 4 | Accessibility pass for TalkBack labels, focus order, and touch targets | Make core remote controls usable by more people | M | Proposed |
| 5 | Contrast and reduced-motion review across light/dark themes | Improve readability and comfort in varied environments | S | Proposed |
| 6 | Indonesian and English terminology review | Keep security, network, and RDP wording consistent | S | Proposed |
| 7 | Profile/credential lifecycle and session privacy review | Reduce accidental exposure and clarify what is stored | M | Proposed |
| 8 | Battery, memory, and input-latency profiling on representative phones | Catch regressions before users experience them | M | Proposed |
| 9 | Windows-host setup, clipboard-policy, and recovery documentation | Help users check host policy before changing phone settings | S | Proposed |
| 10 | Release provenance and signing-artifact verification checklist | Make releases auditable and safer to update | M | Proposed |
| 11 | Native queue wraparound, concurrency, and teardown regression coverage | Catch ordering and lifecycle failures before release | M | Proposed |
| 12 | Network benchmark matrix for loss, jitter, bandwidth, and RTT | Separate codec effects from transport and physical latency | M | Proposed |
| 13 | FreeRDP runtime-version and server-capability compatibility matrix | Avoid shipping flags unsupported by a bundled client or host | M | Proposed |
| 14 | Input acceptance telemetry that never records keystrokes or clipboard data | Show whether a control reached the local native queue safely | M | Proposed |
| 15 | Clipboard-size, allocation, and privacy threat review | Keep large transfers responsive without logging private content | M | Proposed |
| 16 | Connection-state and reconnect race fault-injection tests | Verify retries and cancellation under timing stress | L | Proposed |
| 17 | Certificate prompt and trust-store lifecycle test matrix | Prove changed fingerprints are never silently trusted | M | Proposed |
| 18 | Preference migration tests for saved profiles and custom ports | Preserve existing connections during UI and schema changes | M | Proposed |
| 19 | Android permission-denial and process-death recovery tests | Make partial failures understandable and recoverable | M | Proposed |
| 20 | Release artifact install, signer, and version smoke checks in CI | Catch packaging mistakes without producing local debug builds | M | Proposed |

## Scope note

Prioritize proposals with kall before implementing unrelated work. Existing user-approved work may proceed independently; this list does not imply approval for all twenty items.
