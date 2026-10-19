# Genuine Windows controller dependency assessment — 2026-10-19

Keyboard direction presets and UI sticks are not a Windows controller device.
Do not enable legacy UDP, disable driver-signature enforcement or silently install
retired drivers to make this feature appear complete.

Official upstream ViGEm documentation says the driver/client libraries are retired
and no longer receive updates. Existing updater components require special care:
[14](https://docs.nefarius.at/projects/ViGEm/End-of-Life/).

LizardByte's current announcement identifies libvirtualhid as MIT-licensed and its
Windows Virtual HID Driver as maintained, separately installed and licensed for
paid Windows gamepad creation (with online license checking). These are distinct
components, not a free bundled kernel driver:
[5](https://app.lizardbyte.dev/2026-08-16-introducing-libvirtualhid-and-virtual-hid-driver/?lng=en-US).

No driver was downloaded/installed, license purchased, subscription started,
license terms accepted, contributor application submitted or signing policy changed.
The owner has not authorized a paid purchase. Implementation must distinguish
available/installed/licensed device capability from a packet encoder/UI toggle.
A future optional adapter can target an owner-installed supported driver only
behind authenticated transport, explicit input grant and neutralization on revoke,
disconnect, timeout and session replacement. Rumble/profile/Server compatibility
must be tested rather than inferred from controller-shaped UI.

Secure Direct transport, supported driver integration and Windows/game execution
remain unfinished. This assessment is not an implementation or a release gate pass.
