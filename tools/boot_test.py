"""Boot a payload on the phone's Python stack and record a trial with it.

This is the proof the handoff asked for before any Android code: the service
starts on Python 3.11 + pydantic v1 + the pins in requirements-phone.txt,
from a .begia and nothing else, serves the UI, connects to a PLC (the
built-in simulator), records a trial, and the trial file names the payload's
build. Run it after every payload build and before every APK build.

    .venv-phone\\Scripts\\python tools\\boot_test.py [--payload PATH] [--port 8090]

Exit code 0 is a pass. Everything the service printed is in
boot-test-data\\service.log.
"""
from __future__ import annotations

import argparse
import json
import os
import shutil
import signal
import sqlite3
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

HERE = Path(__file__).resolve().parent
REPO = HERE.parent
RUNTIME = REPO / "runtime"
sys.path.insert(0, str(RUNTIME))

from begia_shell import payload as pl                              # noqa: E402
from begia_shell.boot import wait_healthy                          # noqa: E402

DEFAULT_PAYLOAD = Path.home() / "Desktop" / "IBA-CODE" / "dist" / "begia-payload.begia"


class Check:
    def __init__(self):
        self.n = 0

    def ok(self, what: str) -> None:
        self.n += 1
        print(f"  ok  {what}", flush=True)


def api(port: int, path: str, body: dict | None = None, method: str | None = None):
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request(
        f"http://127.0.0.1:{port}{path}", data=data,
        method=method or ("POST" if data is not None else "GET"),
        headers={"Content-Type": "application/json"} if data is not None else {})
    with urllib.request.urlopen(req, timeout=10) as r:
        raw = r.read()
        return r.status, (json.loads(raw) if raw[:1] in (b"{", b"[") else raw)


def wait_for(what: str, fn, timeout: float, every: float = 0.5):
    deadline = time.monotonic() + timeout
    last = None
    while time.monotonic() < deadline:
        try:
            v = fn()
            if v:
                return v
        except Exception as e:
            last = e
        time.sleep(every)
    raise TimeoutError(f"{what} did not happen within {timeout:.0f} s" + (f" ({last})" if last else ""))


