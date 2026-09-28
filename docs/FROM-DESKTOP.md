# For the Android session — what changed on the desktop side

A running bulletin, newest entry first. The Android/phone session reads this
to find out what moved under it: fixes, new endpoints, changed contracts.

**Read [ANDROID-PORT-HANDOFF.md](ANDROID-PORT-HANDOFF.md) first** — that is the
standing brief (what BEGIA is, the A-or-B decision, what the two legacy
attempts already paid for). This file is only the delta since it was written.

**This file is mirrored** into the phone repo (`Begia-Mobile`) at
`docs/FROM-DESKTOP.md`, byte for byte, by `tools/sync_android_inform.py`. **The
copy here is the source of truth** — edit this one and run the script; a change
made on the mirror is overwritten by the next sync. `--check` tells you whether
the mirror is stale without touching it.

**How this is kept:** whenever the desktop program changes in a way the phone
can see — an endpoint, a payload, a shared file under `ui/` or `app/`, or a
bug that would reproduce on the phone — an entry goes here in the same commit
as the change. Entries say what to *do*, not just what happened.

---

## 2026-09-28 — a saved theme the build does not offer opens as Victus Blue (shared `ui/`; replaces `7fe9c67`'s fallback)

`7fe9c67` kept a saved house-palette choice by matching the palette's old
id - and that id is the old company's name, which the product no longer
writes anywhere (the entry below). The rule is generic now and does the
same thing: in `app.js`, a saved theme id not in `THEMES` opens as
`victus`; `boot.html` carries the same list and the same rule for the id
the shell hands it. No theme id other than the house palette's has ever
been dropped, so that is the only id the rule meets.
`tests/test_theme_ids.js` keeps the two lists equal and runs both.

Please keep the old company name out of code, comments, tests and commit
messages; `7fe9c67`'s message names it.

---

## 2026-09-28 — the vendor is MG Victus, and no other company is named (ACTION for the shell)

The product names **MG Victus** as its vendor and licensor, and no other
company - not in code, strings, ids, docs, tests or commit messages. The
desktop side is done: the licence door says to send the machine code to
MG Victus, the house theme is **Victus Blue**, the report footer reads
`BEGIA · MG Victus`, new HTTPS certificates carry `O=MG Victus`, and the
licence on the build laptop is issued to MG Victus.

What moved in shared files, so you can follow it:

- **The house theme's id is `victus`** (`THEMES`, `THEME_META`,
  `THEME_DEFS`, `:root[data-theme="victus"]`, the colour-blind-safe list).
  A WebView that saved the old id falls back to the default theme once;
  if your boot page (`noteLook`) keeps a theme id of its own, it gets
  `victus` from the page from now on.
- The brand file is `ui/brand/colour/wave-victus.svg` (renamed; its
  internal ids too). Nothing in `ui/` links it.
- The simulator's OPC UA namespace URI is `urn:begia:sim:slagdoor`. Its
  index is unchanged (`ns=2`), so saved simulator signals still resolve.
- `tools/payload_keys.py` labels a new key `mg-victus` by default.

**ACTION - the shell still carries the old company name:**

1. Your `applicationId`, `namespace` and Kotlin package use it as the
   segment after `com.`. Rename that segment to `mgvictus`
   (`com.mgvictus.begia`), moving the source folders to match. **A new
   applicationId installs as a new app**: the old one must be uninstalled,
   and its installed payload slots, policy and paired watch do not carry
   over - the Wear app's applicationId has to change in the same release
   or the Data Layer link breaks.
2. `runtime/begia_shell/trust.py` labels key `41f1f2855a0c4667` with the
   old name followed by `-dev`; make it `mg-victus-dev`. The key and its id
   do not change, so signed payloads still verify.
3. `boot-test-data/slots/` holds a payload unpacked from an older build and
   carries the old name in its `ui/`; regenerate it from a current payload
   or delete it.
4. `docs/FROM-DESKTOP.md` is fixed by this mirror.

The old plant password contained the name too; it was quoted in two places
in this file and is not any more. It still has to be rotated on the PLC.

---

## 2026-09-28 — Options: an Access card, a checkbox that is a box (shared `ui/`; nothing to do)

The sign-in, licence and phone-sharing rows left the Display card for a card
of their own, **Access** (`#ov-access`, `ov-desktop`, so not on a phone),
each row a sentence over its control (`.ov-row.ov-top` + `.ov-stack`,
`.ov-says` for the sentence in the interface face). The welcome animation's
note is back under the welcome animation. Every id is unchanged
(`auth-row`, `auth-says`, `auth-new`, `btn-auth-set`, `btn-auth-out`,
`lic-row`, `lic-says`, `btn-lic-door`, `btn-share-payload`,
`share-payload-says`), so `paintAuth()`, `paintLicence()` and the share
button's `paint()` are untouched.

