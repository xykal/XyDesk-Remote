

# Progress audit — 2026-09-28

**Status:** source changes staged only in the shared workspace. The Android build and device behavior have not been verified; do not treat any item below as runtime-confirmed.

## Work completed in this pass

- **HUD and input:** HUD controls and preview are round, positions are draggable (including scroll up/down), default row spacing is roomier, and the legacy keyboard HUD action is removed. The rail remains the sole keyboard-toggle control. The IME advertises a text editor, forwards composing updates as they arrive, handles commit and surrounding deletion, and the rail state follows actual IME insets (including Android Back).
- **Local zoom and remote DPI are separate:** the session panel has a local zoom slider and a distinct Windows desktop scale control. Remote scale is sent as `DesktopScaleFactor` through FreeRDP's DISP monitor-layout channel, using protocol scale choices; when DISP/server support is unavailable, the preference is reverted rather than reported as applied. Resolution changes preserve the selected remote scale.
- **Network recovery:** after a session has connected, `Disconnected`, `unreachable`, and `connect_timeout` states keep the session screen open and retry with a 2/4/8/16/30-second backoff. The client waits for the previous native instance to reach terminal cleanup before starting another; it does not free a live instance to force a retry. Authentication, certificate, and other non-transient errors are not automatically retried. The user can stop retries. This is a source-level policy only; remote reconnect behavior needs testing.
- **Signing/build supply chain:** release signing no longer defaults to Android's debug keystore. Workflow write permission is isolated to a release-publishing job; build jobs are read-only. Direct GitHub Actions references are pinned to immutable commit SHAs, credential persistence is disabled on checkout, and debug/release artifacts have 7-day/3-day retention. The Gradle wrapper distribution SHA-256 is pinned.
- **Documentation, diagnostics, and splash:** README and build guide were rewritten to remove obsolete milestone claims and to distinguish local zoom, desktop DPI, and resolution. NLA/account names are no longer written to the connection log; README warns that host/certificate metadata may still need redaction. XyVerse wordmark is now at the bottom of the splash.
- **FreeRDP version:** source declares a `3.32.0` fallback version in `cmake/GetProjectVersion.cmake`; this verifies the source version, not a successful Android binary build. The checked GitHub advisory for `freerdp_certificate_data_hash_` affects versions through 3.19.1 and lists 3.20.0 as patched; the source version is later. This is not a comprehensive vulnerability audit: https://github.com/FreeRDP/FreeRDP/security/advisories/GHSA-h78c-5cjx-jw6x

## Workflow and historical release audit

- Prior GitHub API inventory found **19 stable releases** (three APKs each), **23 tags**, latest observed stable release `v0.5.8`, and some older tags without a matching release.
- No release or tag was deleted in this work. Release deletion remains pending explicit approval of scope. The previously approved exposed-artifact cleanup and signing-key rotation were already completed; changing the signing certificate can prevent older installs from receiving in-place updates.
- Workflow YAML parses and static assertions passed for tag-source guard, job permissions, artifact retention, immutable action references, and signing fallback checks. No hosted Actions run was triggered from this workspace.

## Verification results and blockers

- Ran `bash gradlew :core-rdp:testDebugUnitTest :app:compileDebugKotlin --no-daemon`; **Gradle stopped before running either task** because this environment has Java 11 and Gradle 9.6.1 requires Java 17+. `ANDROID_HOME` and `ANDROID_SDK_ROOT` are unset.
- A Python static check passed for YAML parsing, permission boundaries, artifact retention, SHA-pinned actions, IME and remote-DPI wiring, and removal of the debug-signing fallback.
- No Kotlin/Java compile, native C/C++ compile, unit test, APK build, IME test, RDP/DISP test, reconnect test, or visual/drag test has passed in this pass. This workspace has no `.git`, so `git diff --check` cannot be run here.

## Remaining high-priority validation

1. Run CI with JDK 21 and the configured Android SDK/NDK/CMake; fix compile/test failures before claiming the patch builds.
2. Test composing IME updates, commit, backspace (including surrogate-pair text), and IME close via Android Back on physical devices and more than one IME.
3. Test remote DPI choices on supported Windows RDP servers, including missing DISP channel/server rejection; verify the desktop DPI actually changes while local zoom and resolution remain independent.
4. Exercise Wi-Fi loss, server disconnect, timeout, failed re-authentication, user stop, and Activity lifecycle teardown; confirm retry waits for native cleanup and never creates overlapping native instances.
5. Verify HUD drag/reset/collision behavior for every control on portrait/landscape and multiple screen sizes; inspect the new splash positioning visually.
6. Continue static review of FreeRDP JNI bounds, workflow SDK download integrity, and credential/log paths. Do not delete old releases/tags without the user's confirmation.
