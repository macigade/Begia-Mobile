"""Start BEGIA from an installed payload slot.

The same code path on the phone and on the laptop: put the slot on sys.path
and in the environment the way app/config.py expects, start uvicorn on the
loopback address the WebView loads, and wait until /api/state answers. The
service is exactly the desktop's `app.main:app`; nothing here knows what it
serves.

Laptop use (tools/boot_test.py drives this):

    python -m begia_shell.boot --payload dist\\begia-payload.begia ^
        --slots work\\slots --data work\\data --port 8090 [--exit-when-healthy]

Phone use: RecorderService calls `start(slots_dir, data_dir, port)` once per
process. It returns the state payload when the service is healthy, and raises
when it is not - leaving `booting` set in slots/state.json, so the next
process start rolls back (see payload.Slots).
"""
from __future__ import annotations

import argparse
import importlib.metadata
import json
import os
import sys
import threading
import time
import urllib.request
from pathlib import Path
from typing import Callable, Optional

from . import payload as pl

HOST = "127.0.0.1"
PORT = 8080


def prepare(slot: Path, data_dir: Path) -> dict:
    """Environment and sys.path for this slot. Must run before app.* is
    imported: app/config.py resolves its paths at import time."""
    slot, data_dir = Path(slot), Path(data_dir)
    m = pl.slot_manifest(slot)
    data_dir.mkdir(parents=True, exist_ok=True)
    os.environ["TRIALREC_DATA_DIR"] = str(data_dir)
    os.environ["TRIALREC_UI_DIR"] = str(slot / m["ui_dir"])
    for rel in reversed(m["sys_path"]):
        p = str((slot / rel).resolve())
        if p not in sys.path:
            sys.path.insert(0, p)
    return m


def versions() -> dict:
    """What this interpreter runs the service on - printed at boot so a phone
    log can be matched to requirements-phone.txt."""
    out = {"python": sys.version.split()[0]}
    for dist in ("pydantic", "fastapi", "starlette", "uvicorn", "websockets", "asyncua", "cryptography"):
        try:
            out[dist] = importlib.metadata.version(dist)
        except importlib.metadata.PackageNotFoundError:
            out[dist] = "missing"
    return out


def make_server(host: str, port: int):
    import uvicorn
    module, _, attr = "app.main:app".partition(":")
    app = getattr(__import__(module, fromlist=[attr]), attr)
    return uvicorn.Server(uvicorn.Config(app, host=host, port=port, log_level="warning"))


def wait_healthy(port: int, timeout: float = 30.0, host: str = HOST) -> dict:
    """Poll /api/state until the service answers as BEGIA."""
    deadline = time.monotonic() + timeout
    last = ""
    while time.monotonic() < deadline:
        try:
            with urllib.request.urlopen(f"http://{host}:{port}/api/state", timeout=2) as r:
                state = json.loads(r.read())
            if state.get("app") == "begia":
                return state
            last = f"answered, but not as BEGIA: {str(state)[:80]}"
        except Exception as e:                      # connection refused while booting
            last = str(e)
        time.sleep(0.25)
    raise TimeoutError(f"no answer on /api/state after {timeout:.0f} s ({last})")


def serve(slot: Path, data_dir: Path, host: str = HOST, port: int = PORT,
          health_timeout: float = 30.0, on_healthy: Optional[Callable[[dict], None]] = None):
    """Start the service on a daemon thread and wait for it. Returns
    (server, thread, state). Raises if it never becomes healthy; the server
    is asked to stop in that case."""
    m = prepare(slot, data_dir)
    print(f"booting BEGIA {m['version']} ({m['build']}) from {slot}", flush=True)
    print("runtime " + " ".join(f"{k}={v}" for k, v in versions().items()), flush=True)
    server = make_server(host, port)
    thread = threading.Thread(target=server.run, name="begia-server", daemon=True)
    thread.start()
    try:
        state = wait_healthy(port, health_timeout, host)
    except TimeoutError:
        server.should_exit = True
        raise
    print(f"healthy: BEGIA {state.get('version')} on http://{host}:{port}", flush=True)
    if on_healthy:
        on_healthy(state)
    return server, thread, state


def start(slots_dir: Path, data_dir: Path, port: int = PORT, host: str = HOST,
          health_timeout: float = 30.0) -> dict:
    """The phone's entry point: choose the slot, boot it, record the outcome.
    Returns the state payload. Raises PayloadError / TimeoutError / ImportError
    with `booting` left set, so the next process start rolls back."""
    slots = pl.Slots(slots_dir)
    slot, note = slots.resolve_for_boot()
    if note:
        print(f"ROLLBACK: {note}", flush=True)
    build = pl.slot_manifest(slot)["build"]
    slots.mark_booting(build)
    _, _, state = serve(slot, data_dir, host, port, health_timeout,
                        on_healthy=lambda _s: slots.mark_good(build))
    state["rollback_note"] = note
    return state


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description="boot BEGIA from a payload slot")
    ap.add_argument("--payload", type=Path, help="a .begia to install and activate first")
    ap.add_argument("--slots", type=Path, default=Path("slots"))
    ap.add_argument("--data", type=Path, default=Path("data"),
                    help="TRIALREC_DATA_DIR: config, trials, signal sets (kept across payloads)")
    ap.add_argument("--host", default=HOST)
    ap.add_argument("--port", type=int, default=PORT)
    ap.add_argument("--health-timeout", type=float, default=30.0)
    ap.add_argument("--exit-when-healthy", action="store_true",
                    help="stop as soon as /api/state answers (a boot test)")
    a = ap.parse_args(argv)

    slots = pl.Slots(a.slots)
    try:
        if a.payload:
            slot, m = pl.install(a.payload, a.slots)
            slots.activate(m["build"])
            print(f"installed {m['build']} into {slot}", flush=True)
        slot, note = slots.resolve_for_boot()
        if note:
            print(f"ROLLBACK: {note}", flush=True)
        build = pl.slot_manifest(slot)["build"]
        slots.mark_booting(build)
        server, thread, _ = serve(slot, a.data, a.host, a.port, a.health_timeout,
                                  on_healthy=lambda _s: slots.mark_good(build))
    except (pl.PayloadError, TimeoutError, ImportError) as e:
        print(f"FAILED: {e}", flush=True)
        return 1
    if a.exit_when_healthy:
        server.should_exit = True
        thread.join(15)
        return 0
    try:
        while thread.is_alive():
            thread.join(0.5)
    except KeyboardInterrupt:
        server.should_exit = True
        thread.join(15)
    return 0


if __name__ == "__main__":
    sys.exit(main())
