# BEGIA's release key, and moving the devices onto it

*Created 2026-10-07 at the owner's request ("Create it now"), and re-made the
same evening so its certificate names the vendor, MG Victus - before
anything was installed or licensed with it. Step one of
per-device licensing (IBA-CODE `docs/LICENSING-DESIGN.md`, phase 2). The
migration below is a plan: it runs only when the release app is final and
the owner says go.*

## The key

| | |
|---|---|
| Keystore | `C:\Users\user\.begia\begia-release.jks` (PKCS12) |
| Alias | `begia` |
| Key | RSA 4096, valid 2026-10-07 to 2056-09-29 |
| Certificate | `CN=BEGIA, O=MG Victus` |
| Certificate SHA-256 | `3B:D3:52:80:5D:96:5F:75:D9:A1:03:87:A9:19:09:CA:5B:FC:44:37:77:DC:16:BA:B5:22:D7:42:5A:05:97:DA` |
| Password | in `shell/local.properties` (git-ignored), `begia.release.storePassword` |

**Back it up, and never replace it.** Copy the `.jks` file *and* the password
line to wherever the owner keeps `payload-signing.key` and
`licence-signing.key` (same folder, same backup). A copy is good when
`keytool -list -keystore begia-release.jks -alias begia` accepts the
password and prints the SHA-256 above.

Why it can never change:

- **Licences.** A phone's device code is made from `ANDROID_ID`, which
  Android (8.0+) scopes to the app's signing key. A new key gives every
  device a new code, and every licence issued for the old one stops
  matching.
- **The watch.** The phone app and the watch app share one package name, and
  the Wearable Data Layer routes only between two APKs of one package signed
  with the same key. Both are signed with this one (`shell/signing.gradle`).
- **Updates.** Android installs an APK over another only with the same key;
  a different key means uninstalling first, which deletes the app's data.

## Building

```bash
cd shell
JAVA_HOME="$LOCALAPPDATA/Android/jdk-17" ANDROID_HOME="$LOCALAPPDATA/Android/Sdk" ./gradlew.bat :app:assembleRelease :wear:assembleRelease
```

The APKs land in `shell/app/build/outputs/apk/release/app-release.apk` and
`shell/wear/build/outputs/apk/release/wear-release.apk`. Check the signer:

```bash
"$LOCALAPPDATA/Android/Sdk/build-tools/34.0.0/apksigner.bat" verify --print-certs app-release.apk
```

It must print SHA-256 `3bd352805d965f75d9a10387a91909ca5bfc443777dc16bab522d7425a0597da`.
Verified on the release build of 2026-10-07: phone and watch both carry it.

A machine without the `begia.release.*` lines in `local.properties` builds an
unsigned release APK, as before; debug builds are never affected.

## What a release build changes in day-to-day work

A release APK is not debuggable. So, on a phone running it:

- **The dev server is off.** That is `PUT /payload` on 8081, which this
  session and the laptop session's relay used to push payloads, and the
  debug-only `PUT /licence`. Payloads arrive through the app: Setup → Advanced
  → *Install an update from a file*, or *Check the laptop for an update* with
  the pairing code.
- **No WebView devtools and no `run-as`.** The page can't be inspected, and
  the app's files can't be read from adb.
- **What still works**: `adb install` of a release APK over a release APK, and
  `adb forward tcp:18080 tcp:8080`. The forward is a socket on the phone, not
  debugging, so the recorder's API can still be reached from the laptop,
  e.g. `POST /api/signals/import`.

## Migration plan (not run yet)

`PKG` below is the app's applicationId, as `shell/app/build.gradle` sets it:
`PKG=$(grep -m1 applicationId shell/app/build.gradle | cut -d'"' -f2)`.

**Before starting:**

- The release APKs are final: the shell with licensing, phase 2,
  `SHELL_VERSION` 2.
- A signed payload carries `"licence": 2`.
- The Licence Manager can issue phone licences.
- The owner has said go.

**Order:**

1. The S23 with the Galaxy Watch 7. The watch must change together with its
   phone, since debug and release apps can't talk to each other.
2. The A17.
3. The S10e. Do it on a normal WiFi: while it runs the hotspot it can't do
   wireless debugging.
4. The Tab S10 Lite.

**For each phone or tablet**, with the old debug app still installed:

1. **Back up the data.**
   - `adb exec-out run-as $PKG tar cf - files/data > <device>-data-<date>.tar`
   - Keep the tar in a folder on this laptop, outside every repo. It holds
     `config.json`, which has the PLC passwords in clear text.
   - Copy the trial files (`files/data/trials/*.db`) into the laptop
     BEGIA's trials folder, so they stay reviewable there.
2. **Save the signal list.** Run `adb forward tcp:18080 tcp:8080`, then
   `curl http://127.0.0.1:18080/api/signals/export.csv > <device>-signals.csv`.
   Note the saved connections (Setup) by name and address; their passwords
   are in the tar.
3. **Uninstall** with `adb uninstall $PKG`. This deletes the
   app's data, so only do it after 1 and 2.
4. **Install the release app.**
   - `adb push app-release.apk /data/local/tmp/begia.apk` (in Git Bash,
     prefix `MSYS_NO_PATHCONV=1`).
   - `adb shell pm install /data/local/tmp/begia.apk`.
   - Then remove the copy from `/data/local/tmp`.
5. **Open BEGIA.** The embedded payload boots, and the licence door shows the
   device's **new** code.
6. **Licence it.** Issue a phone licence for that code in the Licence
   Manager, and scan its QR on the device (or pick the licence file).
7. **Restore the setup.**
   - Connections: through the welcome screen and Setup.
   - Signals: `curl -F file=@<device>-signals.csv http://127.0.0.1:18080/api/signals/import`
     through the forward, or Signals → import on the device.
8. **Check.**
   - The boot is OK, and a short trial records.
   - The I/O check sees the simulator.
   - Companion mode works with the licensed laptop, and is refused without
     a licence.
   - The S23: the watch shows the value and Mark works.

**The watch:**
- `adb -s <watch> uninstall $PKG`.
- Install `wear-release.apk` the same way (push, then `pm install`).
- Open it once on the wrist.

**Rollback**, if the release app misbehaves on a device:
- Uninstall it and install the last debug APK (built from `2662a6f`).
- `adb exec-in run-as $PKG tar xf - < <device>-data-<date>.tar`
  restores the data on the debug app.
- That device then needs no licence until the release app is fixed.

**Devices:**

| Device | Model | Note |
|---|---|---|
| Galaxy S23 | SM-S911B | Android 16 |
| Galaxy S10e | SM-G970F | Android 12 |
| Galaxy A17 5G | SM-A176B | Android 16 |
| Galaxy Tab S10 Lite | SM-X406B | Android 16 |
| Galaxy Watch 7 | | Wear OS 5; paired with the S23 |
