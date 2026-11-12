---
name: wsa-control
description: Control the Windows Subsystem for Android (WSA) from the command line on Windows - launch it, connect adb, gain root via Magisk, install and drive Android apps, and diagnose the unauthorized/offline and oversized-screen problems. Use whenever Android work must happen on this machine and a physical phone is unavailable, or when WSA shows as unauthorized, offline, or refuses adb connections.
---

# WSA Control

WSA gives you a rooted Android device on Windows with no phone plugged in. Everything
below was verified on `MicrosoftCorporationII.WindowsSubsystemForAndroid 2407.40000.4.0`.

## 0. Facts to know before you start

- adb endpoint is **`127.0.0.1:58526`**.
- WSA runs Android **13 (SDK 33)** and is **already rooted through Magisk**.
- WSA has **no audio output**. You can verify playback via `dumpsys`
  (service state, notification, position) but you will never *hear* it. Do not
  claim audio works because a UI showed a play icon.
- Its default display is large (often **3840x2160**). Tap coordinates from a phone
  will miss. Always derive coordinates from a `uiautomator dump`, never guess.
- Android SDK on this machine: `C:\Users\Sarim\Android\sdk`; adb at
  `C:\Users\Sarim\Android\platform-tools\adb.exe`. `ANDROID_HOME`/`ANDROID_SDK_ROOT`
  are set; `JAVA_HOME` points at Android Studio's JBR.

## 1. Launch

```powershell
Start-Process explorer.exe 'shell:AppsFolder\MicrosoftCorporationII.WindowsSubsystemForAndroid_8wekyb3d8bbwe!App'
```

The Application Id is `App` (the client) - not `SettingsApp`. Building the AUMID from
`(Get-AppxPackageManifest).Package.Applications.Application.Id` concatenates both ids
and produces an invalid string; use the literal AUMID above.

Wait ~20-25 s after launch before expecting the adb port to accept connections.

## 2. Connect adb

```
adb kill-server
adb connect 127.0.0.1:58526
adb devices      # want: 127.0.0.1:58526   device
```

### `unauthorized`

This is the single most common failure. It means the Android-side key prompt was never accepted.

Cause: a stale or missing `%USERPROFILE%\.android\adbkey`. Deleting those files to
"force a handshake" makes it worse - the running WSA still holds the old key, so you
get unauthorized forever with no prompt.

Fix that actually works:

- Do **not** delete adbkey.
- Fully kill WSA: `Stop-Process -Name WsaClient,WsaService -Force`
- Relaunch (step 1) and wait ~25 s.
- `adb kill-server; adb connect 127.0.0.1:58526`

A fresh WSA start with an existing key authenticates silently. Restarting is what
restores the handshake; editing keys is not.

If it still fails, the Android auth dialog is on the WSA window and must be clicked.
Note the DSH window usually sits on top of it: enumerate windows with UI Automation,
minimise everything except WsaClient, then screenshot and look for an "Allow USB
debugging" dialog. UI Automation does not expose the Allow button (94 descendants,
none named), so click by coordinate from a screenshot.

### `offline`

Usually transient right after boot. `adb reconnect` then `adb connect` again; if it
persists, restart WSA as above.

## 3. Root

WSA is rooted, but Magisk may hold a prompt.

```
adb shell id                     # uid=2000(shell)
adb shell 'su -c id'             # uid=0(root) context=u:r:magisk:s0
```

If `su` triggers a Magisk "Superuser access" dialog, tap **Apps and ADB**, then
**Automatic response -> Grant**, so subsequent `su` calls do not block your script.
Find the buttons from a `uiautomator dump` - do not hardcode coordinates.

## 4. Install and drive an app

```
adb install -r app-debug.apk
adb shell am force-stop <pkg>
adb shell am start -n <pkg>/.MainActivity
adb shell pidof <pkg>            # empty => crashed on launch
adb shell logcat -d -b crash     # count 'Process: <pkg>' for real fatals
```

### Getting reliable UI coordinates

```
adb shell uiautomator dump /sdcard/ui.xml
adb pull /sdcard/ui.xml ui.xml
```

Parse `text="..." ... bounds="[x1,y1][x2,y2]"` and tap the centre. Never reuse phone
coordinates across devices.

`uiautomator dump` fails while a Dialog window is open - it writes nothing and the
pull silently leaves the old file in place. If a dump looks stale, delete the local
file first and check `Test-Path` after pulling. When it will not dump, fall back to
`adb shell screencap -p` plus reading the image.

## 5. Working with app-private files

Root is required; `/sdcard/Android/data/<pkg>` is often less accessible than the real path.

```
adb shell 'su -c "ls -l /data/data/<pkg>/databases/"'
adb exec-out su -c "cat /data/data/<pkg>/databases/app.db" > local.db
```

Critical: after replacing a database you must restore ownership to the app's uid/gid,
or the app silently sees an empty DB:

```
uid=$(adb shell 'su -c "stat -c %u /data/data/<pkg>"')
adb push local.db /sdcard/new.db
adb shell "su -c 'rm -f /data/data/<pkg>/databases/app.db-wal /data/data/<pkg>/databases/app.db-shm'"
adb shell "su -c 'cp /sdcard/new.db /data/data/<pkg>/databases/app.db'"
adb shell "su -c 'chown <uid>:<uid> /data/data/<pkg>/databases/app.db'"
adb shell "su -c 'chmod 660 /data/data/<pkg>/databases/app.db'"
```

Also remember Room keeps data in the WAL until checkpoint: pull `app.db` and
`app.db-wal`, or you will read stale rows and wrongly conclude the app lost data.
`sqlite3` is not installed on the device - copy the DB to the host and query it there.

## 6. Do not `adb uninstall` to "upgrade"

`adb uninstall` wipes app-private storage. Use `adb install -r`. Uninstalling to switch
build type (debug <-> release) destroys downloaded media and databases.

## 7. Checklist for a WSA session

- Relaunch WSA, wait, `adb connect`, confirm `device`.
- Confirm `su -c id` returns `uid=0`.
- `adb install -r`.
- Force-stop, start, `pidof`, then check the crash buffer.
- Derive all tap targets from a fresh `uiautomator dump`.
- Verify behaviour through `dumpsys activity services` / `dumpsys notification`, not
  just what the UI claims.
- If you wrote files as root, fix ownership and re-verify from the app side.
