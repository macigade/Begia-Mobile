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
runtime/begia_shell/   payload.py  verify a .begia, install it into a slot, roll a bad build back
                       boot.py     put a slot on sys.path, start the service, wait until it answers
tools/boot_test.py     boot a payload on the phone's Python stack and record a trial with it
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
  13 tests (`tests/`). Next: the shell (Gradle project under `shell/`).
  Handoff for the port: `..\Desktop\IBA-CODE\docs\ANDROID-PORT-HANDOFF.md`.
