# Owner Windows test constraints

Owner confirmed: an owned remote Windows/RDP test target is available; no virtual
controller driver is installed; do not purchase a driver. No credentials were
requested/provided. Do not silently install drivers or accept third-party terms.

A true controller must be an actual system gamepad, not keyboard emulation. The
Microsoft InputInjector InitializeGamepadInjection API creates a virtual gamepad,
but its documented inputInjectionBrokered restricted capability requires a packaged
app context. API/type availability alone is not permission or runtime success for
our portable host EXE. Do not bypass capability enforcement, enable test-signing,
turn off Secure Boot, install a root certificate or change developer policy as a
shortcut. No such changes have been made. ViGEm is EOL, not an automatic installer.

References (research, not target validation):
- https://learn.microsoft.com/en-us/uwp/api/windows.ui.input.preview.injection.inputinjector.initializegamepadinjection
- https://learn.microsoft.com/en-us/uwp/api/windows.ui.input.preview.injection.inputinjector.trycreate
- https://github.com/microsoft/windows-rs/issues/2574

host-agent/tools/ReadOnly-Readiness.ps1 inventories only coarse OS/session, audio
service/redirection policy and existing controller-driver metadata. It does not
install anything, call gamepad initialization, access credentials, change Windows
settings, or send network traffic. It explicitly marks every runtime test untested.
A separate Windows CI job parses/runs it; that validates the diagnostic script on
the hosted Windows runner, NOT the owner's remote target or product functionality.
If execution policy blocks the script, do not weaken policy; report the error.

The owner target's readiness JSON is needed before selecting a supported no-purchase
controller path and conducting actual RDP/frame/microphone/controller acceptance.
The full requested scope remains required, including durable outbox/full history/
media sync and authenticated secure BOTH Direct. Nothing here implements those
features or treats a driver warning as their fulfillment.