def run(payload: Path, port: int, keep: bool) -> int:
    work = REPO / "boot-test-data"
    if work.exists() and not keep:
        shutil.rmtree(work)
    slots, data = work / "slots", work / "data"
    work.mkdir(exist_ok=True)
    log = open(work / "service.log", "w", encoding="utf-8")

    m = pl.read_manifest(payload)
    print(f"payload {payload.name}: BEGIA {m['version']} build {m['build']}, {m['file_count']} files")
    print(f"python  {sys.executable}")

    env = dict(os.environ, PYTHONPATH=str(RUNTIME), PYTHONUNBUFFERED="1")
    flags = subprocess.CREATE_NEW_PROCESS_GROUP if os.name == "nt" else 0
    proc = subprocess.Popen(
        [sys.executable, "-m", "begia_shell.boot", "--payload", str(payload),
         "--slots", str(slots), "--data", str(data), "--port", str(port),
         "--health-timeout", "60"],
        env=env, stdout=log, stderr=subprocess.STDOUT, creationflags=flags, cwd=str(work))
    c = Check()
    try:
        state = wait_healthy(port, timeout=75)
        c.ok(f"service answered on :{port} as BEGIA {state.get('version')}")
        assert state.get("version") == m["version"], (state.get("version"), m["version"])
        c.ok("reports the payload's version")

        # the booting process clears `booting` on its own health check, which
        # races ours: give it a moment rather than read the file at once
        wait_for("slot marked good", lambda: pl.Slots(slots).state["booting"] is None, 10)
        assert pl.Slots(slots).state["active"] == m["build"]
        c.ok("slot marked good after the health check")

        status, page = api(port, "/")
        assert (status == 200 and b"js/010-core.js" in page
                and b'href="css/' in page and b"BEGIA" in page), status
        c.ok("serves the UI from the payload")

        status, r = api(port, "/api/sim/start", {})
        assert status == 200 and r.get("ok"), r
        # /api/state says only "recording or not" by design (the driver's state
        # travels over the WebSocket), so readiness is "search answers": it
        # refuses with 409 until the driver is connected and has its index
        # OPC UA reports changes only, so the probe signal must move: the
        # simulator's line pressure carries noise on every 100 ms tick, while a
        # PID loop's PV sits flat through the 20 s wait step (seen: 1 sample)
        q = "Presion_Linea"
        hits = wait_for(f"search results for {q} (the driver connected and indexed)",
                        lambda: _hits(api(port, f"/api/search?q={q}&limit=50")[1]), 60, every=2)
        c.ok(f"connected to the built-in simulator at {r.get('endpoint')}, {len(hits)} hits for {q}")
        hit = next(h for h in hits if h.get("is_var") and h["node_id"].endswith('"Presion_Linea_bar"'))
        status, sig = api(port, "/api/signals",
                          {"node_id": hit["node_id"], "name": "Presion_Linea_bar",
                           "path": hit.get("path", ""), "unit": "bar", "rate_ms": 100})
        assert status == 200, sig
        c.ok(f"added a signal over OPC UA: {hit['node_id']}")

        time.sleep(2.5)
        status, r = api(port, "/api/trial/start", {"name": "boot_test", "pretrigger_s": 1.0})
        assert status == 200, r
        assert api(port, "/api/state")[1].get("recording") is True
        time.sleep(3.0)
        status, r = api(port, "/api/trial/stop", {})
        assert status == 200, r
        assert api(port, "/api/state")[1].get("recording") is False
        c.ok("started and stopped a trial; /api/state reported both")

        dbs = sorted((data / "trials").glob("*boot_test*.db"))
        assert dbs, f"no trial file under {data / 'trials'}"
        con = sqlite3.connect(f"file:{dbs[-1]}?mode=ro", uri=True)
        rows = con.execute("SELECT COUNT(*) FROM samples").fetchone()[0]
        meta = dict(con.execute("SELECT key, value FROM meta").fetchall())
        con.close()
        # ~4 s of a signal that changes every 100 ms: dozens of rows, not one
        assert rows >= 10, f"the trial recorded only {rows} samples - acquisition is not flowing"
        c.ok(f"trial file holds {rows} samples of a moving signal: {dbs[-1].name}")
        assert meta.get("begia_build") == m["build"], (meta.get("begia_build"), m["build"])
        c.ok(f"trial file names the payload build: {meta['begia_build']}")

        status, trials = api(port, "/api/trials")
        assert any("boot_test" in (t.get("name") or t.get("file") or "") for t in _list(trials)), trials
        c.ok("trial listed by the API")

        _iocheck(port, c)

        # The simulator speaks OPC UA, so nothing above touched the S7comm-plus
        # driver - the one that reaches tags OPC UA hides. Prove it imports on
        # this stack from the installed slot, including the part that needs
        # cryptography (legitimation) and the trimmed snap7.
        slot = slots / pl.slot_name(m["build"])
        code = (
            "import sys; sys.path[:0] = [sys.argv[1], sys.argv[2]]\n"
            "import snap7; assert not hasattr(snap7, 'Client'), snap7.__file__\n"
            "from s7commplus import AsyncClient\n"
            "import s7commplus.legitimation, s7commplus.typeinfo\n"
            "from app.s7plus import S7PlusDriver\n"
            "from app import driver, recorder, trigger, udp, tls\n"
            "from app.iocheck import IoCheck\n"
            "print('ok')\n")
        r = subprocess.run([sys.executable, "-c", code, str(slot), str(slot / "vendor")],
                           capture_output=True, text=True, timeout=120,
                           env=dict(os.environ, TRIALREC_DATA_DIR=str(data),
                                    TRIALREC_UI_DIR=str(slot / "ui")))
        assert r.returncode == 0 and r.stdout.strip() == "ok", r.stderr[-800:]
        c.ok("S7comm-plus driver and the trimmed snap7 import on this stack")
    except Exception as e:
        print(f"\nFAIL after {c.n} checks: {type(e).__name__}: {e}")
        _stop(proc)
        log.close()
        print("--- service.log (tail) ---")
        print("\n".join((work / "service.log").read_text(encoding="utf-8", errors="replace").splitlines()[-40:]))
        return 1
    _stop(proc)
    log.close()
    runtime = next((l for l in (work / "service.log").read_text(encoding="utf-8", errors="replace").splitlines()
                    if l.startswith("runtime ")), "")
    print(f"\nPASS  {c.n} checks  ({runtime})")
    return 0


