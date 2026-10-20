# Android duplicate Global Chat media directories

Owner reported `Android/media/id.xydesk.remote/globalchat` and a similarly named
second folder. Source confirms the typo is **globwlchat** (not glowbalchat).
CustomStickerVault intentionally created BOTH folders and mirrored new/imported
stickers into both; even listing/deleting stickers created missing directories.

Changes:
- New writes use `globalchat/sticker` only. On external-storage failure, try the
  internal `filesDir/globalchat/sticker` fallback, stopping after one success.
- Legacy `globwlchat/sticker` remains read-compatible, including internal fallback
  files when external media becomes available again. Listing/deleting never creates
  directories. Identical filenames are deduplicated in the picker as before.
- Created stickers use AtomicFile and bounded readback verification; failed writes
  cannot return a fabricated successful external filename. Actual file paths are
  displayed rather than a hardcoded typo path.
- Explicit user sticker deletion still removes its copies from known locations;
  basename validation prevents path traversal. No automatic file/folder deletion,
  migration, storage wipe, crypto/key conversion or old-media cleanup is performed.

**Existing typo folders can therefore remain physically visible after update.**
This fix stops new mirroring/recreation; it does not pretend to have consolidated
or deleted owner files. Do not ask the owner to uninstall/clear storage/delete the
old folder without checking its contents. No APK has been installed on the owner's
phone by this source change, and genuine phone/SD-storage failure tests are pending.

Nine JVM policy tests cover canonical paths, read-only legacy compatibility,
side-effect-free listing, duplicate roots, basename safety, first-success termination,
fallback and all-failure reporting. Kotlin/app JVM gate passed at 52dcd09: run 39372922876.
Web Worker checks passed: run 39372923935. See STICKER-DIRECTORY-VALIDATION.json.
143 Worker tests and five protected-baseline guards still pass locally. No Connect,
render, native/build baseline or backend deployment change. Durable Android outbox,
full history/media sync and protected portable backup remain separate unfinished work.
The existing sticker crypto format/key is unchanged; this is not a claim of new
Keystore-protected media encryption.