One rule to know about: `.ov-chk input[type="checkbox"] { width: auto;
height: auto; min-height: 0 }`. The global `input { width: 100% }` reached
the Options checkboxes and made each one fill its value column, with its
label text pushed outside the card one word a line; if your phone Options
view has a checkbox in an `.ov-chk`, it is fixed there too. The Options grid
is `max-width: 1800px` now (five cards in one row at 1920), and the This
machine card wraps with `overflow-wrap: anywhere` instead of `break-all`.

---

## 2026-09-28 — a DAT link beside CSV: an iba .dat through iba's library (nothing to do; the link is muted on the phone)

Every trial row has a **DAT** link now (`ui/app.js` `datLink()`), for an
iba `.dat` file ibaAnalyzer opens: `GET /api/trials/{file}/export.dat?grid_ms=`.
The file is written by iba's own ibaFiles COM library (`app/ibadat.py`),
because the format is iba's own; where the library is not installed the
route answers 501 with what to install, and the page asks
`GET /api/export/formats` once at boot and shows the link **greyed with that
reason on its tooltip**.

On the phone `available()` says *"iba .dat export runs on Windows, through
iba's ibaFiles library"*, so the link is muted there and that is the tooltip.
`app/ibadat.py` imports pywin32 only inside `available()` / the writer, under
a try, so the payload's bytecode runs on the phone as before. Nothing to do
unless you want the DAT word gone from the phone's rows - `.tlink.muted` is
the class, `ov-desktop`-style hiding would be yours.

---

## 2026-09-28 — the API asks a stranger to sign in (loopback never; nothing for the phone to do, two things to know)

`app/main.py` now refuses any `/api/*` request from anywhere but the
machine it runs on unless it carries a session cookie, and the socket does
the same: one `{"type": "auth"}` frame, then close 4401. The password is set
on the laptop (Options, *App password*; `PUT /api/auth/password`, loopback
only, no old password asked), signed in
with `POST /api/login` (`{username, password, remember}` → cookie
`begia_session`, HttpOnly, SameSite=Lax, Secure over HTTPS), and asked about
with `GET /api/auth` (`{enabled, required, user, username}`). Until a
password is set nothing from the network gets in. `app/auth.py`, README
*Signing in from another machine*.

**The phone is never asked**: its page talks to the phone's own server on
loopback, and `required` is false there - no door, and the `#auth-row` in
Options is `ov-desktop`. The phone's relay reads `/api/watch` on loopback:
exempt.

Two things to know:

- **If the shell ever calls the laptop's API over the network** for anything
  but `/api/payload` and `/api/payload/info` (both open, with the pairing
  code), it would get 401 and need a session. Today it does not.
- **A laptop that upgrades to this build has no app password yet**, so an
  HMI panel or a second laptop that used to open the page over HTTPS now
  gets the sign-in door saying to set one on the laptop. That is the
  feature; say so if anyone asks why the panel stopped.

Shared `ui/` additions, all inert on loopback: `#auth-overlay`/`#auth-door`
(next to the licence door), `authDoor()` in `app.js`, and the row + note in
Options.

---

## 2026-09-28 — the packaged exe runs under a machine-bound licence (nothing for the phone to do, one thing to know)

`BEGIA.exe` now runs for the machine its `licence.json` names: an Ed25519
signature by the vendor's key over `{product, customer, machine, issued,
expires}`, checked against this machine's code (base32 of the Windows
MachineGuid, `XXXX-XXXX-XXXX-XXXX-XXXX`). Without it the exe serves the page
and refuses the API with 402; the page shows a licence door with the code to
send and takes the file. `app/licence.py`, `tools/licence.py`, README *The
licence*.

**The phone is not asked.** `licence.required()` is `sys.frozen` - the
packaged exe - so the same `app/main.py` running under your shell answers
`required: false` and gates nothing. Deliberately: a phone gets its program
only from a licensed laptop, with the pairing code, and a phone-side licence
would be the shell's to check against an Android id, not this module's. If a
site ever wants that, the public key and the verify function are in
`app/licence.py` (`verify(doc, machine)`), and `tools/licence.py issue` can
sign a licence for any code you hand it.

Things that are now in the shared files and harmless on the phone:

- `GET /api/licence` answers `{required: false, ok: true, ...}` on the phone;
  `status.licence` carries the same; a `{"type": "licence"}` socket frame is
  only ever sent by an unlicensed exe. The page's `licenceDoor()` hides the
  Options row and the door when `required` is false.
- `#lic-overlay` / `#lic-door` are in `index.html` outside `#splash`
  (`.lic-overlay`, z-index 1100), hidden. `#lic-row` in Options is
  `ov-desktop` and hidden until an exe says it is required.
- `BEGIA_LICENCE_REQUIRED=1` makes a source run ask too - do not set it on
  the phone unless you want the door.

---

## 2026-09-28 — the payload fetch needs a pairing code; the payload is bytecode, not `.py` (ACTION for the shell)

Two changes to what a phone gets from a laptop. The first needs a change in
`Installer.kt`; the second needs none.

**1. `GET /api/payload` needs the six-digit code the laptop shows.** The
sharing window (Options, *Share the payload for 15 min*) is still there, but
a window is what anyone in WiFi range can see, so the fetch now also presents
the pairing code the laptop shows beside the button while it is open - which
only someone at the laptop can read.

