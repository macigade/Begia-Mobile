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

> **Read [docs/FROM-DESKTOP.md](docs/FROM-DESKTOP.md) at the start of a
> session.** Because `app/` and `ui/` are the desktop's, a change made over
> there lands here without anyone saying so - new endpoints, changed
> contracts, and bugs already fixed that would otherwise be rediscovered.
> That file is the running bulletin of those, newest first. It is a **mirror**
> of `docs/ANDROID-INFORM.md` in the desktop repo and is overwritten by
> `tools/sync_android_inform.py` there; edits made here are lost, so raise
> anything that belongs in it on the desktop side.

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
tools/rate_test.py     the 10 ms measurement: N signals at 100 Hz over the UDP channel into the
                       phone's SQLite for M minutes, then the trial file measured
tools/udp_load.py      the load itself, in the PLC's frame format (also for a laptop)
tools/trial_gaps.py    measure any trial file: samples, rate, every gap that should not be there
tools/screenoff_test.sh the screen-off recording test, with what the first run taught
tools/run_desktop_demo.bat the desktop BEGIA on its demo data directory, never the live config
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
- 2026-09-09, evening: **the phone layout** is in the desktop repo's `ui/`
  (bottom tabs, docked trial bar, list-as-page Trials, landscape single
  pane, tab icons) and verified on the S23 through the shell. **Both
  measurements taken** on the S23: a screen-off recording ran 6.9 min
  continuous, max gap 146 ms, no gap over 1 s, at the same rate as with
  the screen on; and 32 signals at 100 Hz over the UDP channel gave 19 496
  samples on every signal of 19 999 frames sent, no gap over 30 ms, about
  2 600 samples/s into SQLite, screen asleep for part of it (`tools/`).
  **The laptop update path** (`GET /api/payload` on the desktop, Check and
  Install in the phone's Setup) was exercised end to end on the phone
  against its own loopback, since this VM is not reachable from the phone.
- 2026-09-09, night: **touch on the charts** (drag pans, pinch zooms,
  tap reads out, hold follows, double-tap resets, hold-and-drag on an
  axis gutter offsets it) and **digital-signal lanes** (one named strip
  per signal at a fixed height, filled where TRUE; the analog charts
  share the rest of the page), both in the desktop repo's `ui/` and
  verified on the S23 (build v0.9-14-g5a73a26 on the phone).
- 2026-09-10: **design pass over the phone layout**, screen by screen, with
  `tools/phone_shots.py` in the desktop repo (headless Chrome driven over
  the DevTools protocol at exact phone sizes, with an audit of overflow and
  hit targets on every shot). On the S23 it found the live value pushed off
  the pane header, the analog chart starved to 43 px by two-row headers
  and a page rule that charged the time axis twice, a trial's stats cut at
  the edge, the Signals filter squeezed to 22 px, Setup's list holding a
  320 px hole, and the ruler's date line cut in half; all fixed in `ui/`.
  Then the same matrix at 320x520, 360x600, 375x627, 320x790 (fold cover),
  360x760, 393x812, 412x875, 430x892 and three landscapes: no overflow
  left; a page holds what fits at a readable plot, the brand mark yields at
  320 px, a wide phone on its side (915 px) keeps the drawer, safe-area
  insets are in place for the day the shell goes edge to edge.
- 2026-09-10, later: **Analyse at tablet width.** The phone decision is
  now the screen's smaller side (<= 600 px) rather than the current width,
  so a phone sideways stays a phone and a tablet gets the laptop layout
  with `data-device="tablet"`: thumb-sized targets, the drawer instead of
  the docked sidebar up to 13", a compact top bar upright. Analyse under
  a finger: A and B are buttons (press one, tap the chart), a finger on a
  marker line drags it, the palette is a strip across the top upright and
  beside the chart sideways, the A/B table keeps five columns. Checked at
  1280x800, 800x1280, 1024x768, 768x1024, 962x601 and 601x962 with touch
  emulation (`tools/phone_shots.py` in the desktop repo). Not yet tried
  on a real tablet: the shell APK is the same; a tablet just needs it
  installed.
- 2026-09-11: **one welcome, not two.** The shell now shows the payload's
  own `ui/boot.html` (the app's eye animation with a line of state under
  it) from the slot directory the moment it opens, while the recorder
  process starts, and loads the app with `?splash_start=` so the
  animation carries on there instead of replaying. The native boot
  screen remains for a payload without the page, a failed start and the
  rollback note. The page wears the app's last theme and text size
  (`BegiaShell.noteLook`). Needs the APK rebuilt (shell change) and the
  payload from desktop commit "The phone's boot page".
- 2026-09-11, later: **the watch companion** (`shell/wear/`, Wear OS 3+,
  built for a Galaxy Watch 7). A remote for the phone, not a recorder:
  one screen with the recording state and elapsed time, the marks so
  far, one analog signal's latest reading, a big Mark button (felt on
  the wrist), Start, and Stop behind a confirmation. It talks to the
  phone over the Wearable Data Layer (Bluetooth): the shell gained
  `WearRelayService`, which turns `/begia/state|mark|start|stop`
  messages into calls on the recorder's loopback API and answers with
  `GET /api/watch` (new in the desktop repo, `app/watch.py`). Same
  applicationId as the phone app on purpose - the Data Layer routes
  between the two apps of one package. Both APKs build
  (`gradlew :wear:assembleDebug :app:assembleDebug`); neither is on a
  device yet: the phone needs the new APK (the relay) and the watch
  needs `wear-debug.apk` over its own wireless debugging, paired the
  same way as the phone.
  Next: the second-screen mode, payload signing, a real tablet in hand.
  Known gap: sideways, the single pane is the first one; reaching another
  pane takes the pane menu (Restore, then Maximize on the other).
  Handoff for the port: `..\Desktop\IBA-CODE\docs\ANDROID-PORT-HANDOFF.md`.