def _iocheck(port: int, c: Check) -> None:
    """The I/O check on the simulator's cabinet, the way a FAT runs it from
    the phone: inputs pressed, each one's DB member found by its pattern
    alone. Nothing tells the check which member belongs to which input: it
    watches every member of every DB, sets aside what moves by itself, and
    names a member only once it followed the edge out AND the edge back -
    one edge is not a pattern. The simulator wires DB_Cabinet.DI_xx into
    FB_Cabinet.in_xx one cycle later, DI_07 inverted, DI_08 to nothing."""
    status, page = api(port, "/")
    if b"js/284-iocheck-popup.js" not in page:
        print("  --  no I/O check search in this payload (built before it): skipped", flush=True)
        return
    assert b"css/155-iocheck-popup.css" in page
    c.ok("serves the I/O check's five scripts and its popup sheet")

    ids = {}
    for name in ("DI_07", "DI_08", "DI_11"):
        hits = _hits(api(port, f"/api/search?q={name}&limit=20")[1]) or []
        ids[name] = next(h["node_id"] for h in hits
                         if h.get("is_var") and h["node_id"].endswith(f'"DB_Cabinet"."{name}"'))
    status, r = api(port, "/api/iocheck/inputs",
                    {"items": [{"node_id": n, "path": f"DB_Cabinet.{k}"} for k, n in ids.items()],
                     "cabinet": "boot", "rate_ms": 10})
    assert status == 200 and r.get("added") == len(ids), r
    c.ok(f"{len(ids)} cabinet inputs in the I/O check's list")

    status, r = api(port, "/api/iocheck/start", {"name": "boot_test", "search": "every"})
    assert status == 200, r
    search = api(port, "/api/iocheck")[1]["session"]["search"]
    assert search.get("mode") == "every" and search.get("watched", 0) > 0, search
    c.ok(f"check started against every DB: {search['watched']} members watched, no match given")
    # before the first press: a few seconds to learn what moves by itself
    wait_for("the search to learn what moves by itself",
             lambda: time.time() * 1000 > search["learn_until"] + 500, 15, every=0.25)

    def press(name: str, value: int) -> None:
        status, r = api(port, "/api/sim/cabinet/press", {"name": name, "value": value})
        assert status == 200 and r.get("ok"), r

    def mapping(name: str) -> dict:
        return api(port, "/api/iocheck")[1]["session"]["mapping"].get(ids[name], {})

    def found(name: str, state: str):
        def probe():
            m = mapping(name)
            return m if m.get("state") == state else None
        return wait_for(f"{name} {state}", probe, 10, every=0.25)

    press("DI_11", 1)
    found("DI_11", "searching")
    time.sleep(1.0)
    assert mapping("DI_11").get("state") == "searching", mapping("DI_11")
    c.ok("DI_11 pressed and held: searching - one edge names nothing")
    press("DI_11", 0)
    to = found("DI_11", "mapped")["to"]
    assert to[0]["name"].endswith("in_11") and to[0]["relation"] == "mapped", to[:2]
    c.ok(f"DI_11 released: found {to[0]['name']}, {to[0]['lag_ms']} ms behind")

    press("DI_07", 1)
    time.sleep(1.0)
    press("DI_07", 0)
    to = found("DI_07", "mapped")["to"]
    assert to[0]["name"].endswith("in_07") and to[0]["relation"] == "inverted", to[:2]
    c.ok(f"DI_07 pressed and released: found {to[0]['name']}, inverted")

    press("DI_08", 1)
    time.sleep(1.0)
    press("DI_08", 0)
    found("DI_08", "not mapped")
    time.sleep(1.5)                       # a member's late edge would still show
    assert mapping("DI_08").get("state") == "not mapped", mapping("DI_08")
    c.ok("DI_08 pressed and released: nothing in the DB follows it")

    status, r = api(port, "/api/iocheck/stop", {})
    assert status == 200, r
    status, csv = api(port, "/api/iocheck/export.csv")
    text = csv.decode("utf-8-sig") if isinstance(csv, bytes) else str(csv)
    assert status == 200 and "found in the DB" in text and "in_11" in text and "in_07" in text, text[:400]
    c.ok("check stopped; the CSV names the members found")


def _hits(r):
    if isinstance(r, dict):
        r = r.get("hits") or r.get("results") or []
    return r or None


def _list(r):
    if isinstance(r, dict):
        r = r.get("trials") or r.get("items") or []
    return r


def _stop(proc: subprocess.Popen) -> None:
    if proc.poll() is not None:
        return
    try:
        if os.name == "nt":
            proc.send_signal(signal.CTRL_BREAK_EVENT)
        else:
            proc.send_signal(signal.SIGINT)
        proc.wait(15)
    except Exception:
        proc.kill()


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    ap.add_argument("--payload", type=Path, default=DEFAULT_PAYLOAD)
    ap.add_argument("--port", type=int, default=8090)
    ap.add_argument("--keep", action="store_true", help="keep boot-test-data from the previous run")
    a = ap.parse_args(argv)
    if not a.payload.is_file():
        print(f"no payload at {a.payload} - build one with build_payload.bat in the desktop repo")
        return 2
    return run(a.payload.resolve(), a.port, a.keep)


if __name__ == "__main__":
    sys.exit(main())
