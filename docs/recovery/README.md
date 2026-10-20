# Owner-selected recovery baseline

Use the exact draft selected by the owner, not rc.3 or a guessed version number.
`OWNER-BASELINE.json` locks its release ID, artifact hashes and proven source.
GitHub publish-job logs for run 39123868039 contain that exact draft URL.

Recovery branch begins at source 9f64b474d02f2b3d6a13c65c7b77f15b045dfbca.
Existing RDP/PC Stream connect/loading/render/session behavior and media/feed UI
are the compatibility baseline. No APK-size/native stripping or broad lifecycle
refactor is to be mixed into functional recovery. Preserve original artifacts;
never overwrite this owner's draft/tag/assets.

All original requested additions/fixes remain required, not silently dropped:
owner chat edit/delete/tombstones and optimistic updates; Global Chat navigation
and separate notifications; complete Buat RDP provisioning; portable backup and
full durable outbox/history/media sync; all stick modes including genuine Windows
controller; ordinary RDP microphone; secure Direct BOTH endpoints. Do not restore
chat TTS as a substitute for real mic controls, disable certificate/authentication
checks, or enable insecure public raw UDP to make a connection appear fixed.
No paid upgrade/driver purchase without separate authorization.

Keep changes isolated and verify preserved workflows before treating them as
ready. Compilation/format/signature checks do not prove runtime compatibility.
The known live storage-quota outage is separate and must actually recover; an
old APK alone does not reset Cloudflare quota. No live backend rollback or data
reset was performed as part of selecting this baseline.

IMPORTANT: a branch descended from the old baseline must not simply be merged
into current main expecting it to remove newer regressions. A later recovery PR
must explicitly show the intended restoration/addition diff against current main,
preserve security fixes, and pass functional acceptance before release.
