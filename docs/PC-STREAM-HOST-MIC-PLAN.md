# PC Stream consolidation, session mic and readiness

## Owner decisions
- Target host is **this repository's host-agent**, not a competing change to sibling XyDesk.
- Replace legacy Koneksi PC with PC Stream; preserve standard RDP.
- Distribute one `XyDeskRemoteHost.exe`, not separate Standalone/companion XyDesk DLLs/ZIP.
- Remove VOICE ON/OFF from global and floating chat. Session microphone control only. Chat voice-note attachments/audio playback/notification tones are separate and remain available.
- Need a truthful setup/readiness panel for mic/audio AND games/stream in RDP.
- No APK or EXE build, deployment or release in this phase; future release remains draft.

## Source work prepared (runtime acceptance pending)
1. Shared global/floating chat: remove TTS reader, preference reads, controls in both headers and settings. Old stored preference no longer activates it. Does not remove sending/listening to audio attachments.
2. Home add menu and new device editor no longer create legacy PC mode; use PC Stream. Legacy saved PC Connect from home opens PC Stream without forwarding Windows credentials. Preserve stored profiles/export data; the legacy editing/auto-resume/import deep-link compatibility paths still need an explicit migration sweep, not destructive deletion or pretending old IDs are new hub IDs.
3. CMake and future native/release recipe link host UI, UDP/audio and host core into one statically linked application target. Release artifact lists only the host EXE, no Standalone/DLL/ZIP variants. Build script shell/source contract checks only; **no native binary compiled or import table tested**. Windows system DLLs are still OS dependencies, not files to bundle. Old public download aliases/releases are not deleted; website/download metadata migration remains pending.
4. PC Stream mic now requests RECORD_AUDIO permission and routes to the active direct client or the already negotiated WebRTC `xy-mic` sender. Track disabled initially; disable on stop/session change and guard late permission results by session identity/lifecycle. UI says MIC DIMINTA (requested), not proof of recording/Windows detection. `XyHubStreamViewer` sender enable and recording call are wired; track cleanup added, no new SDP/wire tag.
5. Direct UDP audio: stop/release AudioRecord when mic intent becomes off instead of leaving recorder open in a sleeping loop; release failed first recorder before fallback, verify recording state, reset intent on init/capture failure, recheck intent after read and serialize off/on cleanup restart. `micCapturing` is local capture status only. Hardware races/errors need device tests; no Windows endpoint/host acknowledgement proof.

## Important host gaps found — not solved by packaging
- This host-agent direct engine uses XYV1 JPEG/UDP and XYM1 audio. Android hub WebRTC uses a separate signaling/peer path; do NOT assume this one EXE already implements the external hub host. Inventory listener/host ID/auth/capture capabilities before promising a universal host.
- `main_host.cpp` On-Mic setup currently fetches/runs a remote PowerShell script hidden, and reports readiness after checking registry policies. Neither is evidence that a virtual recording device exists, audio reaches it, or a game selected it. Replace with local/native, consented and measured readiness/setup; do not claim script completion means mic detected.
- `web/onmic.ps1` installs external VB-CABLE and modifies policies. Single application EXE does not remove Windows driver requirements. Driver packages must be official/signed, licensing checked, install consent explicit, reboot/failed checks reported. Do not silently execute remote scripts or bundle unsigned drivers.
- Existing native UI contains unverified TLS/QUIC/E2EE/120FPS readiness claims; raw UDP paths must not inherit those security/performance promises. Audit and correct before release.

## Readiness panel: Android evidence view implemented; native setup still pending
- **Read-only scan first:** session type (RDP/console), supported OS, elevation, physical/virtual audio render AND capture endpoints, selected game recording endpoint, signal level/host packet receipt; distinguish unknown/missing/available/tested.
- Stream: actual capture backend, encoder availability, GPU driver, first frame/timeouts and loss; do not report physical console or hardware encoding by inference.
- Actions only after disclosure/consent: Windows sound/privacy settings, official driver/install/reboot guidance, supported RDP redirection policy fixes with error handling. Never silently disable security, change firewall/RDP settings or replace game binaries.
- A UI panel inside Android cannot truthfully run Windows checks without a trusted host diagnostic channel. Use existing status fields where sufficient; coordinate new host fields/protocol first. Keep local permission, local capture, host receipt, virtual input detection and game selection as separate statuses.

## Game reports and acceptance
- Roblox: owner reports **VM detection**. Do not spoof VM/anti-cheat, replace protected components or claim a bypass. Establish actual supported OS/environment; advise supported physical Windows/console if virtualization is rejected. PC Stream does not make a VM physical.
- SA-MP: server list/add server works, but Connect leaves loading cursor and no game. Not reproduced. Need game/launcher version, GTA executable path, process creation/exit code/events, runtime dependencies, permissions, session/display mode and crash logs (redacted). Determine whether game fails on PC itself before blaming streaming. No unsupported runtime/download guesswork, no claims that mic/policy changes fix launch.
- Device/Windows checks: real permission allow/deny/revocation, mic off releases capture, quick OFF/ON, background/reconnect/switch, WebRTC sender start/error callbacks, Windows input device existence/levels and game selecting it. Prove standalone EXE dependencies/launch/AV/signature and all host UI functions only after native validation/build is explicitly authorized.


## Verification gate correction
Source inspection found the existing compile-check job, despite its “no native” label, still contained a Windows host compilation step. That step is now removed from this gate and replaced by bash syntax/source-contract checks. Future explicitly authorized release still has its single-host recipe, but no release/native job is dispatched here. Earlier gate success alone must not be described as proof that no native compile could run; the historical step could run or skip depending on toolchain availability. No APK assembly was part of that gate. This correction is a real execution guardrail, not just a renamed step.