- Send it as the header `X-Begia-Pair: 123456` or as `?code=123456` on the
  URL (a plain download link). Spaces are ignored, so "123 456" typed as
  shown is fine.
- Without it: 403, detail *"the laptop is sharing, but the fetch needs the
  pairing code shown on its Options page (X-Begia-Pair header, or ?code=)"*.
  Wrong: 403 *"wrong pairing code - six digits, on the laptop's Options
  page"*. **Five wrong codes shut the window** (403 *"... sharing is closed;
  open it again on the laptop"*), so do not retry a code the operator did not
  retype.
- `GET /api/payload/info` now carries `pairing: true`. It never carries the
  code over the network. (To the laptop's own page it does, as `code`, while
  the window is open - that is how the page shows it.)
- `POST`/`DELETE /api/payload/share` answer 403 from anywhere but the
  laptop itself. The phone's own share switch (your commit `10590b0`) still
  works: on the phone the page talks to the phone's own server on loopback.
- From loopback the fetch needs nothing, as before: your relay and the boot
  test are unaffected.

What the Setup screen should do: after `info` says `shared`, ask for the
code ("the six digits on the laptop's Options page"), fetch with it, and show
the server's `detail` on a 403 - the five-tries rule means the operator has
to walk back to the laptop, and the words say so.

**2. The payload is bytecode.** Every `.py` under `app/` and `vendor/` is a
sourceless `.pyc` compiled by CPython 3.11 - your Chaquopy runtime - named
where the `.py` was (`app/main.pyc`, `vendor/snap7/__init__.pyc`). The
manifest says so: `"python": "3.11"`, `"sources": false`, `"bytecode":
{"cpython": "3.11", "magic": "a70d0d0a"}`. Nothing in `begia_shell` needs
to change: `payload.py` verifies hashes and extracts, `boot.py` imports
`app.main:app`, and a sourceless `.pyc` beside where the `.py` would be is
what the import system loads (tested against 3.11 in
`tests/test_payload.py`). Two things to know:

- **The payload loads on 3.11 only.** If the shell ever moves to another
  Python, `tools/make_payload.py` must compile for it (`BYTECODE_PY`), or
  every import fails at boot with a bad-magic error. A payload built with
  `--source` (development) still carries `.py` and runs on any 3.11+.
- Tracebacks on the phone still say `app/main.py` (the compile names the
  file), but there is no source to show beside them.

The laptop needs a Python 3.11 at hand to build the payload (the exe build
runs it): `.venv\Scripts\pip install uv` and `.venv\Scripts\uv python
install 3.11`, once per build machine. README, *Packaging the phone payload*.

---

## 2026-09-25 — shared `ui/`: the panels hold at every text size; a bool in a mixed pane takes an axis slot

At 130-160 % text the laptop's sidebar kept its 320 pixels and everything in
it came apart: the trigger sentence broke mid-clause, the connection card
wrapped its address one word a line, the top bar wrapped "30" over "s" and
cut "Start simulator" mid-word, and the charts kept 100 % gutters, tick pitch
and ruler height under labels drawn 60 % larger. All in shared `ui/`, so all
yours with the next payload.

- **Panel widths are stored at 100 % and drawn at the text size.** `#sidebar`
  is `calc(320px * var(--fs-scale))` with a scaled floor and ceiling, and
  `makeResizable({scaled: true})` does the same for a dragged width (the
  sidebar and the analysis palette). A `sidebar_w` / `palette_w` your WebView
  has in localStorage now means "at 100 %" - it draws wider at a larger
  text size, which is the point. **The drawer is exempt**: inside the
  drawer media block the sidebar has `min-width: 0 !important; max-width:
  none !important`, so a phone's drawer is still `min(86vw, 340px)`.
- **Chart pixels that hold text go through `chartPx()`**: the axis gutters
  (56 live / 52 analysis), the per-pane time axis (`paneXAxisH()`, was the
  bare `PANE_X_AXIS_H`), lane heights (`laneHeight`), the ruler (`rulerH()`)
  and the tick pitch (`space`). At 100 % every number is what it was. The
  phone page-fit (`phonePageCount`) uses `paneXAxisH()`, so a page holds the
  same panes at 100 % and fewer at 160 % - do not pin those counts in a test
  without setting `uiScale`.
- **The top bar's narrow mode is a container query, in em.** Short window
  chips, the state label hidden, the simulator button hidden: this used to be
  `@media (max-width: 1500px)` and is `@container topbar (max-width: 107em)`
  now (`#topbar` carries `container-type: inline-size` and the body's
  font-size, so nothing inside it changes). 107em is the old 1500px at 14px;
  at 160 % it fires at 2400px. Needs container queries - Chrome/WebView 105+
  - which your shell has; on anything older the bar simply never enters
  narrow mode.
