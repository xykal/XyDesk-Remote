# Landing optimization and owner-requested signed draft candidate

Source prepared for PR22: 6261d9213507017c345f7dc9311dd6894a1208db.
Main lineage5990 is explicitly reconciled using a restoration merge while preserving
the tested recovery Android tree / exact9f64 connection/native guards. No rejected
rc3 lifecycle/render tree was silently imported. Safe main host/readiness/static
builder and Windows syntax validation were ported separately before packaging.

## Production landing

Final Worker40054b53-f29d-40a9-8f7a-5df684af2605. Original main5990 config and
all other assets retained; only web/index.html uploaded as changed each deployment.
No namespace reset/new binding/migration, code/token login, chat write or paid upgrade.
Original production c532 -> e76d9d (initial) -> a8466a (anchor correction) ->40054b53
(long path wrap). These are intentional recorded follow-ups, not one deployment.

- Removed expired countdown markup/CSS/timer: browser observed8 running animations
  before,0 after, despite old popup being visually hidden.
- Landing no longer queries chat presence. Single-flight active-session fetch with
  8-second abort, 60-second visible scheduling, hidden-tab pause/abort/resume.
- No fabricated minimum1 online; unavailable statistics show an error/—.
- Mobile backdrop filters disabled; explicit logo dimensions; hidden ABI badges
  really stay hidden (old CSS display override showed every badge as recommended).
- Long code paths wrap without horizontal page overflow at390x844.
- Experimental content-visibility section skipping was rejected and removed after
  browser test proved it mis-targeted smooth #download anchor. Final live click
  reached target top84.47px below sticky header;4 APK links preserved.
- 160 Worker/landing tests (six new landing cases),6 original baseline guards,
  5 candidate safety guards,3 PE-validator tests locally pass.

Live landing and health200; canonical OAuth start302 to github.com with API callback
unchanged. Actual owner login untested. /api/active-sessions returned503 both before
and after: existing statistics backend unavailability is NOT fixed by visual work.
No false online count, no paid upgrade. Browser checks are not physical-phone FPS
measurements and no speedup percentage is claimed.

## Candidate packaging and safety

PR22 must pass final source JVM/Kotlin, static host PE/import validation, Worker and
MSVC checks before main merge/tag. Candidate v1.1.23-recovery.1 is DRAFT/prerelease,
never public/latest. Original draft416426283 and blocked rc3 are not overwritten.
Android package version1.1.23/build87 remains locked, not a new version increment.

Host is one static XyDeskRemoteHost.exe, plus a ZIP containing only that EXE; no
Standalone/DLL/setup-script distribution. Safe main host defaults legacy UDP OFF
with explicit private-test flag only, no fake TLS/E2EE or Windows password setup.
Android raw Direct/input/audio bridge starts and discovery blocked with no release
override; blocked mic cannot report enabled. This does NOT implement secure Direct
and does NOT complete ordinary native RDP mic/device acceptance.

Publish depends on compile gate + release build; verifies pinned official APK signer,
V1/V2, ZIP/libcrypto and checksums. Includes HOST-VERIFICATION.json. Removed old global
Actions artifact/cache deletion step so unrelated evidence/caches are not destroyed.

Final build/run/draft status: /home/user/landing-release-state.json (live state), then
LANDING-AND-SIGNED-DRAFT-VALIDATION.json when finalized. Pending is not signed output.
Candidate limits remain in RECOVERY-CANDIDATE-NOTES.md. No physical/runtime acceptance,
full history/media/portable outbox recovery, provisioning, genuine controller, secure
BOTH Direct transport or historical secret rotation claim.

## Confirmed progress

PR22 merged to main6753116aa0440edb6cc7b82333c2e49372136d36 after exact-source6261d92 gates green.
Android gate39439319430 SUCCESS (JVM/Kotlin + static host compile/PE validation).
MSVC and Worker160 checks SUCCESS. Candidate tag points to same merge commit on main.
Draft418497730 created (draft/prerelease, build pending); official signed release run
39439824900 in progress. Keystore restore already succeeded; signing/verifications/assets
not yet complete at this documentation checkpoint. No debug build was run.

Draft: https://github.com/xykal/XyDesk-Remote/releases/tag/untagged-cefde124a74061f5a29f
Build: https://github.com/xykal/XyDesk-Remote/actions/runs/39439824900
