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
through the same helper. **The `.sha256` seal sidecar is removed with its
file** by both routes — it was left orphaned before, and the next trial to
reuse the name would have inherited it.

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
| `GET /api/payload/info` | phone Setup screen | version/build/bytes before offering an update — lets it say "0.10, and you have 0.9" |
| `GET /api/payload` | phone Setup screen | the `.begia` zip itself |
| `GET /api/watch` | the phone's relay, on loopback, a few times a minute | recording or not, since when, marks so far, one analog signal's latest reading |
| `GET /api/hosting` | phone, on first contact | app name, version, whether it is recording |
| `GET /api/state`, `WS /ws` | the UI, phone or laptop | unchanged contract |

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
