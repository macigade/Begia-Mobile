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
  `FSarralle2024`, `opc.tcp://10.6.70.153:4840`) is quoted in plaintext in
  `docs/BACKLOG.md` (the FIX item 1 write-up) and has been on origin since
  `ca23472`. The packaging leak itself is fixed; **rotating the credential does
  not remove it from git history**, and rotation is the user's call and the
  user's action, not ours. Do not carry that credential into anything the
  Android port ships.
- Versions are not bumped and tags are not cut unless the user asks.