## Direct-host entry point correction
Source inspection found LAN discovery/direct-client methods existed but pairing UI exposed only the OTHER hub host (9-digit ID/PIN). Added an explicit Host Direct screen, default for this EXE, with LAN scan/host selection, private IPv4/Tailscale-name input and acknowledgement of owning/trusting the private network. Keep the distinct hub host option labelled separately; do not treat its IDs as this EXE's credentials. Public IPv4/unknown domain forms are rejected by local policy until a secure direct-host handshake exists. This is only temporary containment: DNS is not resolved/verified by the pure policy, host listener auth is not repaired, and a private LAN is NOT automatically encrypted/authenticated. Never expose the UDP listener publicly. Pure network-policy and permission-result policy cases added; device/network/native-host verification pending.


## RDP readiness phase implemented in source
- Session HUD menu now opens Kesiapan RDP · Mic & Game. Read-only panel samples existing evidence; no remote shell, network probe, driver install, registry/firewall change or game-binary modification is triggered by opening it.
- Foreground/resumed session sampling shows local RECORD_AUDIO permission, bridge request and actual AudioRecord recording flag separately. Existing XYST1 version1 parsing adds flags, level, unsigned audio/mic counters and bounded endpoint text. Monotonic freshness expires reports after5seconds; stale values are not shown as current routing.
- Connected UDP socket filters peers to configured host/port; this is source filtering, NOT cryptographic auth. Reset DatagramPacket receive length before each packet to avoid long audio truncation after short status packets. Mic sends via that pinned socket, avoiding a second DNS lookup/possibly different destination.
- RDP bridge toggle now requests permission, binds result to current native instance and foreground lifecycle, stops bridge mic on background/dispose/profile change, and does not persist microphone preference. Bridge startup microphone is off; explicit session action required.
- **Native RDP audio input is separate:** stored native microphone configuration and FreeRDP audin behavior remain unchanged; no supported live-disable API was found in the audited wrapper. Panel warns that bridge OFF does not disable this channel; profile change/reconnect is required to alter configured native input. Do not claim all mic capture is off or that Windows/game detection is verified. A unified supported mic-control design is still pending.
- Prevent a stale terminal session callback from stopping a newer bridge by checking native instance ownership before bridge teardown in SessionManager.
- Four new pure JVM cases cover existing status header/version/bounds, unsigned counters, endpoint detail cap, and monotonic freshness. No UDP/device/native endpoint or game behavior is validated by these cases. Panel viewport, permission flows, native-channel co-existence and background capture need device testing.

## Owner release permission and native host hardening
Owner now authorizes compile/build and a **draft release after completion/verification**; not an immediate build from unfinished source, debug release or production deploy. Secure direct pairing/transport on both endpoints explicitly approved; see DIRECT-SECURE-TRANSPORT.md for coordination/design and blocking tests.

Native source changes prepared:
- Local read-only Windows readiness window enumerates active WASAPI render/capture endpoints and default communications input, reports session type/elevation, and lists matching SA-MP/GTA/Roblox process names/PIDs. Process visibility is not launch success or game compatibility. Inventory is not a level/loopback test or proof of selected game input.
- Refresh does not write policies, install drivers or start programs. Separate user-click actions open Windows sound/privacy settings; opening the official audio-driver site requires confirmation and does not install anything.
- Removed hidden local/downloaded PowerShell setup, startup policy/firewall/privacy writes, Windows-password generation/rotation/sync, legacy auto relay heartbeat and fake SHA256/E2EE/TLS/120FPS UI claims. Does not silently revert earlier OS settings or delete saved credentials.
- Legacy UDP listener now off by default, explicit owner private-test CLI flag only. Authenticated transport still unimplemented; this is an intentional release blocker, NOT a finished secure host or a replacement for firewall isolation.
- Single EXE recipe includes host_readiness.cpp and Windows system libraries. New Windows CI uses MSVC `/Zs` (syntax only: no EXE/OBJ/link) to validate Windows API code. Native runtime and final EXE linkage remain unverified until their actual checks pass.

### Verification and web containment follow-up
- Windows source syntax gate 39205488141 initially FAILED on pre-existing Windows min/max macro collisions in stream/audio code. Fixed by defining NOMINMAX before Windows headers (7296670). Replacement **39205592281@7296670 SUCCESS** with MSVC `/Zs`; no object/EXE output, no link/runtime test.
- Android Kotlin/JVM gate **39205487271@5e65527 SUCCESS** (includes Android Direct warning change). Prior **39203623724@afe9b86 SUCCESS** also confirmed; earlier pending note is superseded.
- Web source now withdraws false QUIC/TLS/120Hz/password promotion and old download/execute setup instructions; new single EXE is clearly NOT publicly available yet. Six old Windows setup entry points remain at their paths but print retirement guidance and return failure without elevation, download, registry/firewall/privacy/service changes or process launch. This is containment, NOT a completed installer; existing machine settings are not reverted.
- Historical release-state.json and download aliases are unchanged: they describe old published assets, not a fabricated new release. No web deployment performed. New-host asset metadata must only change after a real approved release/artifact exists.
- Current local source-contract suite: **7 PASS**; Worker suite: **82 PASS**. Host/index inline JavaScript syntax checked separately. Runtime/game signal and secure transport requirements remain OPEN.
- WebCI39205892870@1bb72fd exposed an outdated Worker test expecting the retired agent BAT to still download/open a script. Updated to verify the stronger current contract: retirement notice, nonzero exit, no PowerShell/download/process/firewall/elevation commands, and unchanged downloadable route/content headers. Latest local Worker suite rerun: 82 PASS. Replacement WebCI result must be checked rather than reporting the failed run green.
