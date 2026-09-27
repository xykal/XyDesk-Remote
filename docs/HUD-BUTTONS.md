# Custom HUD buttons, keyboard toolbar, and local storage

XyDesk Remote keeps the phone keyboard for typing and gives you a set of
free-floating round buttons for everything else. This document describes the
model as built in v0.5.0.

## 1. One button = one action

Every button on screen is a single round button (`CircleShape`, diameter in dp).
Nothing is merged into a single widget: left click, right click, middle click,
scroll up, scroll down, and the input-mode switch are six separate buttons, each
with its own icon, position, size, and action.

| Property | Range | How to change |
|---|---|---|
| Position | normalised 0..1 of the session area | drag the button; or "Edit layout" mode |
| Size | 32..96 dp (diameter, radius = half) | long-press the button, or the button list in the right panel |
| Action | Single tap / Hold / Toggle | button editor |
| In toolbar | on / off | button editor |
| Key | any key from the catalogue (F1..F12, numpad, letters, symbols, arrows, modifiers) | add via "Add button" |
| Combo | Ctrl+C, Ctrl+Shift+Esc, Alt+Tab, Win+R, ... | catalogue group "Ready combos" |

Action semantics:

- **Single tap** – press and release (e.g. a plain letter, Enter, a combo).
- **Hold** – active for as long as you hold it (drag with the left button,
  continuous scroll).
- **Toggle** – first tap turns it on, second tap turns it off (e.g. Ctrl as a
  sticky modifier).

Buttons are stored per device (`xydesk.input` → `<deviceId>.hudkeys`), because
screen size and orientation differ between devices. Stored as JSON, no binary
assets.

## 2. Toolbar above the phone keyboard

The phone keyboard (system IME) is the primary typing surface. A toolbar is
pinned directly above it (offset by the live IME height, so it never overlaps
the keyboard):

- left side: buttons flagged "in toolbar" plus two icon chips (panel, pointer);
- far right: `123` → opens the full built-in board (QWERTY + F1..F12 + numpad);
- when the board is open the chip reads `ABC` → closes the board and brings the
  phone keyboard back;
- hiding the phone keyboard hides the toolbar and the board with it – the
  toolbar only exists while the IME is up.

Implementation notes: IME height comes from `WindowInsetsCompat.Type.ime()` in
`SessionSurfaceController.installInsetsHandling`, forwarded to Compose via
`SessionSurfaceController.onImeChanged`. No polling, no guessing.

## 3. Local storage redirection (phone storage → remote drive)

The `drive` option maps to `/drive:sdcard,<path>`. Since Android 11 an app can
no longer read `/storage/emulated/0` freely, so the path is chosen at runtime by
`LibFreeRDP.appDrivePath(Context)`:

1. if the app holds "All files access" (`MANAGE_EXTERNAL_STORAGE`,
   `Environment.isExternalStorageManager()`) → the whole external storage;
2. otherwise → the app's own external directory (`Android/data/<pkg>/files/Share`),
   which always works without any grant.

The app manifest declares the permission (via the `freeRDPCore` manifest merge)
and the device editor shows the current state plus a button that opens the
system "All files access" page for this app. The drive works either way; full
access only widens what is visible.

## 4. Diagnostics without ADB

`XyApp` sets winpr's logging environment before the native library is touched:

```
WLOG_APPENDER=file
WLOG_LEVEL=INFO
WLOG_FILEAPPENDER_OUTPUT_FILE_PATH=<app files dir>
WLOG_FILEAPPENDER_OUTPUT_FILE_NAME=freerdp-native.log
```

The app log dialog ("Session log" in General → Connection problems) prints the
native tail first, then the app log. That is the file to send when audio,
microphone, clipboard, or the drive misbehave — it contains the channel load
lines from the native side.

## 5. Files

| File | Role |
|---|---|
| `ui/HudKey.kt` | button model, JSON (de)serialisation, defaults, catalogue |
| `ui/SessionKeyLayer.kt` | free layer, drag/long-press, toolbar, picker, editor |
| `ui/SessionControls.kt` | session chrome: panels (right: input/pointer/buttons/keyboard, left: screen/session) |
| `ui/SessionPrefs.kt` | per-device storage of buttons, pointer, keyboard scale |
| `ui/Lang.kt` | ID/EN text table and language preference |
| `ui/Wallpaper.kt` | `DevicePreviewArt` – procedural device preview (not an RDP wallpaper) |
| `SessionSurfaceController.kt` | IME  callback → toolbar placement |
| `LibFreeRDP.java` | `appDrivePath`, `hasAllFilesAccess` |
