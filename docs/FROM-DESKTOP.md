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

## 2026-10-10, later — the bars under the header on light themes (shared `ui/`; nothing to do but install)

The owner: "the errors that show below the header should be a different
color ... dark themes is ok, light theme is wrong". `css/110-themes.css`:
on `data-theme="daylight"` and `"hmi"`, `.banner` is #fff4dc / #7a4f00 and
`.banner.fault` (`#linkbar`, `#faultbar`) #fdecea / #a1231b.

---

## 2026-10-10, late — "built-in simulator", not "built-in slag door" (shared `ui/`; nothing to do but install)

The owner, on the tablet: the app "still shows simulator as built in slag
door, not generic". `js/140-connections.js` (the Setup list's sub-line) and
`js/150-sidebar.js` (the connection card's meta line) now say "built-in
simulator"; the Graphite & Amber theme's note is "warm light".
`test_sim_connection.js` pins the new words. The simulator's tags are
unchanged until the new generic one (docs/SIMULATOR-DESIGN.md).

---

## 2026-10-10, evening — the licence screen: three ways in, no request file (shared `ui/`; nothing to do but install)

The owner, on the tablet: "send licence request file shouldnt be an option,
remove it ... copy code-scan qr as one option, open licence file the other,
and online as the main one" (and: keep "Get it from the laptop").

- The door (`#lic-door`): `#lic-online` first; then a `.lic-way` with the
  code, Copy, the QR, `#lic-scan` and `#lic-box-row`; then a `.lic-way` with
  `#lic-open`; `#lic-update` last.
- Gone: `#lic-share`, `#lic-request`, `#btn-licdev-request`,
  `#cvp-lic-request` and `licenceViewModel().request`. The page no longer
  calls `BegiaShell.shareRequest`; the shell may keep the method (nothing
  breaks), but no screen offers it any more.
- Tests: `test_licence_door.js`, `test_device_licence.js`,
  `test_phone_layout.js` pin the new order and the absence.

---

## 2026-10-10 — shell 3 wanted: native Google sign-in (plan only; NOTHING to install yet)

Online licence requests work end to end (the owner fixed the Google client
secret; the S10e's request was issued from the Licence Manager). The owner
now wants the request **inside the app**, and chose **native Google sign-in
in the phone app**. Plan: `docs/IN-APP-SIGNIN.md`. Your part:
- Credential Manager *Sign in with Google* (`androidx.credentials` +
  `googleid`): an ID token with
  `serverClientId = 118369397450-ohlnklbd4j5ls2pjr7l0c1e4l5q7qo7f.apps.googleusercontent.com`
  (the Web client ID Supabase uses - public) and the page's nonce.
- `BegiaShell.googleIdToken(nonce)` returns at once; the shell then calls
  `window.begiaGoogleIdToken({token, error})` (`error: "cancelled"` on back).
- The owner adds an Android OAuth client in Google Cloud (package
  `com.mgvictus.begia` + the release cert's **SHA-1**): please read that
  SHA-1 from the release keystore (`keytool -list`, never printing a
  password) and give it to the owner with the steps.
The desktop side (the licence screen exchanging the token with Supabase and
sending the request itself; a pop-up on PC) follows after the owner's usage
reset (13 Oct). Shell-2 phones keep the browser route.

---

## 2026-10-09, night, closing — plant-neutral examples (shared `ui/`; nothing to do but install)

The owner: *"i want it to be a generic product for any plant"*. In
`index.html`: `#gate-host` placeholder `192.168.0.1` (was the plant's PLC),
`#lic-box-addr` `e.g. 192.168.0.10`, `#cv-name` `Line 1 PLC` (was "EAF
furnace 1"), the simulator note "A simulated PLC" (was "A slag door PLC"),
the signal-set tooltip "from one machine" (was "furnace").

---

## 2026-10-09, night, very last — no company name on screen (shared `ui/`; nothing to do but install)

As on the request page: the licence door's `.lic-pc` / `.lic-phone` lines say
"your licence provider"; the Request online tooltips (`#lic-online`,
`#btn-licdev-online`, `#cvp-lic-online`) and `#licdev-report`'s no longer
name MG Victus; the trial report's footer (`js/250-report.js`) is
"... · BEGIA". `test_licence_door.js` pins the new door lines.

---

## 2026-10-09, night, last — the I/O check page redesigned (shared `ui/`; nothing to do but install)

The owner: *"add some setup panel on the left, without it it looks awkward.
redesign the IO page"*. `#iocheck-view` holds `.ioc-layout` (grid): an
`aside.ioc-side` with the numbered steps (`#ioc-pick`, `#ioc-search`,
`#ioc-name`), `.ioc-actions` (`#ioc-start`, `#ioc-stop`, `#ioc-csv`),
`#ioc-count`, `#ioc-start-note` and the cabinet `#ioc-cabinet`; and
`.ioc-main` with `#ioc-last`, `#ioc-ready`, `#ioc-popup`, `#ioc-empty` (now a
card with the four steps) and `#ioc-table-wrap`. Every id is the same, so no
script changed. `:root[data-layout="phone"]` (and windows under 760 px)
stack the setup over the results - checked at 375 px: no sideways scroll.

---

## 2026-10-09, night, latest — the page compressed and revalidated (shared `app/`; nothing to do but install)

The owner: *"let's do general optimization of the app"*. `app/main.py`:
non-`/api` responses carry `Cache-Control: no-cache` (was `no-store`) - kept,
revalidated by ETag, 304 when unchanged - and `PageGzip` (Starlette's
GZipMiddleware, min 1 KB) compresses them for a client that asks with
`Accept-Encoding: gzip`. `/api/*` is untouched (payloads, updates, trial
files go out as before). Measured on the dev server: 59 files, 1.21 MB
-> 0.40 MB first visit, 0 bytes on the next (all 304). The WebView on a
phone revalidates the same way; nothing to change in the shell.

---

## 2026-10-09, night, later — an update without a licence (shared `app/` and `ui/`; nothing to do but install)

Your design point, taken: `/api/update/check|install|fetch|payload` are in
`gates.LICENCE_OPEN`, and the licence door has `#lic-update` "Check for an
update" (after `#lic-file`), which checks and - asked - installs in one go
(`updFromDoor()` in `js/096-update.js`, words through `licenceSay`). An
unlicensed or lapsed phone then updates over the internet like the S10e
needed to; the update gives no use without a licence.

---

## 2026-10-09, night — updates over the internet: the server side, and PCs (shared `app/` and `ui/`; nothing to do but install)

The owner: *"we can start the updates on supabase"*.
- Supabase (begia-usage): table `releases` (a row per update file: build,
  kind `pc|phone`, channel `stable|test`, path, size, sha256, `manifest`,
  `sig`), public bucket `releases`, function `updates` (`GET ?kind=&channel=`
  -> the newest, with its public `url`, or `{none: true}`); `manage` gained
  `release_url` / `release`. `tools/publish_release.py` signs each file's
  manifest - canonical JSON `{build, kind, channel, file, size, sha256}`,
  sorted keys, no spaces - with the PAYLOAD key (Ed25519, key id
  41f1f2855a0c4667, the one the shell trusts) and uploads it. v0.9-240 is
  on the **test** channel for both kinds; **stable** is empty.
- PCs: `/api/update/check`, `/api/update/install` (`app/routes/update.py`),
  `config.UPDATES`, `config.UPDATE_KEYS`; Options card `#ov-update`,
  `js/096-update.js`.
- Phones, your route (no new APK): on Android `update.py` asks
  `kind=phone`; `POST /api/update/fetch` downloads the offer over Python's
  verified TLS (`usage._tls()`), checks size and sha256 against the signed
  manifest and keeps it in `<data>/update/`; `GET /api/update/payload`
  serves it; the card then calls
  `BegiaShell.installFromUrl(location.origin + "/api/update/payload")`
  (feature-tested), so the shell checks the payload signature again and
  asks. The card is on every device now; without `installFromUrl` a phone
  is told to open BEGIA's app.

---

## 2026-10-09, evening, later — Compare DBs picks its protocol (shared `app/` and `ui/`; nothing to do but install)

The owner: *"let me pick protocol for comparing the dbs. I prefer S7"*.
- `#dbc-proto` in the compare window: S7 / OPC UA (`.skind` buttons), S7
  unless picked, kept in `localStorage` `begia_dbc_proto`.
- New `GET /api/dbcompare/blocks?proto=` (what A and B are picked from) and
  `POST /api/dbcompare/close`; `GET /api/dbcompare` takes `proto` and
  answers `proto` (what it read over) and `note`. Over the active
  connection's protocol it reads through `S.driver`; over the other it opens
  its own driver instance to the same host (the saved connection over that
  protocol gives the login; none saved: the protocol's default port, no
  login), polling nothing, closed by `close` or after 5 minutes unused. The
  simulator is read as it is (`note` says so). A failed first connect is a
  502 naming the host and the protocol.
- A phone's compare would open its own S7 session from the phone - the
  shell needs nothing new.

---

## 2026-10-09, evening — Compare DBs; Request online on the Licence card (shared `app/` and `ui/`; nothing to do but install)

The owner: *"compare 2 dbs (2 vertical tables) marking with colors what has
the same value what doesnt"* and *"put request online on the licence card
too"*.
- New `GET /api/dbcompare?a=<node>&b=<node>` (`app/routes/dbcompare.py`):
  every member of both nodes (`members_of`, at most 2000 a side), read once -
  the S7 driver in batches (`read_values`, new), any other driver a probe a
  member - and lined up by the path below the node. The answer: `rows` of
  `{rel, a, b, st}` (`st` same / diff / a / b / err; a side is null where the
  member is missing, `{error}` where it could not be read), `counts`, `a` and
  `b` as `{node, members, capped}`, `read_s`. One compare at a time: 409
  while one reads; 409 `not connected` without a PLC.
- `ui/`: `#btn-dbcompare` in the Signals toolbar opens `#dbc-overlay`
  (`js/265-dbcompare.js`, `css/157-dbcompare.css`); under 700 px wide the
  Type columns are hidden. Nothing in it needs the shell.
- The Licence card (`#ov-licence`) and the phone's LICENCE block
  (`#cvp-lic`) have **Request online…** (`#btn-licdev-online`,
  `#cvp-lic-online`) where the request page can be opened: a browser, or
  shell 2 with `BegiaShell.openUrl` (`deviceLicence().online`).

---

## 2026-10-09, day — a shorter Options page, no paste box (shared `ui/`; nothing to do but install)

The owner: *"remove the typeface selections from options, compact theme to a
dropdown"* and *"remove the paste field for the license"*.
- Options: the Theme and Typeface cards are gone; Display's first row is
  `#ov-theme-now` (the theme in force, its card) over `#ov-themes`, now a
  dropdown list (`.ov-theme-menu`, `toggleThemes()`, shut by an outside
  click or Escape). `#ov-sans`, `#ov-mono`, `.ov-font*` are gone; the faces
  stay applied (`applyFonts`). The only `.ov-card.ph-adv` is gone with them.
- The licence door: `#lic-text` and `#lic-go` are hidden, not removed - a
  scan (`takeLicenceScan`) and a picked file still go through them.
- The request page (`ui/request.html`) has its own sheet now and a larger,
  centred logo.

---

## 2026-10-09, night — licences asked for online (shared `app/` and `ui/`; THE SHELL GAINS `openUrl`)

The owner: *"the request would come by signing up with email or google"*,
Google only for now, the same Supabase project as the usage inbox.
- The door's `#lic-online` (**Request online…**) opens `ui/request.html`
  (served by the device's own BEGIA) in a real browser: Google refuses to
  sign anyone in inside a WebView. On a phone that is
  `BegiaShell.openUrl(url)` - **new in the shell** (ShellBridge.kt +
  MainActivity.openUrl: ACTION_VIEW, https anywhere, http only to
  127.0.0.1/localhost), on branch `migration/app-id` in the phone repo,
  built but not installed yet. Without it the door says to update the app.
- The page signs in at `config.BACKEND` (Supabase Auth, Google; token in
  the #fragment, kept in sessionStorage) and inserts into
  `licence_requests` with the person's own token (RLS: their own rows).
- `GET /api/licence/online` (what to send), `POST /api/licence/online/check`
  (asks `config.LICENCE_PICKUP` by code; an issued licence is installed
  through the same path as `POST /api/licence`). Both in `LICENCE_OPEN`,
  not in `auth.OPEN`. `licence.online: false` switches both off.
- The door checks once as it opens and every 20 s while it waits.

---

## 2026-10-08, late night — three more usage counts (shared `app/` and `ui/`; nothing to do but install)

The owner: *"make the apps count how many times it has been opened and how
long time it spent recording, how many different plcs etc. i want to see this
data in license manager, not in the app"*. The ledger (`app/access/usage.py`,
`usage-<CODE>.json`) gains:
- `opens`: the page POSTs `/api/licence/usage {"open": 1}` once per session
  (`countOpen()`, js/095-licence.js, `sessionStorage.begia_opened`, marked
  only after a 2xx) - so in the BEGIA app, once per WebView session.
  `UsageBody.open`.
- `plcs`: `on_link(address)` - `S._plc_address()` gives the driver
  endpoint's host, "" for loopback (the simulator); the ledger keeps 16 hex
  of HMAC-SHA256 under sha256(PLC_TAG + report key), never the address; a
  link before the report key exists waits in memory (`plcs_waiting`).
- `iochecks`: `iocheck_start` calls `on_iocheck()`.
- `totals()` (a request's `usage`) gains `opens`, `iochecks`, `plcs_seen`.

A ledger written before reads them as 0 and []. The report format and its
signature are unchanged (the new fields are inside the ledger). The shell
needs nothing: its `shareUsage()` still fetches the report from loopback.
The Licence Manager imports the report files and shows them per device
(IBA-LICENCE-CODE).

**Then, the same night: the report goes to the internet by itself** (the
owner chose "Automatic upload to MG Victus"). `usage._maybe_send()` from the
heartbeat: at the first one after a start, then every six hours at most, an
hour on after a failure, in a thread `usage-send`; it POSTs `report()` to
`config.USAGE_INBOX` (a Supabase function, `begia-usage`, EU) with a TLS
context that on a phone loads Android's roots (`/apex/com.android.conscrypt/
cacerts`, then `/system/etc/security/cacerts` - Python on the phone has
none). Needs the INTERNET permission the app has. `licence.report_url ""`
stops it, `BEGIA_USAGE_SEND=off` too: **set that in boot_test and any long
test run on the phone stack**, or its heartbeat sends a test device's report
to the real inbox.

---

## 2026-10-08, night — the Licence card, on every device (shared `app/` and `ui/`; nothing to do but install - the phone shows it in Setup and Options)

The owner: *"in the apps (pc and android), make a clearer section for
license stuff and show the validity and expiry date/'no end date'"*.
- **`#ov-licence` is now on every device**, the first card after Display
  (before Access); the laptop's `#lic-row` in the Access card is gone with
  `#lic-says`, `#btn-lic-door` and `#lic-report`, and so is the CSS that hid
  the card on a laptop. Its lines are `#licdev-state` (a chip, `data-state`
  ok / soon / late / bad: green valid, amber ending in 14 days or in its
  grace period, red not licensed or the grace over), `#licdev-note` (hidden
  when empty) and `#licdev-facts`, a `<dl>`: Valid until (or *Ended*) with
  the day and "in 183 days", or **No end date**; Licensed to; Used by;
  Device; Licence (id · made <day>); Device code with a Copy button
  (`.lic-copy`, `copyText`). `#licdev-says` is gone.
- **Setup › This phone's LICENCE block** says the same: `#cvp-lic-state`,
  `#cvp-lic-note`, `#cvp-lic-facts` in place of `#cvp-lic-says`.
- The door button in both reads **Install a licence…** (was *Licence…*).
- js/095-licence.js: `licenceFacts(li)` -> `{state, head, note, rows}` from
  ok, why, start_allowed, days_left, expires, issued, id, name, holder,
  machine, clock_note - the licence's own `days_left`, never the browser's
  clock; `LICENCE_GRACE_DAYS` / `LICENCE_SOON_DAYS` = 14, pinned against
  app/access/licence.py. `deviceLicence()` returns `lic` (the licence)
  instead of `line`. `paintDeviceLicence()` paints the card on a laptop too
  (from the brief, no shell asked); `paintLicence()` (every licence frame)
  now only repaints a laptop's card - a phone's is still painted with
  Options and Setup, from the one `BegiaShell.info()` reading.
- The brief (`GET /api/licence`, the 402 body, the socket's frames) gains
  `issued` and `clock_note`. The shell's own `info().licence` already has
  `issued`; it has no `clock_note`, and the card says none then.

Pinned by tests/js/test_device_licence.js (rewritten), test_licence_door.js,
test_options_layout.js, test_phone_layout.js and tests/py/test_licence_v2.py.

---

## 2026-10-08, evening, later still — the Signals table's kinds (shared `ui/`; nothing to do but install)

The owner: *"make the filter selectors bigger and in order ALL, DIGITAL,
ANALOG, STRING, OTHER"*. `#sig-kind` (index.html) now holds `data-kind` all /
digital / analog / string / other, labelled in capitals, `all` active and
`sigKind = "all"` at start (js/120-sigtable.js). `signalKind(sig)` decides:
`is_bool` digital, `is_text` string, `OTHER_TYPES` (BYTE WORD DWORD LWORD,
CHAR WCHAR, the time and date types) other, else analog. `"text"` is no
longer a kind - nothing stored it. Larger: `#sig-kind .skind` 13 px bold,
36 px; on a phone (120-phone.css) the nav takes a row and the five share it
(69 px each at 375 px).

---

## 2026-10-08, evening, later — Refresh in Add signals (shared `app/` and `ui/`; nothing to do but install)

The owner: *"i want a button to refresh the search PLC view, i know for a
fact my colleague added a member to a db but it doesnt appear"*.
- `POST /api/plc/refresh` (routes/signals.py): 409 while recording, the
  trigger armed, an I/O check running, a source switch, or not connected;
  then `S.driver.reload()` and it answers once the driver is connected with
  its index ready (90 s at most): `{ok, state, index, count, unit}`.
- `S7PlusDriver.reload()`: `start()` again with the same endpoint and login
  (the block list and the type information are read per session).
  `OpcUaDriver.reload()`: `build_index()` again under `_index_lock` (a
  browse is live already).
- The picker: `#btn-plc-refresh` in `.pk-bar`; `refreshPlc()` re-reads the
  tree (`loadTreeRoot` when browsing) and re-runs a search on screen; the
  ticks (`picked`) stay.

Pinned by tests/py/test_plc_refresh.py and tests/js/test_plc_refresh.js. On
a phone the bar wraps already (`:root[data-layout="phone"] .pk-bar`), so the
button takes the next place on its line.

---

## 2026-10-08, evening — right-click, and a long press on Android (shared `ui/`; nothing to do but install - and try it on a device)

The owner: *"we're not using right click at all, let's find some use for
it. same for hold down finger/pen on android versions"*. New script
js/055-ctxmenu.js (index.html, after 050-dialog.js):
- `onCtxMenu(el, fn, {mouseOnly})`: `contextmenu` (a mouse), or a pointer
  held 450 ms (`pointerType` touch/pen, 10 px slop) - the innermost bound
  element answers (`e.ctxTaken`); a field keeps the browser's menu; after a
  hold the lift's click is swallowed, `dragstart` is prevented (a Signals
  list row is `draggable`), and the `contextmenu` Android sends as well is
  ignored. `navigator.vibrate(12)` on the hold.
- `openCtxMenu(x, y, items)`: `.ctx-menu`, fixed, placed at the pointer
  (above it if it does not fit below); 44 px rows on a touch device
  (140-late.css). Items: `{head}`, `"-"`, `{label, act}`, `{label, choices}`.
- Menus: `signalMenu(sig)` on .sig rows, Signals table rows (130-tree.js) and
  `.ph-sig` readouts; `paneMenu(paneNo, t)` on `.pane-head`, and on the plot
  through a new `wheelZoomPlugin` option `contextMenu(u, e)` - installed in
  `installTouchGestures`, which ignores a touch-made `contextmenu` (the hold
  is the readout, unchanged); `analysisMenu(t)` on the Analyse plot; the
  trial row with `mouseOnly` (a finger's long press there still selects).

On a device please check: a held signal row opens its menu and does not
start a drag; a held chart header opens the chart's menu; a held plot still
shows the readout; a held trial still selects. The WebView may or may not
send `contextmenu` on a long press - either way it opens once.

Later the same evening, from your touch-lab pre-check (a phone's pane
header is nearly all readouts, the chart menu only on the 30 px #1 chip):
`signalMenu(sig, {paneNo, x, y})` from a `.ph-sig` readout ends with the
chart's name ("Chart #1…" / its own name), which opens `paneMenu(paneNo)`
at the same place.

---

## 2026-10-08, afternoon, later still — a WORD in hex or binary (shared `app/` and `ui/`; nothing to do but install)

The owner: *"data types of byte, word, double word should be possible to
visualize as decimal, hex and binary formats"*.
- `SignalCfg.fmt`: "" (decimal), "hex", "bin" - set by `PATCH
  /api/signals {fmt}` and `PATCH /api/signals/bulk {fmt}` ("dec" is ""; 422
  otherwise; not refused mid-trial, display only). A config key: a phone on
  an older payload keeps it untouched (Stored).
- js/070-signals.js: `BIT_WIDTHS` (BYTE/USINT 8, WORD/UINT/UInt16 16,
  DWORD/UDINT/UInt32 32, LWORD/ULINT/UInt64 64), `bitWidth`, `bitsText`
  (`16#1234`, `2#0001_0010_0011_0100`, zero-padded, `_` in fours; null for a
  non-whole, negative or too-large value - a scaled one), `numText` (a whole
  number of such a type without `.00`), `fmtSource` (a trial's signal takes
  today's by node). `readingText`, `fmtSigVal`/`fmtVOf` (js/170-plugins.js),
  the axis tag (hex for bin) and Analyse's A/B/extremes use them. No BigInt:
  `Number.toString(16|2)`.
- The choice: a `select.fmt-sel` in the Signals table's Type cell (and
  `BULK_FIELDS` offers it to the selected rows that can take it), and a
  "Shown as" field in `openSignalDetail` - `ask()` fields now take
  `options: [[value, label], ...]` for a select.
- The sidebar: `.sig-row1.bits-row` wraps the value onto its own line when
  it does not fit beside the name (140-late.css).

Pinned by tests/js/test_number_formats.js and tests/py/test_signal_formats.py.

---

## 2026-10-08, afternoon, later — a log file beside the exe (shared `app/state.py`; nothing to do)

`state.log_beside()` adds a rotating `begia.log` (5 MB, three kept) to the
root logger - called only when `sys.frozen`, i.e. the PyInstaller exe. The
phone's backend is not frozen and writes no file; logcat stays its log.
`state.LOG_FORMAT` now names the format `basicConfig` already used.

---

## 2026-10-08, afternoon — your tablet diagnosis, fixed; BOOL for BBOOL (shared `app/` and `ui/`; nothing to do but install)

The four causes you replayed on the tablet's own set, and two found on the
way (the owner said "execute"):
- **Constants and a lagging CPU clock.** `push_tick` (app/push.py) keeps a
  cursor per signal on its ring buffer, `SignalBuffer.pushed`, instead of
  the global `S.last_push_ms` (gone); with no page open each cursor moves up
  to its newest sample. The init frame's backfill starts with the sample in
  force at the look-back's start (`SignalHub.history`), kept out of
  `_thin`. `state.page_points` shows any sample older than the look-back at
  its start, so a day-old gain does not stretch the page's history (the
  scrub bar). The recorder and the ring are untouched.
- **Readings on OPC UA.** `readingAge()` (js/070-signals.js): with
  `status.protocol === "opcua"` a non-UDP signal not in `unsubscribed` has
  age 0 - a value that holds no longer reads "—" three seconds in. S7 and
  UDP keep the arrival rule. This was the dash on DI_01..03 and on a steady
  Out_PV.
- **Ramps.** `holdAcrossGaps()` in `displaySeries` (js/190-scrub.js) puts a
  point 1 ms before a sample that came after a gap of more than
  max(0.25 s, 3 x rate): drawing only, not bools, not the stepped line.
- **Axes in a small window.** `axisBudget()` (js/180-panes.js, from
  `chartsEl.offsetWidth`, which a scrollbar does not move) is stored on each
  pane's record; `resizeCharts` rebuilds when it changes, carrying `yZoom`.
- **Zoom past now.** `notPastNow()` clamps a held pane, the live Strings
  strip and live Analyse, keeping the span.
- **BOOL.** `config.tia_type()`: the S7 driver reports BOOL for BBOOL and
  TIME_OF_DAY for TIMEOFDAY (search hits, tree rows, probes, `dtype_for`),
  and `SignalCfg` maps a stored one on load (pydantic 1 and 2 both;
  tests/py/test_tia_types.py runs it on pydantic.v1).

Checked on a throwaway server on the simulator: steady values read as
numbers, 8 axis columns at 1587 px and 3 at the 560 px pop-up (the plot
271 px, was 40), a zoom-out at a held pane's edge ends at now. Pinned by
tests/py/test_push_cursor.py, test_tia_types.py, tests/js/test_readings.js,
test_display_slice.js, test_tablet_live_fixes.js. Reaches devices with
shell 2 (main's payloads), or a backport if the owner asks for one.

---

## 2026-10-08, midday, later — a text row keeps its name (shared `ui/`; nothing to do but install)

Found while checking the entry below on the phone layout: in the sidebar's
Text tile, a long step text ("WAIT TO START AUTOMATIC SLAG DOOR PUSHING
CYCLE") took the whole row - `.sig-val` is `flex-shrink: 0`, a number's
reserved width - and the name went to 0 px; let wrap, the row grew and
shrank with every step under the finger. `.sig.sig-text .sig-val` is now
`flex: 0 0 60%`, one line, `text-overflow: ellipsis` (140-late.css); the
text panel above the charts still shows the whole value. Pinned in
`tests/js/test_text_signals.js`.

---

## 2026-10-08, midday — a signal off Live, still in its pane (shared `ui/`; nothing to do but install)

The owner, of the sidebar's Signals list: *"i need a way to deselect the
signals from this panel (not remove from pane, just hide the graph and it's
axis from live)"*.
- Each row has a box before its colour square (`.sig-show`; 26 px on a touch
  screen). The row's grid is `auto 10px 1fr` (touch `auto 32px 1fr`, a text
  row `auto 1fr`). Unticked, the trace and its Y axis leave Live - a digital
  signal's whole lane, a text signal's lane in the live Strings strip - while
  the signal stays in its pane, its readout stays in the header, dimmed, and
  it is recorded as before.
- `setTraceHidden(nodeId, hide)` (js/180-panes.js) is the one way in: the
  box, a pane header's readout (which used to hide only the trace and leave
  the axis) and the live strip's readout all go through it. It draws the
  charts again (`laneScalesFor`, `lanesShown`; `stepsPlugin`'s `drop`) and
  the list.
- `hiddenSigs` is kept in `localStorage.hidden_traces`: a reload or a restart
  comes back as it was left, per device. A trial's strip is unchanged: a
  hidden lane stays there, empty.

Pinned by `tests/js/test_hide_traces.js`.

---

## 2026-10-08, morning — the picker: a block once, and a parent's tick reaches its children (shared `ui/`; nothing to do)

The owner, in the Add signals picker:
- *"what is the difference between the 2 yvh134.a.b? ... i like the second
  one better"*: a block whose own name matches a search is shown once, as its
  own row (opened, the PLC's Input / Output / InOut / Static) - `blockOnce()`
  drops the flat group of the tags inside it, which matched only by carrying
  its name. Matches in other blocks still group under their blocks.
- *"selecting a parent should select all children"*: `treeTick()` - a ticked
  structure ticks every box drawn inside it ("covered": shown ticked, one
  entry in the selection, expanded by the server at Add); unticking one of
  them splits the structure into everything else; ticking the last box of a
  level takes the structure whole again; partly ticked shows indeterminate.
  A variable with members drawn under it (OPC UA's DataHMI) is ticked as the
  structure it shows. Pinned by `tests/js/test_tree_ticks.js`.

---

## 2026-10-08, later still — the rail on phones and tablets; a panel a finger sizes (shared `ui/`; your branch, merged)

Your `layout/touch-rail` (d99ebda + 657abe2), reviewed and merged: Options ›
Layout on every device ("Bottom tabs" / "Side rail" on a touch screen);
`wantedLayout` gives a tablet the rail at any width when chosen; `placeNav`
stamps `data-nav` (tabs|rail) and moves a phone's modules, eye and switch
into a 64 px rail while `data-layout` stays `phone`; `makeResizable` runs on
pointer events with capture (a tap stores nothing), with two remembered
widths, docked (`sidebar_w`) and drawer (`drawer_w` via `--drawer-w`); the
open drawer's edge carries `#resizer` and `#sb-toggle` shuts it. Pinned by
`tests/js/test_touch_rail.js`. It reaches devices with shell 2 (main's
payloads need it).

---

## 2026-10-08, later — the licence door keeps its answer (shared `ui/`; nothing to do)

From your shell-2 bench: the door's answer to a paste, a scan or the
laptop was wiped by the next licence frame (the socket retries every 6 s
while unlicensed), 1-5 s after it appeared. `showLicenceDoor()` now says the
opening sentence only when the door opens or the licence says something new
(ok, why, code or kind changed), and focuses the paste box only then. The
door's buttons are 44 px on a touch screen (`#lic-copy`, `#lic-share` were
`.btn.small`'s 40). Pinned in `tests/js/test_licence_door.js`.

---

## 2026-10-08 — licensing Phase 1: the payload asks every phone for its licence (shared `app/` and `ui/`; ACTION: shell 2 only)

docs/LICENSING-DESIGN.md Phase 1, built against the shell side on
`licence/phone`. **From this build on a payload needs shell 2**
(`payload.json`: `"min_shell": 2`, `"licence": 2`): shell 1 refuses it, and
under shell 2 the program asks for a licence. The last payload for the
shell-1 phones is v0.9-210.

- **What the payload expects from the shell** (as built on `licence/phone`):
  `BEGIA_DEVICE_ID = "and:<ANDROID_ID>"`, `BEGIA_DEVICE_NAME`, and
  `BEGIA_LICENCE_REQUIRED=1`, all set before `app.main` is imported, for a
  manifest with `licence >= 2`. Under Chaquopy `required()` is true anyway;
  with no `BEGIA_DEVICE_ID` the status is the refusal
  *"this phone's BEGIA app predates licences - install the newer BEGIA app,
  then the licence"* with no code (`machine_code()` is `""`).
- **The licence**: `<filesDir>/data/licence.json` (`config.LICENCE_PATH`).
  `verify()` is v2 (the shared vectors; refusals in `begialic/format.py`'s
  words, e.g. *"it was issued for another device"*), with the device check
  (`device` = `"phone"` on a phone) and the clock anchor (`clock_max` from
  the usage ledger). A pack is accepted wherever a licence is.
- **Routes, as the shell side assumes**: `POST /api/licence {text}` → 2xx the
  brief (now also `device`, `id`, `name`, `holder`, `start_allowed`) or 4xx
  `{"detail"}` (400 refused, 423 another copy holds the folder; a non-loopback
  caller cannot replace a newer licence with an older one); `GET
  /api/licence/request` → the v2 request (`machine` = this phone's code,
  `device`, `current`, `report_pub`, `usage`); `GET /api/licence/usage` → the
  signed usage report (in `LICENCE_OPEN`; signed in from the network,
  loopback free); `POST /api/licence/usage {screen_s, companion_s}`; the
  laptop's box `/api/licence/box/*` (open, prefix-matched; table in section 3).
- **Unlicensed means idle**: the service dials no PLC and starts no UDP while
  unlicensed (the lifespan's dial is `connections.dial_configured()`, run by
  the install route once a licence is in), and `start_checks()` refuses a
  trial - the trigger's `pre_start` too. Once the grace has ended while the
  process stays up (an hourly re-read), a running trial finishes and no new
  one starts.
- **Usage**: `<filesDir>/data/usage-<CODE>.json` and `usage-key-<CODE>.json`
  beside the licence; counted only where a licence is required; the page
  posts its visible seconds every 5 min; the shell posts `companion_s`.
- **The page** uses the bridge as specified: `deviceId()`, `info().licence`,
  `scanLicence()` + `begia-scan`, `pickLicence()`, `shareRequest()`,
  `shareUsage()`, `getLicenceFromLaptop(base)` + `begia-box`; the door is
  worded by `data-kind`, the LICENCE block sits in `#cv-phone` above SECOND
  SCREEN, the tablet's Options card is `#ov-licence`.
- Trials name their seat: `licence_id` and `licence_device` in provenance.
- **The page also uses** `BegiaShell.source()` and the `begia-found` event
  (as before, for the laptop field in the door), and the door offers *Send a
  licence request…* (`shareRequest()`) itself, since Setup is unreachable
  behind it. QR-A is not drawn yet: no QR library is vendored in `ui/vendor`
  (the hook takes qrcode-generator's `qrcode()` or qrcodejs's `QRCode`); the
  code shows as text. Nothing reads QR-A before Phase 3's webcam scan.
- **The spec** is `docs/LICENCE-FORMAT.md` (the usage report and the signed
  refusal of the box included). The vectors: `python <IBA-CODE>\tools\sync_vectors.py
  --to <phone repo>` copies them; that repo needs `tests/vectors/* text eol=lf`.
- **For `licence/phone`**, from the review of the box: `getLicenceFromLaptop`
  should show the answer's `detail` on any non-2xx (today a 507, 413, 400,
  403 box-off or 423 on the POST is ignored and the GET then says "no
  licence yet"). And a phone report's signed `shell` block carries `apk`
  from `BEGIA_SHELL_APK` if the shell sets it at boot (empty otherwise) -
  nothing can be added to a report after its signature.
- **The laptop boot test** (`tools/boot_test.py`, yours): a patch that copies
  the bench's `dist\licence.json` into the data folder and names a 402 as
  the licence door is prepared at the desktop session's scratchpad
  (`boot\boot_test-bench-licence.patch`, `git apply --check` passes on
  `licence/phone`).

ACTION: boot_test this payload under shell 2 with a bench identity and run
the door end to end in a debug build (scan, file, paste, the laptop's box),
then report. Nothing goes on a device before the owner says migrate.

---

## 2026-10-07, late night — a tablet has the phone's bars (shared `ui/`; nothing to do but install)

The owner, on the Tab S10 Lite: "the top header of the app on tablet has too
small buttons, i think it should follow more phone-like design".

- **The modules are tabs along the bottom** on a tablet, as on a phone:
  `placeNav` (`js/100-theme.js`) moves `#modules` into `#phone-tabs` when the
  layout is classic and `deviceKind` says tablet. An icon over each name,
  64px upright, a 52px row of icon and name sideways. The phone's six icons
  carry the tablet in their selectors (`120-phone.css`); Analyse and Options,
  which a phone has no tab for, have their own (`130-tablet.css`).
- **The top bar is 62px of 44px controls**: ☰ (only where the module has a
  sidebar to slide in), Analyzer / I/O check with their names, the eye, the
  connection chip with its word and address (it takes the room the tabs
  left), the acquiring chip sideways, the window and LIVE. The gear goes:
  Options is a tab. The acquiring chip's 12px clip at 1408 wide is gone.
- **The layout itself is unchanged**: the drawer upright, the docked sidebar
  sideways, Analyse, the Record card. A tablet no longer offers the rail.
- The laptop's bar and the phone's are as they were (shots at 1280 and 360).

Pinned by `tests/js/test_tablet_bars.js`.

---

## 2026-10-07, late night — a finger's size for a signal's small controls (shared `ui/`; nothing to do but install)

The owner: "the buttons to modify and highlight and move left, right are too
small for a tablet and for the phone ... in the signals list or panes
header". They were a mouse's size (9-20 px).

- Under `:root:not([data-device="desktop"])` (a tablet and a phone; the
  laptop unchanged), `130-tablet.css`: the signal list's colour square
  (`.sig .dot.swatch`) 32 px in a 32 px column; `.sig-flash` (◎), each
  `.sig-side button` (L, R), `.pt-del` (a pane's ✕) 36 px; `.rate-odd`
  32 px tall; a pane header's `.ph-flash`, `.ph-side`, `.ph-link` 36 px,
  their `.ph-row2` centred. A phone shows no second header line, so the
  header part is the tablet's.
- Measured in a browser: 1587x992 `?layout=tablet` and 375x812
  `?layout=phone` at 32/36 px, the laptop still 9-20 px.

Pinned by `tests/js/test_touch_controls.js`.

---

## 2026-10-07, night — two rows of readouts at most, then sideways (shared `ui/`; nothing to do but install)

The owner, from the Tab S10 Lite with 46 signals in one pane: "only allow
two rows of text in pane header, later just let it scroll sideways". The
readouts' wrap (the tablet's, from the entry below, and the phone's) had no
end: they ran down over the whole chart.

- `fitReadouts(strip)` (`180-panes.js`) counts the rows a header's readouts
  wrap into; past `READOUT_ROWS = 2` it sets `.ph-2rows` on the `.ph-sigs`
  strip: a grid of two rows, filled down then across, scrolling sideways
  (`130-tablet.css`, after both wrap rules). One or two rows wrap as before.
- `buildCharts` fits every strip before `fitPanes`; a `ResizeObserver` refits
  a strip whose size changes (turned, a value a digit longer) after the
  frame, and fits the panes again when its rows changed.
- The phone gets it too (its readouts wrap the same way); the laptop's
  single scrolling row never wraps, so never changes.

Pinned by `tests/js/test_readout_rows.js`.

---

## 2026-10-07, evening — the Tab S10 Lite (shared `ui/`; nothing to do but install)

The owner: "prepare a version for tab S10 lite". The shell APK runs on it as
it is (arm64, no orientation lock); what a 10.9" tablet gets is the tablet
layout, checked at its sizes - 880x1336 upright, 1408x808 sideways (2112x1320
at density 1.5) - with `tools/phone_shots.py` and the shell standing in.
The layout itself was already right: the drawer upright, the docked sidebar
and the laptop's bar sideways, `data-device="tablet"` both ways. Fixed:

- **Two traces in one chart**: the second readout was cut ("DB_SlagDo") and
  the strip had to be dragged sideways to read it; on a tablet the readouts
  wrap, as on a phone (`130-tablet.css`, `.ph-sigs`).
- **Analyse's palette handle** sat over the tree/list toggles: half over
  them sideways, right on the list toggle upright (any tablet under 900 px,
  not only this one). The header keeps that end clear (`.an-pal-head`
  padding, 22 px wide and 46 px narrow).
- **The A/B status line** under the Analyse chart said "(click)" and
  "(shift+click)" to a finger; it now carries both wordings
  (`.an-hint-mouse` / `.an-hint-touch`, `220-analysis-chart.js`), and the
  stylesheet shows one.

Pinned by `tests/js/test_tablet_s10.js`. Left as they are: the connection
chip's address ellipsis in the bar, and the sidebar's fold handle astride
its resizer - both the laptop's, by design.

---

## 2026-10-07, later — the simulator keeps its own signal list (shared `app/`; nothing to do but look)

The owner: "give the simulator its own signal list".

- **Config**: `SimCfg` gains `signals`, `groups`, `iocheck_inputs`,
  `iocheck_areas` - the same four lists a `ConnectionCfg` keeps (pydantic
  1.10-safe: plain fields, `Field(default_factory=list)`).
- **Where the lists on screen live**: `common._list_home()` - `cfg.sim`
  while `cfg.sim.enabled`, else the active connection's entry (None: no
  home). Both mirrors use it: `save_config` (signals, groups) and
  `ioinputs._inputs_changed` (the I/O check's two lists).
- **The switches**: `_stash_live_lists(home)` copies the four lists into
  their home before any change of source. `POST /api/sim/start`, joining,
  stashes the PLC's and installs the simulator's (`_restore_signals_for(cfg.sim,
  orphaned=no home)`: its own; else adopts an orphan list; else clears) and
  now answers `{"ok", "endpoint", "signals": {...}}` and owes `signals`,
  `trigger`, `iocheck_inputs` as well as `status`. Leaving it -
  `_activate` (Setup, the welcome, the S7 / OPC UA tabs), `/api/sim/stop`,
  `/api/connect` - stashes the simulator's and brings the PLC's back, even
  when the PLC is the active name already (`_lists_back_from_sim`; an
  address typed without a name gets an empty list).
- **Boot**: when the simulator was the connection and does not come back
  (welcome on, or it fails to start), `connections._sim_lists_off_at_boot()`
  puts the active PLC's lists on screen before the hub's buffers are made.
  A config from before keeps what the old mirror wrote (the PLC's copy);
  with no PLC at all the simulator keeps the only copy.
- `tests/py/test_sim_own_lists.py` pins it (12 cases).

---

## 2026-10-07 — nothing cut off: the overflow sweep (shared `ui/css/`; look on a device)

The owner: "check all views for overflowing buttons, pc, android and all".
`tools/phone_shots.py` now audits buttons as well (text clipped, a word
wider than its button, cut by or sticking out of its card, a strip scrolled
sideways, two on top of each other - judged as drawn, after the clipping
ancestors), and a shot may carry `"views"` (one load, a JS per view). All
views at 360x780 and 384x832 (100/115/130%), 780x360, and the laptop sizes.
Fixed, in the "overflow sweep (2026-10-07)" section of `css/120-phone.css`:

- `@container topbar (max-width: 21em)`: `.wsel-short` for `.wsel-full` -
  Live's bar at 130% (LIVE was past the edge).
- portrait, `[data-mod="review"][data-trial="open"] #chip-conn #conn-text`
  hidden - the open trial's chip ran 52px off the screen at 130%.
- `@container topbar (max-width: 26em)`: Live's / the open trial's chip is
  its lamp (`.sc-label` hidden) - 384px phones cut "Connected".
- `.text-panel` on a phone: two columns, `grid-auto-flow: row dense`,
  `.tp-val` on its own row - values were broken per letter.
- `@media (max-height: 500px)`: `#signals-view` scrolls as one,
  `#sig-table-wrap { flex: 0 0 auto }` - sideways, Save was under the tabs.
- `.pane-tools .steps-guides { padding: 0 8px }` after the 36px glyph
  buttons' `padding: 0`; `[data-device="phone"] .an-bar { max-height:
  none }` - the Combined A/B hint was cut in half.

And in `css/130-tablet.css` (an 800px tablet, whose bar ran LIVE past the
edge while recording): `@container topbar` 52em -> `.wsel-short`, 46em ->
`.brand` hidden, 40em -> `#btn-settings` hidden and `.mod { padding: 0 4px }`;
`.pane-tools .steps-guides { padding: 0 8px }` there too.

Checked by scrolling: content that starts under the bottom tabs scrolls
clear of them on every page. `tests/js/test_overflow_sweep.js` pins these.

---

## 2026-10-07 — the simulator is one connection in Setup; Not now starts nothing (shared `app/` and `ui/`; nothing to do but look)

The owner: "the not now button on login shouldnt turn on the simulator, the
sim should only be mentioned as one connection in the setup, run/connect and
that's it. Not now should not connect to anything new. both pc and android".

- **Boot** (`app/main.py` lifespan): with `startup_gate` on, a
  `cfg.sim.enabled` left from the last run is switched off in memory
  (`enabled = False`, `prev_endpoint = ""`) and nothing is started or
  dialled. With the gate off the simulator comes back as before.
  `POST /api/welcome/skip` was and is only the flag; it connects nothing.
- **Status**: the `sim` block now carries `enabled` -
  `{**S.sim.stats(), "enabled": cfg.sim.enabled}` - "the simulator is the
  chosen connection", which stays true through a Disconnect.
- **The page**: `#sim-block` / `#btn-sim` / `#sim-state` / `#sim-detail`,
  `updateSimStats` and the fault strip's "Start simulator" are gone. Setup's
  list ends with a Simulator row (`.cv-item.cv-item-sim`, tag SIM);
  picked (`cvPickSim`), `#cv-sim` replaces the form (`#cv-edit`) with
  `#cv-sim-url` and `#cv-sim-connect` -> `POST /api/sim/start`
  (`simConnect`). `onSimulator(st)` in `js/140-connections.js` is the one
  test (enabled, or the driver on the simulator's address); `connHere`,
  `updateConnCard` and `reconnect()` (Reconnect, `js/040-picker.js`) use it.
  The welcome's `gateWanted` no longer makes an exception for the
  simulator, and the door says nothing about it.
- The phone's drawer and Setup take it as they are (shared markup);
  `tests/js/test_sim_connection.js` pins it.

---

## 2026-10-06, night — the welcome picks the protocol (your branch welcome/protocols, merged; nothing to do)

Your b19ad50, merged into main as 20c272e after review (checked live at
1280 px: one line each, the default port dropped from the address, the fields
following the choice).

- **`POST /api/welcome/connect`** takes an optional `protocol`: `"s7plus"`,
  `"opcua"`, or `""` (as before: a bare address takes the scheme of the
  endpoint in use). Anything else is a **422**. It picks the scheme for a
  bare address (`resolve_endpoint(text, current, protocol)`); a URL is taken
  as written. The order inside is unchanged: the trial refusal, the running
  I/O check's PLC (`_refuse_if_check_elsewhere`), `_activate`.
- **S7**: a `username` sent with S7 is dropped, and an empty password keeps
  the saved PLC password (it used to be wiped on every confirm).
- **The gate**: `#gate-proto-s7plus` / `#gate-proto-opcua` radios,
  `.gate-port` spans filled from `default_ports`, `#gate-pass-label`;
  `gateProtoOf` and `gateFields` in `js/090-doors.js`.

---

## 2026-10-06, evening — the phone cleaned up (your branch phone/cleanup, merged; nothing to do)

Your a3de574, merged into main as 3dbd5a9 after review. For the record:
`#btn-menu` hidden on the phone off Live; Live's bar paddings; `.ph-sigs`
wrapping with `.ph-sig` 150 px; `.cv-actions` wrapping; `setBreakable`
(`<wbr>` after "." and "_") on the drawer's names and the Signals table's
addresses; `.ph-adv` behind `.adv-toggle` / `setPhoneAdvanced`
(`localStorage` "phone_adv", `<html data-adv="on">`); `#cv-phone` reordered,
every id kept; `tools/phone_shots.py` takes an `init` script.

---

## 2026-10-06, later — Y axes under a finger, the highlight, the selected-only sidebar, trial panes that move (shared `ui/`; check on a device)

All page-side; it reaches the phone with the next payload:

- **A finger on a Y axis** (Monitor and Analyse): each chart gets two
  `.u-axis-touch` areas over its Y axis columns (`touch-action: none`,
  placed by `placeAxisTouch`, re-placed on every `setSize`). A one-finger
  drag offsets the axis under it, a two-finger pinch zooms it - no hold any
  more. The old hold-then-drag lost to finger jitter on a real phone (the
  page's `pan-y` scroll took the move). The page still scrolls from the plot
  and the headers. **Please check on the S23/S10e**: in Monitor and in
  Analyse, drag a Y axis up and down, and pinch it.
- **Monitor's Y gestures** now find the axis column under the pointer
  (`axisKeyAt: axisColumnAt`, as Analyse) - before, they asked for a scale
  "y" the per-signal panes do not have, and did nothing.
- **Axis names**: hovering a Y axis (or a finger on it) shows `.u-axis-name`
  with the signal(s) it measures.
- **Highlight**: `.ph-flash` on a pane header readout, `.sig-flash` on a
  sidebar row (flashTrace / flashLiveSignal). The sidebar's `.sig-act`
  (●/○ acquire switch) is gone; the sidebar lists only `active` signals.
- **Review**: Combined gets the Strings strip (`mountCombinedSteps`) and
  step guides; Panes' charts move by a grip, the order kept in
  `localStorage` under `trial_order:<file>`.

---

## 2026-10-06 — a running I/O check keeps its PLC: switches refused while it runs (shared `app/` and `ui/`; show the 409's detail)

- **New 409s while an I/O check runs**, when the switch goes to another PLC
  (another protocol, host or port; a port left out is the protocol's own):
  `POST /api/connect`, `/api/welcome/connect`, `/api/sim/start`,
  `/api/sim/stop`, `/api/connections` with `connect: true`, and
  `/api/connections/connect`. The detail is *stop the I/O check before
  changing the connection*. `POST /api/profiles/load` answers *stop the I/O
  check before loading a profile* whatever the address. A reconnect to the
  same PLC passes, so does a check from before this build, and so does any
  switch when no check runs.
- **The session gains `source`** `{connection, endpoint}`: the connection and
  the address the driver dialled at Start. A file from before resumes with
  `{}`.
- **`POST /api/iocheck/start`** answers **409** during a switch. It also
  answers **409** when the connection changed (*the PLC connection changed
  while the DBs were listed*) or the link dropped (*the link to the PLC
  dropped while the DBs were listed - start again*) during the listing.
- **Over S7**, `/api/iocheck/start` with `search: "area"`,
  `/api/iocheck/scope`'s `area_error` and `POST /api/iocheck/areas` answer
  *the PLC's block list is still loading - try again in a moment* until the
  block list is complete.
- **The page's S7 / OPC UA tabs** refuse with the same words before asking
  anything while a check runs.
- **Do:** if the phone shell switches connections on its own, expect these
  409s while a check runs and show the detail. The boot test is unaffected:
  it connects before it starts its check.

---

## 2026-10-06 — the S7 driver's stop no longer hangs on Python 3.11; the slim landscape bar (shared `app/` and `ui/`; nothing to do)

All of it reaches the phone with the next payload:

- **S7, Python 3.11**: every request deadline in `app/drivers/s7plus/driver.py`
  (each read, the connect, the goodbye) is `asyncio.timeout` now, not
  `asyncio.wait_for` - on 3.11 the latter drops a cancel that lands in the
  same loop turn as the answer, so `stop()` waited for ever with the source
  lock held (every switch and start behind it). Reproduced 5 of 5 on CPython
  3.11.16 before, cancelled 5 of 5 after; an unanswered request still raises
  `TimeoutError("no answer from the PLC in ... s")`. `stop()` also cancels
  until the task is done. The same race as the OPC UA watchdog's (entry
  below). Test: `tests/py/test_s7_cancel_race.py`.
- **Phone, landscape**: `#topbar` is 44 px (+ safe area) sideways, as the
  landscape block in `css/120-phone.css` always meant - the safe-area rule
  after it set 56 px and won.
- **Picker rows**: the `.proto` chip has `order: 2`, after the name.
- `tests/py/test_bug_hunt_config.py` reads the model's fields the pydantic-1
  way too (`__fields__`), so it passes on the phone stack.

---

## 2026-10-05, night — one name per tag in the CSV and on the watch (shared `app/`; nothing to do)

- **`GET /api/watch`**: the `iocheck` block's `last.name` for an S7 area tag
  reads `FAT_NH`, not `IArea.FAT_NH`. `last.node_id` (what OK / Not OK post)
  and `last.address` (`%I12.3`) are unchanged, and no key changed.
- **The CSV's** *name* and *together with* columns, and the member names in
  *found in the DB*, drop the `IArea.` / `QArea.` / `MArea.` prefix. The
  *address* column keeps the full node path. `app/iocheck/rules.py
  display_name()` does it, as the page's `iocLabel` does.

---

## 2026-10-05, night — the Inputs tab's Left and Right: two browsers, references on the right, one label per tag (shared `app/` and `ui/`; do: ship `js/287-iocheck-tree.js`, drop the old Inputs ids)

**The page:**
- **New script `ui/js/287-iocheck-tree.js`**, loaded between 286 and 288.
  **Do:** add it wherever the phone lists the page's files. The boot test's
  284/155 checks are unchanged.
- **New `#ioinputs-view` markup.**
  - Gone: `ioi-q`, `ioi-search`, `ioi-hits`, `ioi-hits-count`, `ioi-add`,
    `ioi-add-db`, `ioi-to-db`, `ioi-to-io`, `ioi-db-cabinet-set`.
  - New: `ioi-sides`, `ioi-left`, `ioi-right`, and per side
    `ioi-{l,r}-{title,show,count,q,clear,view,results,browse,tree,foot,chosen}`,
    plus `ioi-l-all` and `ioi-r-every`.
  - Kept: `ioi-cabinet`, `ioi-list`, `ioi-list-title`, `ioi-cabinet-set`,
    `ioi-remove`, `ioi-dblist`, `ioi-dblist-title`, `ioi-db-remove`.
  - `data-side="l|r"` sits on the view. The Check toolbar gains
    `#ioc-start-note`.
  - **Do:** update anything of yours that reached for the old ids.
- **The module names `ioinputs` and `iocheck` are unchanged** (markModule),
  so the shell and the watch follow as before.
- **The phone shows one side at a time.** The side is remembered in
  localStorage `ioi-side`; the lists start closed; rows are 40 px with a
  20 px box; the node-id, type and protocol chips are hidden. Checked at
  360 px with no sideways scroll.
- **localStorage:** new keys `ioi-show-l`, `ioi-show-r` and `ioi-side`.
  `ioc-search` is no longer used.
- **Labels:** an S7 area tag shows as `FAT_NH` plus `%I12.3`. No key or node
  id changed.
- **`080-shortcuts.js`:** outside the analyzer suite only `c`, `o` and `i`
  act on a hardware keyboard.

**The server:**
- **`GET /api/iocheck/inputs` and the `iocheck_inputs` frame** gain `rev`
  (counted up on every change, starting from the boot time in ms; keep the
  highest), `areas` (the right's references), `area_total`, `area_capped` and
  `connection` (the active connection's name). `inputs` is the left only.
  `db_count` is now `len(areas)`, and `db_cabinets` comes from the
  references' `group`. `cabinets` is unchanged.
- **`POST /api/iocheck/inputs`** `{items:[{node_id,path,expand}], cabinet,
  rate_ms, side}`:
  - `side: "db"` answers **422** *this page is older than the laptop's BEGIA -
    reload it*.
  - `expand` rows carry `via` (the container's id).
  - The list holds at most 1000 rows after expansion; over that, **422** with
    the advice.
  - More than one item is read with `describe()`, one with `probe()`. Rows
    store `area` and `abs_address`; an S7 row is named by its node id.
  - New skip reasons: *already on the right*, *nothing inside it to check*.
  - It no longer resubscribes or makes buffers.
- **`POST /api/iocheck/inputs/remove`** answers **409** *<name> is in the
  running check - stop it first*. A container's id removes the rows ticked
  through it.
- **`/api/iocheck/inputs/side`** answers **410** with the reload sentence, for
  any method.
- **New `POST /api/iocheck/areas`** `{items:[{node_id,path}]}` returns
  `{added, skipped:[{node_id,why}], absorbed:[{node_id,name,into}], total,
  capped, ...payload}`, or 409 when the source moved or the link dropped.
- **New `POST /api/iocheck/areas/remove`** `{node_ids}`: the payload, plus
  `note` while a check runs. **404** when none of them is ticked.
- **New `POST /api/iocheck/areas/covered`** `{node_ids}` (at most 600):
  `{covered:{id: ref_id}, rev}`. It reads nothing from the PLC and is open in
  a read-only copy.
- **`GET /api/iocheck/scope`**: `area` is `{members, blocks, capped, refs}`
  from the references, and `area_error` is new. Over S7 there is a `batch:
  32`. `every` includes %M.
- **`POST /api/iocheck/start`**: the body is unchanged. `"area"` means the
  references, with `with_group` filtering them by `group`; over 5000 members
  it answers **422** *the ticked DBs hold more than 5000 members - untick some
  on the right*. Left rows are never candidates. A row's `area` is filled in
  at Start.
- **`config.json`**: `iocheck_areas: [AreaRef]` is new on the config and on
  every connection. Old `side: "db"` rows become references (`kind:
  "member"`, `count: 1`, cabinet kept) at start, on a profile load and as a
  connection's lists come on. An old file still loads, and unknown keys
  survive. A connection switch carries the references with the rows.
- **Do:** nothing for `tools/boot_test.py` (POST /inputs side io, then start
  every). Its scenario passes in-process on the phone's stack
  (`tests/py/test_iocheck_sim.py`).

---

## 2026-10-05, late — the OPC UA driver's stop no longer hangs on Python 3.11 (shared `app/`; nothing to do)

- **`OpcUaDriver.stop()` could wait for ever on the phone's Python 3.11.**
  It hung when a `request_resubscribe()` landed in the same turn of the event
  loop as the stop's cancel. 3.11's `asyncio.wait_for` returned normally and
  swallowed the cancel, and the watchdog ran on. The watchdog now waits with
  `asyncio.timeout`, which is right on 3.11 and later.
  `tests/py/test_opcua_watch_cancel.py` drives that exact turn, and it fails
  on 3.11 with the old code. The laptop's 3.14 was never affected.
- **Still open, in the S7 driver:** it uses `asyncio.wait_for` inside its
  poll loop (`driver.py`, the request wrapper), so a stop that lands as a
  read completes could in principle be swallowed the same way on 3.11.
  Reported to the desktop session; not changed here.

---

## 2026-10-05, late — the I/O check reads a row by where it lives: "value" rows, outputs, "moves on its own", rows read only during a check, a trial mark at Start and Stop (shared `app/` and `ui/`; nothing to do for the boot test)

All of it reaches the phone with the next payload. No route, body or frame key is removed or renamed.

- **Rows** (`session.items[]` in `GET /api/iocheck` and in `iocheck` frames)
  gain `area` (`in`, `out`, `mem`, `db`, `other`, or `""` for a row from
  before), `rate_ms`, `lo`, `hi` and `noisy`.
  - `kind` can be `"value"`: a number that is not a raw input word (a %QW, a
    %M word, a DB REAL).
  - A value row's `edges` are `[t, value, up]`, three elements like a DB
    member's. A bool's are `[t, 0|1]` and an analog row's are
    `[t, word, connected, up]`, as before.
  - A file from before resumes with `area: ""`, `noisy: false`, `lo`/`hi`
    null and `rate_ms: 10`.
  - **Do:** allow the new keys if anything parses frames strictly.
- **`config.json`:** `SignalCfg` gains `area` and `via`, both `""` by
  default. The next step fills them in. An old file loads as before, and an
  unknown key still survives a save.
- **The Inputs list is read only while a check runs, and only the rows in
  it.**
  - `AppConfig.polled()` is the signal set alone.
  - `IoCheck.watched()` adds the running check's rows at their own `rate_ms`
    (10), and its DB members at 100 ms, the DB area's included.
  - Start empties a row's old buffer, so its baseline is its first good
    reading after Start. Stop drops the buffers nothing else reads. A check
    resumed at boot reads its rows again.
  - **Do:** nothing for the boot test: it waits for `learn_until + 500`
    before its first press. Its scenario also runs in-process on the
    simulator, on the phone's stack, in `tests/py/test_iocheck_sim.py`.
    Anything that presses straight after Start must first wait for a reading
    (`baseline` not null).
- **An output's member may move first.** For a row with `area: "out"` the
  mapping looks either side, ±500 ms. So `lag_ms` and `lags` can be negative
  (the member moved first), and so can the CSV's *lag ms*.
- **`noisy` means the row moves on its own.** A row whose area is `mem`,
  `db` or `other` and that moves in the first 3 s of a check, or of its
  *Again*, gets `noisy: true`.
  - It stays in an `iocheck` frame's `rows` but **never appears in its
    `edges`**, so there is nothing to announce.
  - It has no entry in `mapping`, never joins `together` and never explains
    a member.
  - The CSV's *found in the DB* reads *moves on its own*.
- **`GET /api/watch`:** the `iocheck` block's keys are unchanged. `last`
  never picks a noisy row, which would otherwise hold the wrist for good. A
  value row's `value` is a number written like the pane header (`27648`,
  `160.4`).
- **A recording trial gets a mark** when a check's Start or Stop changes what
  the driver reads: *I/O check started - the PLC subscriptions were rebuilt,
  a gap of up to N ms*, or *I/O check stopped - ...*.
  - It also goes to the pages as an `event` frame and counts in the watch's
    `marks`.
  - Over OPC UA, N is 1000 plus the slowest recorded interval. Over S7 it is
    that interval, plus 3000 when a block has to be read first.
- **`session.aside`** (at most 200 members set aside) is sorted by name, as
  the ready panel lists them. It now holds the alphabetically first 200.
- **`ui/` (280, 284, 286):** a value row reads *Input · value*, *the PLC saw
  it change*, *QW_Speed → 27648*, *went up at …* and *3 changes*. Its
  was/now cells are wide like an analog row's. The popup's stack of rows
  still to be marked, and the panel before the first press, ignore noisy
  rows.

---

## 2026-10-05, night — browse and search take `show`; "every" takes %M; the simulator's Inputs and Outputs folders (shared `app/` and `ui/`; nothing to do yet)

All of it reaches the phone with the next payload. A page that does not ask for the new parameter gets the same answers as before.

- **`GET /api/browse?node=&show=all|io|rest`** and
  **`GET /api/search?q=&limit=&show=`**.
  - `all` is the default and unchanged. `io` keeps %I and %Q. `rest` keeps the
    data blocks, %M, the timers and counters.
  - Any other value, an empty one included, answers **422** "show must be
    all, io or rest".
  - S7 root rows and every search hit carry `area` (`in`, `out`, `mem`, `db`,
    `other`). S7 tag rows carry `type` and `abs` (`%I12.3`, or `""` for a DB
    member), and S7 tag hits carry `abs`.
  - A filtered OPC UA root lists each PLC's own folders (Inputs and Outputs,
    or Memory, Timers, Counters and the two DB folders). Each row has a
    `path` (`PLC_1.Inputs`), and with two PLCs it reads `PLC_1 › Inputs`. A
    server without the SIMATIC folders gives `[]` for `io` and its whole root
    for `rest`.
  - A filtered search counts `total` after the filter.
- **An S7 search that starts with `%` is an address.** `%I12` finds
  `%I12.0`–`%I12.7`, never `%I120.0`.
- **"Every DB" includes %M**, after the data blocks, filed under the block
  `"%M"` (never `MArea`). That holds on S7 and for an OPC UA server's Memory
  folder. `/api/iocheck/scope`'s `every` counts can grow.
- **The simulator.** The cabinet's `DI_01..16` and `AI_01..04` now sit in
  **SIM_EAF > Inputs**, with their node ids unchanged
  (`ns=3;s="DB_Cabinet"."DI_01"`), so saved lists, the press route and the
  strip work as before. New: `FB_Cabinet.cmd_01..04` and **SIM_EAF > Outputs >
  DO_01..04** (`ns=3;s=DO_01`), which follow their command one cycle
  (100 ms) later; `tools/sim_cabinet.py --command N` sets a command. "Every
  DB" on the simulator no longer lists the cabinet's inputs, and it gains
  the four commands. **Do:** the boot test's I/O check scenario is unchanged;
  only the member count it prints moves.
- **Driver calls the next I/O check routes will use** (no route uses them
  yet): `db_members(cap, roots=[...])` with `per_root`,
  `area_covers(refs, ids)` and `describe(ids)`.
- **`ui/js/040-picker.js`**: the row functions take an owner object, the
  analyzer's `PICK` by default, and the analyzer's requests are unchanged.
  New helpers: `browseUrl`, `searchUrl`, `drawHits`, `searchFootText`.
- The guards `_probe_source`, `_refuse_mid_switch` and
  `_refuse_if_source_moved` now live in `app/routes/common.py`, and
  `routes/signals.py` re-exports them.

---

## 2026-10-05, evening — the I/O check as the canvas draws it: on the phone the popup is the Check screen (shared `ui/`, `app/iocheck/`; look at it on the phone)

All of it reaches the phone with the next payload. There is no new script and no new route.

- **The popup is a sheet on the phone** (`ui/css/155-iocheck-popup.css`). It
  is fixed between the top bar and the bottom tabs, 56 px each way upright,
  with the tabs at 40 px on its side, plus `env(safe-area-inset-*)`. On its
  side the top bar measures 56 px too, because the safe-area rule in
  `120-phone.css` outranks its 44 px landscape rule. A card holds the input
  over the member, the search in one line, and the earlier stack. The marks
  sit under it: the suggested one full width at 56 px, the others at 52 px,
  held at the foot of the sheet when the card is long. Small mode (−) is a
  floating card over the table. **Do:** if the shell changes the bar or tab
  heights, or starts drawing under the status bar, the sheet's insets must
  follow. Check it once on the phone, upright and on its side.
- **The I/O module hides `#time-controls`** in the phone's top bar. The
  Inputs tab's icon is a framed list (`120-phone.css`,
  `.mod[data-mod="ioinputs"]`).
- **The module switch** (`#suite`, the AN | I/O pair) is `role="group"`, with
  `type="button"` and `aria-pressed` on both buttons, and `setSuite` keeps
  `aria-pressed` current. Nothing to do unless the shell finds it by role.
  The module names `iocheck` and `ioinputs` are unchanged.
- **Frames:** the session's `search`, in `iocheck` frames, gains `learn_ms`
  and `block_names` (the first 24 DB names, sorted). A file from before
  resumes with `0` and `[]`. **Do:** allow both if anything parses frames
  with a strict schema.
- **Marks:** *OK all found*, and *Skip* on two inputs pressed together, post
  `POST /api/iocheck/mark` once per input, one after another, with the same
  body as before.

---

## 2026-10-05, later — the S7 / OPC UA tabs switch the connection; a `connections` frame after every switch (shared `app/` and `ui/`; nothing to do)

All of it reaches the phone with the next payload:

- **The S7 / OPC UA tabs** now sit in the picker's search bar, the I/O
  check's search bar and the Signals toolbar, and they **switch the
  connection**: `POST /api/connections/connect` to this PLC's saved
  connection over the other protocol (same host), or `POST /api/connections`
  with `connect: true` to save one for the same host first. Both after a
  confirm. The Signals table's protocol *filter* from the entry below is
  gone - the list is always the active connection's own.
- **Every switch sends a `connections` frame** (after the source lock is let
  go): `/api/connections/connect`, `/api/welcome/connect` and
  save-and-connect. Only a save did before, so a page kept marking the old
  connection as live. The page that asked also applies the answer at once.
- **A page event**, `begia-source-changed` on `window`, when the status's
  protocol or endpoint changes: the picker's search and the I/O check's
  hits are cleared, as an address found over one protocol is none over the
  other.
- The strip over the charts is called **Strings** (the owner's word).
- **A connect to a PLC switches the simulator off** (`/api/connect` to a
  non-simulator address, and every switch through a saved connection):
  `sim.enabled` false, `sim.prev_endpoint` empty, its server stopped - a
  restart comes back on the PLC, not the simulator. `GET /api/sim/cabinet`
  answers `running: false` unless the driver is reading the simulator.

---

## 2026-10-05 — step texts on the time axis, S7 strings, types, protocol tabs (shared `app/` and `ui/`; run the boot test)

All of it reaches the phone with the next payload. What it can see:

- **A new page script**, `ui/js/185-steps.js`, loaded from `index.html`
  right after `180-panes.js`. `tools/boot_test.py` reads the list from
  `index.html`; run it on the payload.
- **Frames**: `init` gains `text_backfill` - `{node_id: [[t_ms, text, good],
  ...]}`, what each text signal said over the backfill's look backwards,
  oldest first, the change in force when it begins included (capped at
  20 000 changes across all signals). `data` frames gain `text_log` - every
  change since the last push, same shape - beside `texts` (still the latest
  only). The shared page draws them; nothing else needs to read them.
- **The page**: a *Strings* strip (lanes, like the digital ones) over the
  charts when there are text signals; on a phone its header is just its
  name and *in charts*, as for a digital strip. Step lines through the
  charts and step rows in their readout. The Signals table has S7 / OPC UA /
  All tabs, on their own row on a phone. A trial's digital lanes draw in
  Review (they were -2 px high).
- **Routes**: `GET /api/diag/texts` (gated like every other route) - what
  the S7 CPU last answered for each text tag. The block, import and preset
  adds store `dtype`; `GET /api/signals` may save the configuration once
  when the driver reports types it did not have.
- **S7**: a text tag is read in a request of its own, at most every 100 ms.
  The hub never stamps a text change earlier than the one before it.

---

## 2026-10-04 — the bug hunt's remaining defects (shared `app/` and `ui/`; one thing done on your side)

All of it reaches the phone with the next payload. What it can see:

- **Configuration**: the stored models keep unknown keys (a `Stored` base,
  `class Config: extra = "allow"` on v1) - your `keep_unknown_check.py`
  passed 15/15 on the phone venv. `app.main` can raise
  `app.config.StartupError` at import when the data folder cannot be made or
  written, or config.json cannot be opened; your `2662a6f` (boot.py, not yet
  pushed) treats it as the data folder's problem, not the payload's.
- **Events**: the recorder writes "recording degraded" / "samples lost" /
  "recording resumed" itself (resumed after 5 s of landed flushes), and
  every mark written to a running trial is sent as `{"type": "event"}`;
  the `init` frame gains `recording_events` (the running trial's marks from
  its file). The shared page handles both.
- **Exports**: CSV preamble lines are raw `#` lines; a quote, apostrophe or
  line break as the separator is a 422; the zone line is reworded.
- **S7**: more types decode (S5TIME, DATE_AND_TIME, LDT, TIME_OF_DAY,
  WCHAR, LTOD); a type that cannot is listed in `unsubscribed` and refused
  by the Start checks.
- **Routes**: PUT `/api/trigger` drops unknown body keys and merges into the
  stored trigger; GET `/api/trials` runs off the event loop.

---

## 2026-10-04 — trials start only on evidence they can stand behind (shared `app/` and `ui/`; nothing to do)

The phone runs the same `app/` from the payload, so all of this reaches it
with the next one. What it can see:

- **The trigger frame** gains `refused` (the reason a fire did not start a
  trial, `""` when none) and `refused_ms`. The shared page toasts each
  refusal once and keeps the reason under the chip while armed. A trigger
  fire now passes the Start button's checks (`start_checks()` in
  `app/routes/recording.py`): every signal missing, a refused signal, under
  100 MB free, not connected - including a UDP trigger while the PLC link
  is down, which used to start a trial of flat PLC columns.
- **New 409s.** `POST /api/trial/start` while the PLC source is being
  changed (*the PLC connection is being changed - start the trial when it is
  done*); the signal adds (`/api/signals`, `/bulk`, `/import`, `/preset`,
  `/repoint`) and `POST /api/iocheck/inputs` when a switch is under way or
  the source changed (or a trial started) while they probed - nothing is
  added, retry. A negative pre-trigger with no PLC now answers 422 before
  the 409.
- **423 from a refused copy**: a BEGIA that is the second copy on its data
  folder answers 423 with a sentence to every non-GET `/api/*` except
  `/api/login`, `/api/logout`, `/api/path/check` and `/api/welcome/skip`.
  On the phone that only happens if the shell ever runs two recorders on
  one `TRIALREC_DATA_DIR`. `config_warnings()` can carry a `LOCK_WARNING`
  where the folder cannot be locked at all.
- **The Signals table at 320 px**: under 400 px the pane select gets the
  width left after the tick, the address and the delete button, and the
  add-signals row wraps (`ui/css/120-phone.css`) - the overflow you measured.
- **`/api/watch`** carries the `iocheck` block when a check exists (your
  `1ab0a7c`), and discovery answers again (your `f672aab`); both are on main
  under this entry and in the next exe and payload.

---

## 2026-10-03 — the I/O check finds the DB member by its pattern; the CPU's %I address (shared `ui/`, `app/`; the payload and a config field)

- **The popup is new** (`ui/js/284-iocheck-popup.js`, `ui/css/155-iocheck-popup.css`): an
  input that moves opens a panel that stays until the next one - the input's
  name, symbol and CPU address, what it did, the DB member **found** for it
  (no list, no pairing: every DB is watched and what moves with both edges
  of the input is named), the lag, the search in three numbers, the
  evidence lanes, and the marks. On the phone (`data-layout="phone"`) it is
  the full width above the bottom tabs and the marks are a sticky row under
  the thumb (52 px). Enter / Esc are wired for the laptop only (a hardware
  keyboard); nothing there for a phone to do.
- **One edge is not a pattern**: after the first edge the popup waits for
  the edge back and names nobody; a member is named only after it followed a
  rising and a falling edge.
- **New route** `GET /api/iocheck/scope`; `POST /api/iocheck/start` takes
  `search: every|area|none` (`with_group` alone still works and means
  `area`). Socket frames of type `iocheck` carry `search` and `aside` beside
  `mapping` when the search moves; `session.members` in a full frame is only
  the members still searched that moved - the file keeps them all.
- **Config:** `SignalCfg.abs_address` (string, default `""`) - the absolute
  address the CPU gives over S7comm-plus (`%I12.3`). Old configs load without
  it. **Do:** if the phone shell parses `config.json` or the signal payload
  with a strict schema, allow the field; nothing else changes.
- **The module is restructured** the way BEGIA was: `ui/js/280-iocheck.js`
  is five scripts, `280-iocheck-words.js`, `282-iocheck-state.js`,
  `284-iocheck-popup.js`, `286-iocheck-check.js`, `288-iocheck-inputs.js`,
  loaded last from `index.html`; the popup's styles are
  `ui/css/155-iocheck-popup.css`, linked after `150-iocheck.css` in
  `index.html` **and `boot.html`**. On the server `app/iocheck.py` is the
  package `app/iocheck/` and its routes are three modules - nothing the phone
  calls changed. **Do:** nothing if the payload carries `ui/` whole (it does:
  `tools/make_payload.py` walks the folders); if anything of yours names
  `280-iocheck.js`, name the five.

## 2026-10-03 — stopping a trial runs off the event loop (shared `app/` and `ui/`; nothing to do)

The phone runs the same recorder from the payload, so this reaches it with
the next one. What changed at the edges:

- The `recording` frame carries `stopping` (bool): true from the moment Stop
  is pressed until the file is sealed, with `active` still the trial. The
  `init` frame carries `recording_stopping` for a page that connects
  meanwhile. The shared page shows *Stopping…* and disables Stop, Mark and
  the quick marks; nothing in the shell needs to change.
- `POST /api/trial/stop` answers when the trial is sealed - seconds on a
  long trial - but the service keeps streaming meanwhile. Three new 409s:
  a second stop (*the trial is already being stopped*), a mark
  (*the trial is being stopped*) and a start (*the last trial is still being
  stopped - try again in a moment*) while one runs.
- The final `recording` frame (active null) now comes from the recorder's
  after-stop hook, for every stop - the operator's, the trigger's, the
  shutdown's - so it arrives once per stop.
- A trial the stop could not fold its -wal into (something held the file
  open for the whole wait) is stopped but left unsealed, and the stop
  record says why in `seal_error`.

## 2026-10-03 — `ui/style.css` is `ui/css/*.css` (shared `ui/`; nothing to do unless you name the file)

- The stylesheet is 15 files, `ui/css/010-base.css` … `150-iocheck.css` (the
  I/O check's blocks, gathered; they shared no selector with what sat
  between them), linked
  in that order from `index.html` **and `boot.html`** (the boot page draws the
  splash with them). Cut at the sheet's own banners, nothing moved: one
  cascade, a later file wins a tie as a later rule did. The payload carries
  the folder; `OwnUi.response` serves any path under the slot's `ui/`.
- If anything of yours names `ui/style.css` (a grep, a test, a doc), it means
  `ui/css/*.css` now; the desktop tests read it through
  `require("./lib/ui").cssSource()` (joined in `index.html`'s order).
- A rule you add goes in the file it belongs to, but never before a rule it
  must beat: `100-ui-pass` and `140-late` are late on purpose.

## 2026-10-01 — the I/O check tab (on main since 2026-10-03; shared `ui/`, new routes)

A loop-check list for digital inputs: the ticked bool signals are scanned for
edges from the moment the check starts, each change is named as it arrives,
rows get OK / not OK / skipped. `docs/IOCHECK.md` is the brief.
It is on main now (merged 2026-10-03, after the restructure below), so this
reaches you with the next payload:

- A new tab, `data-mod="iocheck"`, between Signals and Setup, **shown on the
  phone too** (the field end is who needs it); its phone tab icon sits with
  the others in `style.css`. `markModule` sets `data-mod="iocheck"` on the
  root like any module; the view `#iocheck-view` replaces the chart area the
  way Signals does.
- A new file `ui/js/280-iocheck.js`, the last of the page's scripts (after
  `js/270-boot.js`; it is in the `<script>` list like the others). It is in
  the payload if the payload carries all of `ui/` (`tools/make_payload.py`
  zips the directory); say if your shell lists files by name instead.
- New routes `GET /api/iocheck`, `POST /api/iocheck/{start,stop,mark,reset}`,
  `GET /api/iocheck/export.csv`, all behind sign-in and licence - the
  phone's own BEGIA is loopback and never asked. A new socket frame
  `type: "iocheck"` (and `iocheck_inputs`), handed by `js/020-api.js` to
  `js/280-iocheck.js` as a `begia-iocheck` (`begia-iocheck-inputs`) window
  event. Later entries on the branch added the module switch left of the
  eye (`#suite`; the root carries `data-suite`), the Inputs tab
  (`data-mod="ioinputs"`, `#ioinputs-view`), the routes under
  `/api/iocheck/inputs*` and `/api/sim/cabinet*` - all in `docs/IOCHECK.md`.
- On a change the page calls `navigator.vibrate(120)`, guarded: without the
  VIBRATE permission in the shell nothing happens. Add it if you want the
  buzz in the field end's hand.
## 2026-10-03 — the restructure: `app/` is packages, `ui/app.js` is `ui/js/*.js` (shared `app/` and `ui/`; one thing to do)

- `ui/app.js` is gone. The page's code is `ui/js/010-core.js` … `270-boot.js`,
  classic scripts `index.html` loads in order (the number is the order; one
  global scope; the split cuts at the section banners, nothing moved, two
  spots guarded for the load order). The payload carries the folder as it
  carries `ui/` (`tools/make_payload.py` walks it), and `OwnUi.response`
  serves any path under the slot's `ui/`, so companion mode draws the new
  page unchanged.
- **Do:** `tools/boot_test.py` asserts `b"app.js" in page`; make it
  `b"js/010-core.js"`. *Done for you on a branch:* `restructure/desktop-ui-split`
  (`ff810b9`) in Begia-Mobile has that fix plus the comments in
  `MainActivity.kt`, `OwnUi.kt`, both `colors.xml` and `design/s23` that
  named `ui/app.js` / `ui/style.css`; your 27 tests pass on it. Comments and
  the test only, no Kotlin code - merge it when you next build. Anything else of yours that names `ui/app.js` (a
  grep, a doc, a test) means `ui/js/*.js` now. The desktop JS tests read
  the scripts through `tests/lib/ui.js` (`appSource()` joins them in
  `index.html`'s order) if you want the same.
- `app/` is packages now (phases 1-4 of `docs/RESTRUCTURE-PLAN.md`):
  `app/recording/`, `app/drivers/` (`opcua.py`, `s7plus/`, `udp.py`),
  `app/access/`, `app/net/`, `app/sim/`, and `main.py` split into
  `state.py`, `push.py`, `gates.py`, `serve.py` and `routes/*`. Every old
  name (`app.recorder`, `app.s7plus`, `app.driver`, `app.licence`,
  `app.discover`, `app.main.X` …) still imports and forwards reads *and*
  writes, so the phone's `app.main` boot and anything that patches through
  an old name keep working. The payload ships the packages as bytecode as
  before; `app/main.pyc` is still the entry. The map: `docs/ARCHITECTURE.md`.

## 2026-10-03 — protocol badges and protocol-specific login fields (shared `ui/`; nothing to do)

- `_signals_payload` carries `proto` (`s7plus` | `opcua` | `udp`, from the
  node id) and the status frame carries `protocol` (the driver that is on);
  the shared page shows S7 / UA / UDP beside every signal (`protoOf`,
  `protoChip`, `.proto` styles).
- The Setup form (`#cv-user-row`, `#cv-pass-label`) and the welcome door
  hide the User field for S7 and call the password *PLC password*; the S7
  driver sends a legitimation only when a password is set. If your shell
  fills the welcome door itself, follow the same rule.

---

## 2026-10-02 — `POST /api/path/check` and a *Check the path* button in Setup (shared `ui/`; nothing to do)

`app/pathcheck.py`: the steps to the PLC - route, ping (advisory), port,
session, and for S7 the type-information read - as one JSON answer
`{"ok", "protocol", "host", "port", "took_ms", "steps": [{"step", "ok":
true|false|null, "took_ms", "text", "hint"}]}`, stopping at the first hard
failure. Behind sign-in and licence. The shared Setup form (`#cv-check`,
`#cv-path`, `pathCheckLines`) posts the typed address and login; the phone
runs it against its own path to the PLC like the laptop does (the ping
step shells out to `ping`, which exists on Android; if it does not on a
build, the step says "ping could not be run here" and the check goes on).

---

## 2026-10-02 — the laptop answers "BEGIA?" on UDP 4858: build the phone half

**Your side to build:** a *Find the laptop* action on the Setup screen (and,
if you like, a quiet search when the screen opens). The laptop side is on
main (`app/discover.py`, started from the lifespan; `AppConfig.discovery`,
default true).

The protocol, one datagram each way:

- The phone sends the six bytes `BEGIA?` (or `{"begia":"probe"}`) by UDP to
  port **4858**, to the subnet broadcast address (and/or 255.255.255.255;
  on Android, `WifiManager.MulticastLock` is not needed for a broadcast
  *send*, but some phones need it to *receive* the unicast reply - test on
  the plant phone). Collect replies for about a second; send again if none.
- Every BEGIA that hears it answers the sender, unicast, with one JSON
  datagram:

      {"begia": 1, "name": "FAT-LAPTOP", "build": "v0.9-82-g...",
       "https": ["https://10.6.70.120:8443", "https://192.168.70.135:8443"],
       "port": 8443, "licensed": true, "auth": "set" | "unset"}

  `https` is the same list the Options page shows (every non-loopback
  address, HTTPS port included); prefer the one on the phone's own subnet.
  `licensed` false means the exe's door is up (it still answers); `auth`
  "unset" means no app password is set yet - say so before the operator
  tries to sign in, since nothing from the network gets in until it is.
- The reply's source address is the laptop; `https` carries it already, so
  nothing needs resolving.

`GET /api/hosting` (signed in) carries `discovery` (bool) and
`discovery_port`, for the Options page. `tests/test_discover.py` has a
real-socket round trip you can mirror. Nothing else moved.

---

## 2026-10-02 — text signals are recorded into trials (`trial_data` payload, CSV, shared `ui/` viewer)

Follow-up to the Text category: a trial now records its text signals, so
`/api/trial/start` no longer leaves them out, and:

- The trial file has a `texts (signal_id, t_ms, value, good)` table and
  `signals.is_text`; `Recorder.flush` writes a row per change from the hub's
  `text_log` (`hub.texts_since`), and the start writes the value the string
  had then.
- `GET /api/trials/{fname}/data` carries `texts: {signal_id: [[t_ms, value,
  good], ...]}` and `signals[].is_text`; a text signal has no `series` and
  no `stats` entry. A trial from before has `texts: {}`.
- The CSV has one column per text signal after the numeric ones (header =
  the name, no unit), last-value-hold, empty while the read was bad; the
  `# signal:` preamble line says `is_text=1`.
- Shared `ui/app.js`: the viewer draws no pane for a text signal, marks its
  changes on the charts (`textChanges`, also in the trial analysis), counts
  them in the info line (`textNote`), and the report has a "Text signals"
  table (`REPORT_T.*.texts/value`). If your shell renders trial data itself,
  skip `is_text` signals when drawing.

`tests/test_text_record.py`, `tests/test_text_record.js`.

---

## 2026-10-01 — the licence: a grace period, `GET /api/licence/request`, a door button (nothing to do)

The phone is not licensed, so none of this reaches it in practice; for
completeness, since the routes and the shared `ui/` moved:

- `GET /api/licence/request` (open, like `/api/licence`) answers a JSON
  download `licence-request-<code>.json` - the machine code, the computer's
  name, the build; nothing secret. The licence door (`#lic-request`, shared
  `index.html`) links to it.
- `app/licence.py`: an expired licence verifies for `GRACE_DAYS` (14) more
  days; `status()` then carries `grace_days_left` and a `warning` with the
  day it stops. The 402/4402 gate is unchanged otherwise.
- `tools/licence.py`: `issue --request FILE`, `backup`, `restore`.

---

## 2026-10-01 — a stopped trial's CSV goes into Trials_DDMMYY (shared `ui/`, a new socket frame)

Every stop - `/api/trial/stop`, the trigger, the shutdown - now writes the
trial's CSV (and the `.dat` where iba's library exists, never on the phone)
into `<base>/Trials_DDMMYY/` (`app/recorder.py` `dated_export_dir`,
`auto_export`; `Recorder.on_stopped` is the hook), in a worker thread after
the stop has answered, and then sends one socket frame:

    {"type": "exported", "file": "<trial>.db", "dir": "<abs path>",
     "folder": "Trials_011026", "csv": "<name>.csv" | null, "dat": ... | null,
     "errors": [..]}

The shared `ui/app.js` toasts it (`exportedText`). On the phone the base is
the app's own data directory, so the folder lands there; if your shell lists
or shares trial files, the CSV is now in `Trials_DDMMYY/`, not only under
`trials/exports/` on demand. `AppConfig.auto_export` (default true) switches
it off. `tests/test_auto_export.py`, `tests/test_auto_export.js`.

---

## 2026-10-01 — Text signals: STRING tags are read and shown (shared `ui/`, `data`/`init` frames, `SignalCfg`)

A third signal category, Text, for STRING/WSTRING (S7) and String (OPC UA)
tags; in your payload if the phone acquires. Nothing to do unless your shell
reads `signals` or the socket frames itself:

- `SignalCfg` carries `is_text` (bool). A text signal has `pane: -1`, is
  never charted, never recorded (`/api/record/start` leaves it out), never
  a trigger signal. Anything that filters on `!is_bool` to mean "analog"
  must also exclude `is_text`.
- The `init` frame carries `texts: {node_id: [changed_ms, text, good]}` for
  every text signal with a value; the `data` frame carries the same map for
  the ones that changed since the last frame, beside `series` (which is now
  always present, possibly `{}`). A text is sent on change only: a string
  that does not change is not stale.
- `POST /api/signals` (and bulk, import, preset, re-point) accept a string
  tag now - the probe answers `is_text: true` and the value as a string -
  where it used to answer 422 "not a recordable scalar".
- Shared `ui/`: a `Text` button in `#sig-kind`, `#text-panel` above the panes
  in `#chart-scroll` (built by `renderTextPanel`), a `Text` tile in the
  sidebar for the text rows (no swatch, no L/R, not draggable).
- The simulator has `"DB_SlagDoor"."Paso_Texto"` (follows the step text) and
  `"DB_SlagDoor"."Receta"` (constant), for trying it.

`tests/test_text_signals.py`, `tests/test_text_signals.js`.

---

## 2026-10-01 — the payload share: 15 or 30 minutes, or until BEGIA closes (`/api/payload/*`, shared `ui/`)

The laptop's Options → *Phones on the WiFi* row now asks how long to share:
15 min, 30 min, or until BEGIA closes. What reaches you:

- `GET /api/payload/info` carries `until_closed` (bool). While it is true,
  `shared` is true and `shared_until` is **null** - there is no time to give.
  Until now `shared_until` was null only when nothing was shared, so if your
  shell reads it, test `shared` for "is it shared", never `shared_until`.
- `POST /api/payload/share` takes `{"until_closed": true}` as well as
  `{"minutes": n}` (still 1 to 120); its answer carries `until_closed`, and
  `seconds` is null for the open-ended share. Still loopback only.
- The code and the five-wrong rule hold for an open-ended window as for a
  timed one. The window lives in the laptop's memory: when BEGIA closes there,
  it is gone.
- The 403 for a fetch with nothing shared now says "Options > Phones on the
  WiFi > Share" (it said "Options > Share the payload with phones") - the row
  and the button as they read on the laptop. If you show the server's
  `detail`, nothing to do; if you wrote your own words, use these.
- Shared `ui/`: the Setup card's laptop line (`laptopOffer`) says "shared
  until BEGIA closes on the laptop" for the open-ended share, and its
  not-shared hint names the same row and button.

`tests/test_payload_share.py`, `tests/test_share_choice.js`.

---

## 2026-09-30 — `app/winconsole.py`: QuickEdit off for the exe's console (nothing to do)

New module, imported first by `trial_recorder.py` and at the start of
`run()`. It switches Windows' console QuickEdit off - a click in the console
used to freeze the whole program, because the log writes from the event-loop
thread - and restores it at exit. It is a no-op off Windows (`sys.platform`)
and without a console, so on the phone it does nothing.
`tests/test_winconsole.py`.

---

## 2026-09-30 — the S7comm-plus driver reconnects in seconds (`app/s7plus.py`; nothing to do)

In your payload if the phone acquires. What changed, in case you read the
driver or its state:

- Every request the S7 client sends has a deadline (`deadline_requests`):
  2 s while polling, 3 s to connect, 20 s while the index loads. An
  unanswered request closes the socket, so the queue behind it fails at once.
- Link errors (`is_link_error`: any OSError, EOFError, the library's
  S7ConnectionError) re-raise into the reconnect instead of being counted
  against a tag, and no longer switch batched reads off.
- Retries after a link error: 0.25 s, 0.5 s, then every 1 s
  (`LINK_RETRY_S`); a wait also ends early when the route to the PLC changes
  (`route_source`, a UDP-connect route lookup, nothing sent). Other failures
  still back off to 30 s.
- `status.index.state` can now be `"building"` while `state` is
  `"connected"`: the configured signals' blocks are indexed before polling
  starts and the rest in the background (`_index_rest`), for about two
  seconds on the plant CPU. The UI already shows it as "indexing PLC… N
  blocks" beside a connected chip; if your phone layout hides the browse
  tree until the index is ready, it is ready slightly later than connected.

`tests/test_reconnect.py`.

---

## 2026-09-29 — the trigger's stop clause shows its number only where it means something (shared `ui/`; nothing to do)

The trigger sentence used one seconds box for every stop mode, shown right
after the mode: "Stop when [30] s when [signal]" and "Stop manually [30] s".
The engine reads that number as the duration under *after*, as a cap under
*when* (the trial stops on the condition, or after that many seconds at
most; 0 = no limit), and not at all under *manually*. Now:

- *after*: `Stop [after] [30] s.` - the box is `#trig-duration` in
  `#trig-dur-bit`.
- *when*: `Stop [when] [signal] [drops below] [0], or after [30] s at most.`
  - the cap is a second box, `#trig-maxdur`, after the condition; the
  repeated word "when" inside the condition is gone.
- *manually*: `Stop [manually].` - neither box.

Both boxes carry the one `duration_s` and are kept equal as you type;
`syncStopMode()` decides what shows, from both `fillTriggerCfg()` and
`pushTriggerCfg()`, and `collectTriggerCfg()` reads the box on screen. The
read-back under the sentence now says the cap too ("until X drops below 0,
or for 30 s at most" / "with no time limit"). The stop value and its comma
are one `.trig-bit`. `tests/test_trigger_stop.js`.

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