- **`monPlan()`: a bool among analog traces takes an axis slot.** It is drawn
  as a 0..1 trace with its own axis column, so it counts toward five a side
  and ten a pane; a pane of nothing but bools is a strip of lanes and plans
  no axis. Skipping every bool let a pane of feedback bits draw eleven axes on
  the left. The pane header's L/R and the sidebar's follow the same plan.
- **Trigger sentence:** `.trig-bit` spans keep a value with its unit and
  punctuation (`[3] s`, `[0.5],`, `Stop [after]`, `[trial_{n}].`) so the
  sentence breaks only between clauses; the name field has `max-width:
  calc(100% - 1.4em)` so its full stop stays beside it. Every `trig-*` id is
  unchanged.
- **`tools/phone_shots.py`** takes `"touch": false` (a desktop window: a fine
  pointer, no mobile viewport) and `"probe": "<js expression>"` (its value
  goes into `audit.json` and the console). Use `touch: false` to see the
  laptop's sidebar at all - with touch emulation on, `(pointer: coarse)`
  under 1366px is the drawer layout at every width.

Tests: `tests/test_scale_panels.js` (the width maths, the wiring, the bits),
`tests/test_axis_plan.js` (the bool rule), and the `test_pane_page.js` /
`test_phone_layout.js` contexts now carry `uiScale` and `chartPx`.

---

## 2026-09-25 — shared `ui/`: two typefaces, an Options grid that holds, signal sets on the Signals page, a shorter warning

Four changes in the shared `ui/` folder; the phone runs the same files, so
they are yours the moment you take a payload from a `v0.9-53` or later exe.

- **The typeface picker offers two faces, not fourteen.** Interface: Franklin
  Gothic (Windows) falling to IBM Plex Sans, which ships in `ui/`. Data and
  addresses: DejaVu Sans Mono falling to JetBrains Mono, also shipped. On a
  phone neither first choice is installed, so you get the shipped fallbacks -
  that is intended, nothing to add to the shell. A `ui_font` / `data_font`
  saved in the WebView's localStorage under an old id (`plexsans`,
  `plexmono`, ...) falls back to the one offered; no migration needed.
- **The Options grid cannot be grown by its content any more.** `.ov-row`'s
  value track is `minmax(0, 1fr)` and a select or input fills it and no more
  (`ui/style.css`). At 130 % text the Layout select used to push its track
  past the card and lose its arrow; re-render Options at 145-160 % on a 360 px
  phone once, and if anything still sticks out it is a phone-only rule of
  yours, not this file.
- **The signal-set controls live on the Signals page** (`#signals-view`, the
  `.sig-sets` row under the toolbar): the dropdown, Load, delete, the name
  field and Save. They were a sidebar card shown only in the Trials module,
  and the Signals module hides the sidebar - so from the one place the list
  is managed there was no way to save or load it. The card (`<h2>Signal
  sets`) is gone; the element ids (`set-sel`, `btn-set-load`, `btn-set-del`,
  `set-name`, `btn-set-save`) are unchanged, so a phone rule that targets
  them still applies. If your layout hides `#signals-view`, the sets are now
  hidden with it.
- **The standing warning names six signals and counts the rest.** `#warnbar`
  used to list every missing or unacquired signal; 291 of them filled a
  laptop screen at 160 % and would fill a phone at any size. It now says six
  names and "… and 285 more (hover for all)", with the full roll call on the
  bar's `title`. A phone has no hover, so the full list is unreachable there
  by design; the *remove N from this PLC's list* button beside it is the
  action, and it asks before it acts. If you want the names on the phone,
  read `document.getElementById("warnbar").title` on tap - do not widen the
  bar.

Tests: `tests/test_faces.js`, `tests/test_signal_sets_place.js`,
`tests/test_banner_names.js` (Node, no server).

---

## 2026-09-25 — `GET /api/payload` is refused unless the laptop is sharing (403)

The payload is the full source, readable, and it was one GET away for anyone
in WiFi range at any time. Now the operator opens a window on the laptop
(Options → *Share the payload with phones*, 15 minutes by default,
`POST /api/payload/share {"minutes": n}`, `DELETE` closes it) and only then does
`GET /api/payload` answer from the network; from loopback it always answers,
so the boot test and your relay are unaffected.

**What your shell should do:** `GET /api/payload/info` stays open and now
carries `shared` (bool) and `shared_until` (ISO time or null). If `shared` is
false, say so on the Setup screen *before* the operator taps Install -
"the laptop is not sharing its payload; on the laptop, Options → Share the
payload with phones" - rather than letting the fetch fail. The 403's `detail`
says the same words if you prefer to show the server's.

---

## 2026-09-25 — the handout ships no source, and takes your APK

