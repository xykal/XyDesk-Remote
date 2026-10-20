# Portable text backup hardening — not full recovery

Source: 0f29e6d0f86fa0a5284245b107cafc505fea5494.

- Compose panel keyed by account: archive, passphrase, picker state and coroutine scope are recreated on account change. Cancellation must not publish old async results into the new panel.
- SAF reads bounded to MAX_FILE+1; zero-progress providers fail explicitly, oversized input rejected, scratch buffer wiped in finally including provider exceptions.
- Local archive read failures show an error rather than an uncaught UI coroutine exception; no deletion on error.
- Four new JVM input tests: exact bound/empty, unbounded oversized provider, zero progress/wipe, I/O failure/wipe.
- Existing XYCBK001 encryption format/KDF and login-based AAD unchanged. This is not immutable-provider-ID backup ownership or identity migration.

Local six original recovery guards and 154 Worker tests pass. Web run39435668629 SUCCESS. Android JVM/Kotlin run39435667699 SUCCESS (all core/app JVM tests and Kotlin compile); see /home/user/backup-hardening-state.json. No signed APK, physical-device/SAF/account-switch acceptance, production deployment or secret rotation.

Still only up to 80 confirmed ordinary text rows. No view-once/media/outbox export, upload, full history sync or complete protected portable backup. In-flight I/O can finish in its original account's storage despite coroutine cancellation; it must not be reassigned to another account. Physical picker-return/account-switch behavior remains to be tested.
