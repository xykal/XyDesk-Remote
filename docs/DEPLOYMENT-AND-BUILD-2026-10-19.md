# Deployment/build checkpoint — 2026-10-19

Owner explicitly requested build, validation, deployment and a draft, not public
publication. Existing main/tag ancestry guards are retained. No VERSION change.

## Verified source baseline

9b2de90 Android gate [39280241095](https://github.com/xykal/XyDesk-Remote/actions/runs/39280241095)
completed successfully after a long runner queue. Later changes in this checkpoint
need their own source gate. A local 640 MB heap/in-process/one-worker compile
attempt was stopped after memory pressure made the 2 GB sandbox unresponsive;
core-pc-stream compiled, full app compilation did not complete locally. No local
Android APK or signing-key extraction was attempted.

## Actual Windows build

A real optimized MinGW x64 GUI build found an unresolved external
`PKEY_Device_FriendlyName`. The readiness implementation now uses the same local
canonical Windows property key already used elsewhere in the host. Rebuild/link
succeeded. Static PE/import validation passed: only Windows OS imports, no
companion XyDesk/libstdc++/libwinpthread DLL. EXE is not Authenticode-verified or
Windows-runtime tested; secure Direct remains unimplemented/off.

The release workflow now requires the compile/test gate before native/signing
builds, validates the actual PE and includes HOST-VERIFICATION.json in the draft.
Release body explicitly lists incomplete features instead of claiming completion.

## Production attempt, rollback and diagnosis

- Recorded previous Worker version `3a356c75-57ac-4ed8-945b-fd107ffda7e2` for rollback.
- Worker 9b2de90 dry-run and 94 tests passed; deployment uploaded version
  `721aa643-f41c-4e3d-9580-37d4c1d3c771`.
- Smoke failed: history HTTP 500 and old mutation behavior from the DO path.
  Rolled back immediately via Cloudflare API; active version confirmed previous.
- History remained HTTP 500 after rollback. A filtered own-request tail captured
  `Exceeded allowed rows read in Durable Objects free tier.` No public chat
  bodies, request headers, bearer values or OAuth traffic were retained in logs.
- No paid upgrade or driver purchase was made. Re-deploying code cannot reset a
  consumed account-level quota.

## Follow-up source mitigation

- Coalesced five-second, revision-invalidated history snapshots; bounded 100-row
  reads; joined-at filtering is applied separately per caller after shared reads.
- Five-minute bounded per-identity joined-at cache (max 1000 identities).
- Edge quota exceptions return no-store structured 503 + Retry-After, not HTML
  exceptions or fabricated empty history. Android keeps local messages, displays
  a clear status and respects bounded retry cooldown.
- Edge authentication occurs before DO mutation access; version-capability checks
  refuse legacy live DO code rather than invoking its destructive delete contract.
- UI no longer labels shared-key transit protection as true E2EE.

Positive authenticated live mutation tests remain pending functional storage and
owner test accounts. A successful degraded-mode smoke is not full chat recovery.

Local follow-up checks: **102 Worker tests**, **43 bounded Kotlin/JVM subset
tests**, **14 distribution guards**, **6 synthetic APK/ELF fixtures**, and **3 PE
validation tests** pass. Workflow YAML parses; release remains draft-only and
requires compile-check. Full Android CI must verify the new cooldown/banner code.

## Production mitigation and official lineage outcome

- Mitigation source `1c3d033` deployed as Worker
  `c7d36303-fe28-48c8-a335-3ad95bcbb9e3` (100% active).
- Actual smoke: health 200; history 503 JSON
  `chat_storage_quota_exceeded`, Retry-After 300; unauthenticated POST edit/delete
  401 `authentication_required`; GET edit 405. This is verified degraded handling,
  not recovered storage or a positive authenticated mutation test.
- All six source CI checks at `1c3d033` passed. Owner explicitly approved PR #19
  merge after green CI. Merged main commit: `8db196b30573b6b2e6f36cbb35f5a620e184a0fa`.
- Existing tag `v1.1.23` points to old source and was not rewritten. Candidate
  tag `v1.1.23-rc.1` points to the merged main commit; APK version remains
  1.1.23/code 87.

## Real release packaging failure and correction

Signed release workflow 39293305385 passed its JVM/Compose compile gate and
`compileReleaseKotlin`, then built native code for all three ABIs. It failed at
`:freeRDPCore:mergeReleaseNativeLibs`: the three SHARED IMPORTED libraries
`libfreerdp3.so`, `libfreerdp-client3.so`, and `libwinpr3.so` were provided by both
jniLibs and AGP's CMake model. No signed release artifacts or draft were uploaded.

The correction strips the original imported inputs before copying, verifies all
three alias SHA-256 values, and removes only their redundant jniLibs aliases.
The originals remain for linking and AGP packaging; other codec libraries remain
in jniLibs. No broad or targeted Gradle pickFirst/excludes bypass is added.
Five CMake guard tests cover matching, differing and missing aliases, paths with
spaces, absence of collision bypass and preservation of unrelated codecs.
The failed rc.1 tag is retained; a corrected candidate must use a fresh tag after
its own green checks/main merge. Success of the new full native release build is
still required before claiming APK/draft availability.