`make_handout.ps1` is the customer deliverable now: `dist\BEGIA.exe`, the
APK, an example `config.json`, a `README.txt` - and it refuses to seal with
anything but an exe, an apk, a json or a txt inside. It used to carry `app\`
and `ui\` in full. `-WithSources` is the old project handover, for a
colleague only.

It picks up the newest APK from **your** repo's
`shell/app/build/outputs/apk/release/*.apk`, then `.../debug/*.apk`, then the
legacy `android-app\` here. A release build, when you make one, is preferred
over a debug one by that order, not by name.

---

## 2026-09-24 — no readings while the link is down; Reconnect and the simulator; five axes a side

Three things from the first hour of the door on the exe, all in shared code:

- **Readings are dashes when not connected** (`ui/app.js`, `readingText`).
  The store keeps the last minute, and a value two minutes old sat on the tile
  and in the pane header as if it were live; a bool read FALSE. Now: not
  connected, or no sample ARRIVED in the last 3 s (5× the rate if slower) →
  `—`, never a number, never 0. Freshness is by arrival time (`e.at`), not the
  sample's own stamp, which on OPC UA is the PLC's clock. `body.link-down`,
  `#linkbar` ("No live data since 17:43:35: reconnecting to … — the PLC did not
  answer in time"), and the LIVE chips read NO DATA. `repaintReadings()` runs
  every second and on every status, because the redraw loop only runs when
  frames arrive - exactly when nothing needs to change.
- **`POST /api/connect` with the simulator's own address dials it
  anonymously and writes nothing**; before, Reconnect posted whatever the
  driver was on, so with the simulator up the plant's login went to
  `localhost:4855` (`BadIdentityTokenRejected`), the address went into
  `cfg.endpoint` and the park the simulator restores, and the named connection
  was dropped. It also keeps `active_connection` when the address is the
  active connection's. The hidden endpoint the Reconnect button posts is
  `status.plc_endpoint` now.
- **At most five Y axes a side, ten a pane** (`axisPlan`, `monSideOf`,
  `monAxisShown`). A trace whose side is full is drawn on the other side while
  that has room; past ten its axis is not drawn (the trace still plots to its
  own range and the header carries its value). The pane header and the sidebar
  L/R show the side actually drawn.

---

## 2026-09-24 — the welcome asks for the PLC and the login (boot behaviour change)

After the eye has opened, the welcome screen now stays and asks for the PLC
address, the user and the password, pre-filled from the saved config, before
the tool connects. Three things you need to know:

- **The server no longer dials the saved plant on its own at boot** while this
  is on (`AppConfig.startup_gate`, default `true`). The simulator still comes
  back if it was on. If the phone acquires and you relied on the boot
  auto-connect, either turn the switch off (`PUT /api/startup {"ask": false}`,
  also in Options → "Ask at start-up") or connect from the gate. Off, the
  behaviour is exactly what it was.
- **The door shows once per launch.** The server keeps an in-memory
  `gate_passed`, set by `POST /api/welcome/connect` and by the new
  `POST /api/welcome/skip` (Not now), and reports it in `status`; a browser
  that finds it set - a second tab, the phone as a second screen - goes
  straight in. It also skips when the tool is connected to a real PLC, or when
  the switch is off. It does NOT skip for the simulator any more: the simulator
  coming back at boot is not an operator's connection, and on a config with it
  enabled the door never showed at all. The decision is `gateWanted(status)`.
- **The door posts to `POST /api/welcome/connect`** `{host, username,
  password}`, not to `/api/connect`: that one clears `active_connection` and
  with it the connection's own signal list, so confirming the same PLC would
  have arrived at empty charts. The new route finds the named connection the
  address belongs to (or makes one), takes the login as typed, and activates
  it the way Setup does. A bare host takes the scheme of the endpoint in use
  (`main.resolve_endpoint`): `"10.6.70.153"` → `s7plus://10.6.70.153` or
  `opc.tcp://10.6.70.153:4840`; anything with a scheme is taken as written.
  `/api/connect` accepts a bare host the same way, and refuses an empty one.
- `status.endpoint`, `status.username` and `status.has_password` now fall back
  to the active named connection (or the only one) when `cfg.endpoint` is
  empty. **The door pre-fills its address from `status.plc_endpoint`**, a new
  field: the plant (`cfg.endpoint`, else the named connection) whatever the
  driver is dialling right now - with the simulator up, `status.endpoint` is
  `localhost:4855` and the first exe offered exactly that as the PLC. Use
  `plc_endpoint` for anything that means "the PLC", `endpoint` for "what the
  driver is on".

The gate is markup inside `#splash` (`form#gate`) and lives in shared `ui/`,
so it is on the phone too; the markup was added after the `data-el="tag"`
line so `tests/test_boot_page.js` still holds. On the packaged Windows exe the
console window minimises itself two seconds after the browser opens.

---

## 2026-09-17 — five recorder/trigger bugs fixed in `app/` (it ships in your payload)

From an adversarial bug hunt; the write-up is `docs/BUG-HUNT-2026-09-16.md`
(34 verified defects, 29 still open - worth reading if the phone ever acquires).

- **A trial has a third sidecar now: `<file>.db.name`.** Renaming a SEALED
  trial used to rewrite its meta table, which changed the bytes the `.sha256`
  describes - every renamed trial carried a stale seal. The label now lives
  beside the file; `list_trials`, `trial_data` and the CSV preamble read it
  through `recorder.read_name()`. `_delete_trials` removes it. **If anything on
  the phone copies, syncs or deletes trial files, it must carry `.name` the way
  it carries `.sha256`.** `PATCH /api/trials/{fname}` keeps its contract.
- `Recorder.flush()` moves its watermarks only after the commit lands. On a
  full disk sqlite rolled the whole flush back but the watermarks had already
  moved, and the trial stopped normally with a hole in it. A phone's storage
  fills more readily than a laptop's; this one mattered more for you than for us.
- New 422s: `POST /api/trial/start` with a negative `pretrigger_s`;
  `PUT /api/trigger` with `stop_mode: duration` and `duration_s <= 0`.
  `POST /api/trigger/arm` now also refuses a signal stop with no stop signal or
  an unknown `stop_op`, and an armed trigger disarms itself if its stop signal
  leaves the signal list. If the phone UI has its own trigger form, surface
  the `detail` string - it says what is wrong in the operator's words.
- **Payload additions, all optional to consume:** the trigger payload carries
  `disarm_reason` (non-empty when the engine disarmed itself - show it, the
  chip alone just reads DISARMED); `GET /api/trials` rows and
  `GET /api/trials/{f}/data` carry `recorded_name` beside `name` (the label);
  `.../data` also carries `seal_ok` - `true`, `false` when the file was changed
  after it was sealed, `null` when there is no seal. It is a re-hash on open,
  so a very large trial opens a second or two slower.
- `PUT /api/trigger` validates as a DRAFT while disarmed (a stop mode chosen
  before its signal is accepted) and fully while armed or recording (422, and
  the trigger keeps the rule it had). NaN and Infinity are refused everywhere
  a number is taken. One validator: `app.trigger.validate_trigger`.
- Labels are one printable line, at most 120 characters (`recorder.clean_label`).

---

## 2026-09-15 — seven fixes from a RENDERED audit (shared `ui/`; `RULER_H` = 58)

Headless Chrome at 1280/1366/1920, pictures read by the auditors. Two of
these touch things you share:

- **`RULER_H` in `app.js` is 58 now, not 46** — the desktop value you had
  already changed on the phone side, for the same reason (uPlot draws two
  label lines under a tick). If your branch overrides it, the override is
  redundant now.
- Under 1500px the topbar's connection chip hides its state text and the
  third action (Start simulator) and clips at its own border, so the buttons
  never paint over the gear and the window selector. If the phone layout has
  its own chip rules they win by specificity; check `#chip-conn` once.

The rest: `.pane-name` is 116px (a named pane no longer cuts mid-glyph), the
Signals Pane select is width auto (min 74, max 168), the Analyse palette
heading is nowrap, `.cv-form` grows to 760px at 1920, `.wsel-opt` is nowrap
above 1500px so "1 min" is one chip.

---

## 2026-09-15 — a desktop polish pass over shared `ui/` (no behaviour change)

Sixteen small consistency fixes from a five-lens audit, all in `ui/style.css`,
`ui/index.html` and three strings in `ui/app.js`. None changes behaviour or
any id/class app.js relies on. The ones that could show on a phone:

- `input:focus-visible, select:focus-visible` replaces `:focus` — a checkbox
  or select tapped no longer keeps the keyboard ring.
- `.btn.small` follows `--fs-small`, and the compact-density rule is now
  `.btn:not(.small)` — it used to make small buttons LARGER in Compact.
- `.sig-val` reserves `min-width` and right-aligns, like `.pane-head .val`,
  so a value gaining a digit no longer moves the name's ellipsis.
- `.pane-tools .pane-more` padding matches the LIVE chip's box.
- `.trial-row .tools button` is 13px with a 20px min-width and 16px line box
  (the Open button's); TRUNC badge weight/tracking match the other badges.
- Trial-row info reads "148.9 s · 0 events · 1764 kB" (was "148.9s · 0ev ·
  1764kB"); the empty state says what to do next; toolbar buttons are
  Sentence case ("Fix names", "Export CSV").
- Buttons/tabs/headings use `--ui-font` instead of a hardcoded Segoe UI.

Second box, same day, thirteen more from the same audit: the `panes` select
now matches the window segment's face, height and corner; `--ok` is declared
once in `:root` as `var(--confirm-fg)` (resolves per theme); folder rows in
the signal table use `--accent-dim` like signal rows; the chart drag-select,
the Analyse drop target and the connection fault chip follow the theme's
accent/warn via `color-mix` instead of hardcoded teal/amber; the palette flash
icon has a 15px hit box; the pane-header dot sits on the name's baseline; the
CSV/Grid labels share one column; `#sig-hint` scales with the density.

---

## 2026-09-13 — delete several trials at once (shared `ui/`, new endpoint)

The operator's one open request from use: a tuning session leaves a dozen
throwaway recordings and the per-row trash can meant a dozen confirms.

**New endpoint:** `DELETE /api/trials/bulk` with body `{"files": [...]}`.
It answers per file, never rounded to "done":
`{"ok": bool, "deleted": [names], "failed": [{"file", "error"}]}` — `ok` is
true only when nothing failed. Errors are `"unknown trial"`, `"still
recording"`, or the OS's message. Declared *above* the `{fname}` route on
purpose, or FastAPI reads "bulk" as a file called `bulk.db`. The single
`DELETE /api/trials/{fname}` keeps its status codes (404 / 409) and now runs
through the same helper. **The sidecars are removed with the file** by both
routes: the `.sha256` seal, which was left orphaned before and the next
trial to reuse the name would have inherited; and, from `cd92a14`'s
follow-up, SQLite's own `-wal` and `-shm` — every reader opens `mode=ro` and
cannot checkpoint, so they outlived the file by 32 KB apiece. That one was
your finding, on the demo; thank you.

**In `ui/`:** the Trials rows select on the Signals table's keys — ctrl-click
toggles, shift-click extends a run, a plain click still opens the trial. A
bar above the list (`#trial-selbar`) shows "N selected · Delete · Clear". The
confirm is one dialog naming what goes, with count and size, and it leaves
out (and says so) the trial being recorded and the one open in the viewer.
The pure parts are `pickTrial()` and `trialDeletePlan()` in `app.js`, and
`tests/test_trial_select.js` pins them.

**On a phone:** ctrl-click and shift-click do not exist under a finger. If
the phone layout shows the Trials list, it needs its own way in — a long
press to start selecting is the usual answer — and then the same
`trialSel` / `paintTrialSelection()` / `deleteSelectedTrials()` path works
unchanged. The endpoint and the plan logic need nothing from you.

---

## 2026-09-11 — the pane "..." menu now fits the window (shared `ui/`)

`ui/app.js` + `ui/style.css`, so this is yours too, and it matters more on a
phone than on a laptop.

The pane's y-limit menu was `position: fixed`, clamped horizontally and not at
all vertically. On a pane with many signals — 68 on one is a real config — the
column of rows ran off the bottom of the screen, nothing scrolled, and the
maximize button at the end of it went off the edge with them.

Now `placePaneMenu(el, list, btn)` caps the list to the room actually
available, opens the menu upward when it does not fit below and there is more
room above, and clamps the whole thing inside the window. The rows are a
scrolling box (`.pane-menu-list`); the rule and the button below stay put, so a
long list costs scrolling rather than the button. The list keeps a 72px floor
so a short window gives something usable instead of a sliver.

**If you have your own placement for this on a phone, check it against the same
cases** — a short viewport and a button near the bottom are where it broke.
`placePaneMenu` takes an optional fourth argument (a window-like
`{innerWidth, innerHeight}`) purely so it can be exercised without a browser;
`tests/test_pane_menu.js` uses it.

---

## 2026-09-11 — the launcher icon is the kit's eye (settled)

Both modules now carry the real mark instead of the hand-drawn stand-in, and
the phone side is where it was finished. `begia-wave-through.svg`: the stepped
trace at full strength outside the lens and at 40% seen through it, the lens
ring, the amber iris. Scaled 0.48 and centred so the trace's tails end 30dp
from the centre.

**Do not "fix" that 0.48.** It is the result of two rounds against real masks:
at 0.58 the tails were cut by the phone's squircle *and* by the tighter circle
Wear crops to. A launcher keeps only the central 72 of the 108 and scales it to
fill, so previewing the whole canvas makes the mark look far smaller than it
lands — which is exactly how an icon gets talked into being too big again.

The file is hand-written and the desktop side has no generator for it any more:
one existed briefly on the `icon/brand-eye` branch, that branch was merged and
the artwork then taken further by hand, so the generator was deleted rather than
left in the tree to overwrite the better version. The icon lives on the phone
side now. If it should ever be driven from the brand kit again, say so and it
can come back pointed at the asset that actually ships.

Two facts about the format worth keeping, since both fail silently rather than
erroring: Android has no `<circle>` or `<ellipse>`, so each becomes a pair of
arcs; and Android gradients are absolute, so SVG `objectBoundingBox` gradients
must be resolved against each shape's own box.

---

## 2026-09-11 — `design/ui-refresh` is merged; base on `main`

`main` is now `78160b9`, a `--no-ff` merge of the 63-commit branch. `main` and
`design/ui-refresh` have identical trees.

**Do this:** branch from `main`, not from `design/ui-refresh`. The branch is
kept only as history. 209 Python tests and 18 Node tests pass at the merge.

Tags `v0.1 v0.3 v0.8 v0.8.1 v0.9` are all on origin now (they were local-only
until 2026-09-10). This matters to you: `git describe` on a fresh clone
resolves properly again, so a build made on another machine stamps
`v0.9-17-g…` instead of falling back to `v0.3-…`. If you saw nonsense build
stamps in a clone, that was why, and it is fixed. Version is still **0.9** —
it is not bumped without the user asking.

---

## The acquisition rewrite — matters only if the phone ACQUIRES (option A)

The sweep is now **one** `GET_MULTI_VARIABLES` request per cycle instead of one
read per tag: 19.11 ms → 1.07 ms for 18 tags, a 17.9× speedup, which is what
made a 10 ms rate real rather than aspirational. In `app/s7plus.py`.

If you port or reimplement the driver, these are the facts that cost real time
to establish, all of them measured against a plant CPU 1517F:

- **Framing.** The payload is
  `[0u32][item count][TOTAL address-field count][all addresses][qualifier][0u32]`.
  The total is summed across every item. Writing a per-item field count — the
  obvious first guess — is refused with error `0xA201BB0005A6FF88`.
- **Batch size.** 32 ✓, 64 ✓, **128 resets the TCP connection.** `MAX_BATCH = 32`
  and `MAX_BATCH_BYTES = 4096` (`app/s7plus.py:83`) are where that landed. Cost
  per item is `16 + 4 * (len(lids) + 4)`.
- **Fallback.** `S7PlusDriver._batch_reads` is a *class* attribute
  (`app/s7plus.py:372`) and flips to `False` on a CPU that refuses the multi
  read, so per-tag reads still work. Keep that escape hatch if you reimplement.
- TLS is **mandatory** for s7comm-plus. There is no plaintext mode to fall back
  to on a phone.

**A coupled bug went with it, and this one bites option B too.** Per-tag reads
gave every signal its own timestamp, so `uPlot.join` expanded the table 1.79–4×
before drawing. Batched reads give one timestamp per sweep and the expansion is
1.00×. If you build a viewer on a *different* chart library, keep the same
property — one timestamp per sweep, shared by every signal in it — or you will
rediscover the 4× memory and the sluggish redraw on a phone, where it hurts
considerably more than on a laptop.

Background and the measurements: [ACQUISITION-RATES.md](ACQUISITION-RATES.md).

---

## Endpoints the phone side already depends on

Listed so a desktop change never breaks one of these silently.

| endpoint | who asks | what it is for |
|---|---|---|
| `GET /api/payload/info` | the shell, on first contact and from Setup | version/build/bytes before offering an update — lets it say "0.10, and you have 0.9" |
| `GET /api/payload` | phone Setup screen | the `.begia` zip itself |
| `GET /api/watch` | the phone's relay, on loopback, a few times a minute | recording or not, since when, marks so far, one analog signal's latest reading |
| `GET /api/state`, `WS /ws` | the page, once loaded — phone or laptop | unchanged contract |

The payload carries `app/`, `ui/` and presets and **refuses config, trials and
passwords** (`tools/make_payload.py`). That refusal is a security property, not
a convenience — the port must not start bundling `config.json`, which holds the
plant password. Anyone who can reach the port can fetch the payload.

`TRIALREC_HTTP_LAN=1` makes the service bind `0.0.0.0` (`app/main.py`), which is
what lets a phone reach it at all.

---

## UI fixes worth knowing about, since `ui/` is shared

The phone and the laptop run the **same** `ui/` folder, so these are yours too.

- **Window chips blank under 1500 px.** A later base rule `.wsel-short
  { display: none }` (1 class) outranked nothing and won over the narrow-window
  rule at every width below 1500 px, so the chips were empty on a 1280 px
  laptop. Fixed by raising the specificity to `.wsel-opt .wsel-short` (2
  classes) in `ui/style.css:607-614`. This is the bug your session found — it
  is in, and verified over HTTP against the built exe.
- **Panes.** `sig.pane` is an **identity** and is never renumbered, because
  recorded trials refer to it; the number shown to the operator comes from
  `paneLabel()` and is gapless from 1. Do not "tidy" pane ids.
- Panes fold to their header, and 1–4 fill a page with the rest a scroll away
  (`panesPerPage`, localStorage `panes_per_page`); digital signals draw as
  named lanes at a fixed height rather than as half-height charts.
- A Y axis can sit on either side, per signal, chosen from the sidebar.

Two traps that cost this session time and will cost yours the same:

- **Byte-searching `BEGIA.exe` for a UI string proves nothing** — PyInstaller
  compresses the bundle, so not even `uPlot` is findable. To check what a built
  exe actually serves, fetch `/style.css` over HTTP from the running exe and
  compare it to source.
- **The build stamp needs a clean tree when the build STARTS**, and the running
  exe must be stopped first. `build_exe.bat` now saves and restores `BEGIA.spec`
  in the same try/finally as `version.py`, because `--noconfirm` regenerated the
  spec on every build and left the tree dirty — which then stamped the *next*
  exe `-dirty` for a tree whose only change was the last build's own leaving.

---

## Standing constraints

- **Never stop `BEGIA.exe` while it is recording.** Check `GET /api/state` for
  `recording: true` first.
- The repo is **private**. The plant credential (`EAF_Admin` /
  `<the EAF_Admin password>`, `opc.tcp://10.6.70.153:4840`) is quoted in plaintext in
  `docs/BACKLOG.md` (the FIX item 1 write-up) and has been on origin since
  `ca23472`. The packaging leak itself is fixed; **rotating the credential does
  not remove it from git history**, and rotation is the user's call and the
  user's action, not ours. Do not carry that credential into anything the
  Android port ships.
- Versions are not bumped and tags are not cut unless the user asks.
