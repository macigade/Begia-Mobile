# BEGIA for Android

The phone build of BEGIA, the FAT data recorder whose desktop version lives
in `..\Desktop\IBA-CODE`. Two halves, deliberately:

| half | what | changes | lives |
|---|---|---|---|
| **shell** | the APK: Python runtime, binary wheels, WebView, foreground service, the payload manager | a few times a year | `shell/` (Gradle project, not started yet) and `runtime/` (its Python side) |
| **payload** | everything that is BEGIA: `app/`, `ui/`, `presets.json`, the S7comm-plus driver, a manifest | every release | built by `build_payload.bat` in the desktop repo; installed on the phone without reinstalling the APK |

The shell never holds a copy of `app/` or `ui/`. The payload is built from
the desktop working tree, so the phone and the laptop run the same source
with the same git build stamp, and every trial file names it.

## Layout

```
shell/                 the Gradle project: Kotlin activity (WebView + boot screen), the foreground
                       service in its own process, the installer and the JS bridge; build_apk.bat,
                       install_apk.bat. Chaquopy 16.1, Python 3.11, AGP 8.9 via the wrapper
runtime/begia_shell/   payload.py  verify a .begia, install it into a slot, roll a bad build back
                       boot.py     put a slot on sys.path, start the service, wait until it answers
                       android.py  what the shell calls: start, install, activate, info
                       devserver.py the loopback door for tools/push_payload.bat (debug builds)
tools/boot_test.py     boot a payload on the phone's Python stack and record a trial with it
tools/push_payload.bat push a payload to the plugged-in phone and restart the recorder (~10 s)
tools/logcat.bat       only what BEGIA logs on the phone
tools/phone_env.bat    the phone's Python stack (3.11 + requirements-phone.txt) as a laptop venv
requirements-phone.txt the pins the APK installs - and why they differ from the desktop's
design/s23/            the S23 screens (build.mjs generates the artboards; the canvas is the spec)
```

## Prove a payload before an APK exists

```
cd ..\Desktop\IBA-CODE
build_payload.bat                              -> dist\begia-payload.begia
cd ..\..\IBA_ANDROID-CODE
tools\phone_env.bat                            (once)
.venv-phone\Scripts\python tools\boot_test.py  -> PASS or the service log
```

The boot test starts the service from the `.begia` alone on Python 3.11 with
pydantic v1, serves the UI, connects to the built-in simulator, records a
trial and checks the trial file names the payload's build.

## Status

- 2026-09-09: design canvas for the S23 published. Payload builder written
  and tested (12 tests in the desktop repo). **Boot test passes**: BEGIA 0.9
  (v0.9-8-ge9f91e4) boots from the `.begia` alone on Python 3.11 + pydantic
  1.10.26 + FastAPI 0.125.0, serves the UI, records 39 samples off the
  simulator in 4 s, stamps the build into the trial file, and the S7comm-plus
  driver imports with the trimmed snap7. The slot manager's rollback rule has
  13 tests (`tests/`).
- 2026-09-09, later: **the shell builds and runs on the S23** (SM-S911B,
  Android 16). First APK: 31.8 MB, boots BEGIA 0.9 from the embedded payload
  in 1.4 s. **Hot update proven on the device**: `tools\push_payload.bat` put
  a newer build on the phone and the recorder came back on it 3 s later, no
  reinstall. **Rollback proven on the device**: a payload that verified but
  could not boot was abandoned and the previous build was back within 9 s,
  with the note on screen. Debugging the phone from this VM works over
  wireless debugging (`adb pair`, `adb mdns services`, `adb connect`);
  USB passthrough shows the phone to Windows but not to adb.
  Next: the phone layout mode in the desktop repo's `ui/`, then the
  screen-off recording and the 10 ms sustained-rate measurements.
  Handoff for the port: `..\Desktop\IBA-CODE\docs\ANDROID-PORT-HANDOFF.md`.
