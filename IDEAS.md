# XyDesk Remote — product ideas

Product attribution: **XyVerse Technology Global**. These are proposals for prioritization, not implementation commitments. Effort is a rough planning estimate: S (small), M (medium), L (large).

## Feature ideas (26)

| # | Idea | User value | Effort | Status |
|---:|---|---|:---:|---|
| 1 | Per-device HUD layout presets and JSON export/import | Switch or share HUD layouts across devices without manual rebuilding | M | Implemented (JSON export/import in v0.5.13) |
| 2 | Search and quick actions for saved devices | Reach a device faster when the saved list grows | S | Proposed |
| 3 | User-defined on-screen shortcut keys/macros and categorized key picker | Add Combos, F1–F12, Single Keys, and Numpad keys directly from top layout bar | M | Implemented (Categorized picker + top bar add in v0.5.13) |
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
| 20 | Per-profile network and visual-quality presets with auto cellular/metered detection | Automatically enable AVC420 low-bandwidth mode on cellular or metered links | M | Implemented (Auto cellular/metered detection in v0.5.13) |
| 21 | One-shot modifier keys (`Ctrl`, `Shift`, `Alt`, `Win` 1x) | Tap a modifier once to apply to the next key or click and auto-release | S | Implemented (v0.5.13) |
| 22 | SSH tunnel / jump-host port-forwarding profile | Reach internal RDP hosts over SSH without a separate tunnel app | L | Proposed |
| 23 | Biometric / device-credential lock before launching saved sessions | Protect saved profiles when handing an unlocked phone to someone else | M | Proposed |
| 24 | Stylus (S-Pen / USI) hover, pressure, and barrel-button mapping | Use tablet pens as precision desktop pointers and right-click tools | M | Proposed |
| 25 | Custom radial / pie menu HUD trigger | Group up to 8 frequent shortcuts into a single swipe-out ring button | M | Proposed |
| 26 | Local-to-remote smartcard / YubiKey (RDPDR Scard) pass-through | Authenticate to enterprise Windows hosts using a hardware security key | L | Proposed |

## Differentiators vs Microsoft Remote Desktop / Windows App (12)

| # | Differentiator feature | Why XyDesk Remote beats Microsoft Remote Desktop | Effort | Status |
|---:|---|---|:---:|---|
| 1 | Per-button customizable HUD overlay (drag, resize, Tap/One-Shot/Hold/Toggle, JSON share) | Microsoft RD only offers a fixed top bar; XyDesk lets users place any key/combo/mouse circle anywhere | M | Implemented |
| 2 | Built-in Wake-on-LAN (WoL) + TCP port readiness poller | Boot a sleeping PC from bed and auto-connect the second port 3389 wakes up (Microsoft RD has no WoL) | M | Proposed |
| 3 | Native SSH Tunnel / Jump-Host & Tailscale/WireGuard endpoint presets | Connect through an SSH bastion directly inside the app without running Termux/ConnectBot | L | Proposed |
| 4 | Multi-step macro sequence buttons (e.g. `Win+R -> cmd -> Enter` or custom script trigger) | Execute repetitive admin/gaming/coding sequences in 1 tap on the HUD | M | Proposed |
| 5 | Floating Picture-in-Picture (PiP) live monitor with 1-tap expand | Watch long builds, renders, or downloads while using WhatsApp/browser on the phone | M | Proposed |
| 6 | Precision trackpad with inertial scroll, 2-finger right-drag, and edge-scroll zones | Desktop-grade trackpad physics instead of Microsoft RD's basic pointer emulation | M | Proposed |
| 7 | Direct Android Share-Sheet to Remote Desktop (`Send file to PC` from any Android app) | Share a photo/PDF from Android Gallery directly into the active Windows session folder | M | Proposed |
| 8 | Live network HUD pill (real-time FPS, RTT ping, codec AVC444/AVC420, bandwidth) | Instant visibility into why a session lags; Microsoft RD hides transport telemetry | S | Proposed |
| 9 | Privacy Curtain / Blackout Remote Monitor mode | Blank the physical PC monitor while remoting in so bystanders at the office/home cannot watch | L | Proposed |
| 10 | Gamepad / XInput virtual controller & gyro-mouse mode | Map Bluetooth controllers or phone tilt to mouse/WASD for remote gaming & 3D apps | L | Proposed |
| 11 | Biometric App-Lock + Panic-Lock (`Win+L` auto-send on phone screen-off or shake) | Automatically lock the remote Windows session if the phone is locked or set down | S | Proposed |
| 12 | Per-app HUD profile switcher (Coding, Gaming, Office, Video Editing) | Switch complete HUD button sets in 2 taps during a live session without reconnecting | S | Proposed |

## Improvements beyond new features (27)

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
| 11 | Native queue wraparound, concurrency, and teardown regression coverage | Catch ordering and lifecycle failures before release | M | Implemented (v0.5.13) |
| 12 | Network benchmark matrix for loss, jitter, bandwidth, and RTT | Separate codec effects from transport and physical latency | M | Proposed |
| 13 | FreeRDP runtime-version and server-capability compatibility matrix | Avoid shipping flags unsupported by a bundled client or host | M | Proposed |
| 14 | Input acceptance telemetry that never records keystrokes or clipboard data | Show whether a control reached the local native queue safely | M | Implemented (v0.5.13) |
| 15 | Clipboard-size, allocation, and privacy threat review | Keep large transfers responsive without logging private content | M | Proposed |
| 16 | Connection-state and reconnect race fault-injection tests | Verify retries and cancellation under timing stress | L | Proposed |
| 17 | Certificate prompt and trust-store lifecycle test matrix | Prove changed fingerprints are never silently trusted | M | Proposed |
| 18 | Preference migration tests for saved profiles and custom ports | Preserve existing connections during UI and schema changes | M | Implemented (v0.5.13) |
| 19 | Android permission-denial and process-death recovery tests | Make partial failures understandable and recoverable | M | Proposed |
| 20 | Release artifact install, signer, and version smoke checks in CI | Catch packaging mistakes without producing local debug builds | M | Proposed |
| 21 | Exclusive modal/sidebar focus isolation and IME key-routing guard | Prevent remote surface from stealing keystrokes or drags while sidebar/dialogs are open | S | Implemented (v0.5.13) |
| 22 | Immediate non-buffered soft-IME key dispatch for digits, space, and symbols | Ensure every soft-keyboard tap appears immediately without dropped `sendKeyEvent` keys | S | Implemented (v0.5.13) |
| 23 | Dirty-region bitmap blitting and frame-pacing optimization on high-Hz displays | Reduce main-thread GPU upload overhead on 120 Hz phones | M | Proposed |
| 24 | Baseline Profile / R8 startup profile generation for Compose session UI | Eliminate first-open Compose jitter when opening the session panel or key picker | M | Proposed |
| 25 | Strict JNI local-reference and native heap leak sanitizer checks in CI | Prevent long-running RDP sessions from accumulating native memory | M | Proposed |
| 26 | Deterministic Compose UI screenshot and layout-bounds regression tests | Verify HUD controls, sidebar tabs, and landscape layouts never overlap across screen ratios | M | Proposed |
| 27 | Android API 24/36 emulator smoke matrix | Prove Android 7 installation, legacy notification paths, and Android 16 session behavior before release | M | Proposed |

## Scope note

Prioritize proposals with kall before implementing unrelated work. Existing user-approved work may proceed independently; this list does not imply approval for all twenty items.
