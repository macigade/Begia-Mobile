"""The 10 ms measurement on a phone: N signals at 100 Hz into SQLite for M minutes.

Nobody had measured whether a phone sustains ~3 000 samples/s to SQLite with
nothing else going on. This does it against a debug build of the shell,
with the load coming over the UDP fast channel from this laptop
(tools/udp_load.py), because no PLC is needed and the built-in simulator
cannot change values faster than every 100 ms.

    .venv-phone\\Scripts\\python tools\\rate_test.py --serial 192.168.6.140:33757 --phone 192.168.6.140
        [--signals 32] [--minutes 3] [--hz 100]

Steps: read the phone's config.json through the dev server, switch the UDP
channel on with N signals, restart the recorder, start the load, record a
trial for M minutes, stop both, pull the trial file and measure it
(tools/trial_gaps.py), then put the config back and restart again.

WiFi carries the frames, so a lost frame here can be the WiFi rather than
the phone; the report says how many frames were sent and how many samples
arrived, and lists every gap with its time.
"""
from __future__ import annotations

import argparse
import copy
import json
import os
import subprocess
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
from trial_gaps import analyze                                      # noqa: E402

ADB = Path(os.environ.get("LOCALAPPDATA", "")) / "Android" / "sdk" / "platform-tools" / "adb.exe"
DEV = "http://127.0.0.1:8081"
API = "http://127.0.0.1:18080"
PKG = "com.sarralle.begia"


def adb(serial: str, *args: str, timeout: int = 60) -> str:
    r = subprocess.run([str(ADB), "-s", serial, *args], capture_output=True, timeout=timeout)
    return r.stdout.decode("utf-8", "replace")


SERIAL = {"value": ""}


def reconnect() -> None:
    """Wireless debugging comes back on a NEW port whenever the phone's screen
    sleeps, and the forwards go with the old serial. Find it again through
    mDNS and put the forwards back; the recorder itself never noticed."""
    out = subprocess.run([str(ADB), "mdns", "services"], capture_output=True, text=True, timeout=20).stdout
    for line in out.splitlines():
        if "_adb-tls-connect" in line:
            new = line.split()[-1].strip()
            if new != SERIAL["value"]:
                print(f"  phone is back on {new} (was {SERIAL['value']})", flush=True)
                subprocess.run([str(ADB), "connect", new], capture_output=True, timeout=20)
                SERIAL["value"] = new
            break
    adb(SERIAL["value"], "forward", "tcp:8081", "tcp:8081")
    adb(SERIAL["value"], "forward", "tcp:18080", "tcp:8080")


def http(method: str, url: str, body=None, timeout: int = 15):
    data = None if body is None else (body if isinstance(body, bytes) else json.dumps(body).encode())
    req = urllib.request.Request(url, data=data, method=method,
                                 headers={"Content-Type": "application/json"} if data is not None else {})
    for attempt in range(3):
        try:
            with urllib.request.urlopen(req, timeout=timeout) as r:
                raw = r.read()
                return json.loads(raw) if raw[:1] in (b"{", b"[") else raw
        except (urllib.error.URLError, ConnectionError, OSError) as e:
            if isinstance(e, urllib.error.HTTPError) or attempt == 2:
                raise
            reconnect()
            time.sleep(1)


def wait_healthy(timeout: float = 60) -> dict:
    deadline = time.monotonic() + timeout
    last = ""
    while time.monotonic() < deadline:
        try:
            st = http("GET", f"{API}/api/state", timeout=3)
            if st.get("app") == "begia":
                return st
        except Exception as e:
            last = str(e)
        time.sleep(0.5)
    raise TimeoutError(f"the recorder did not come back: {last}")


def restart_and_wait(serial: str) -> None:
    http("POST", f"{DEV}/restart", b"")
    time.sleep(3)
    # the forwards survive the process restart; the ports are the same
    wait_healthy()


def run(a) -> int:
    work = HERE.parent / "boot-test-data" / "rate"
    work.mkdir(parents=True, exist_ok=True)
    print(f"phone {a.phone} via adb {a.serial}: {a.signals} signals @ {a.hz} Hz for {a.minutes} min", flush=True)
    SERIAL["value"] = a.serial
    reconnect()
    wait_healthy(15)

    original = http("GET", f"{DEV}/config")
    if not original:
        print("the phone has no config.json yet - open BEGIA once first")
        return 2
    cfg = copy.deepcopy(original)
    cfg["udp"] = {"enabled": True, "port": a.port, "expected_ms": int(1000 / a.hz),
                  "signals": [{"name": f"Load.S{i:02d}", "unit": "", "pane": 1 + i // 8} for i in range(a.signals)]}
    http("PUT", f"{DEV}/config", cfg)
    print("udp channel configured; restarting the recorder")
    restart_and_wait(a.serial)

    sender = subprocess.Popen(
        [sys.executable, str(HERE / "udp_load.py"), "--host", a.phone, "--port", str(a.port),
         "--signals", str(a.signals), "--hz", str(a.hz), "--seconds", str(a.minutes * 60 + 20)],
        stdout=open(work / "udp_load.log", "w"), stderr=subprocess.STDOUT)
    time.sleep(5)                                 # let the channel latch its sender
    name = f"rate_{a.signals}x{a.hz}hz"
    r = http("POST", f"{API}/api/trial/start", {"name": name, "pretrigger_s": 0})
    print(f"trial started: {r.get('file')}")
    t_end = time.monotonic() + a.minutes * 60
    while time.monotonic() < t_end:
        time.sleep(30)
        try:
            st = http("GET", f"{API}/api/state", timeout=3)
            print(f"  {int(a.minutes * 60 - (t_end - time.monotonic())):4d} s  recording={st.get('recording')}")
        except Exception as e:
            print(f"  state: {e}")
    http("POST", f"{API}/api/trial/stop", {})
    print("trial stopped")
    sender.terminate()
    try:
        sender.wait(10)
    except subprocess.TimeoutExpired:
        sender.kill()
    time.sleep(3)

    fname = r["file"]
    for suffix in ("", "-wal", "-shm"):
        data = subprocess.run([str(ADB), "-s", SERIAL["value"], "exec-out", "run-as", PKG, "cat",
                               f"files/data/trials/{fname}{suffix}"], capture_output=True, timeout=120).stdout
        if data:
            (work / f"{fname}{suffix}").write_bytes(data)
    print(f"pulled {(work / fname).stat().st_size} bytes")
    print((work / "udp_load.log").read_text().splitlines()[-1])
    print()
    result = analyze(work / fname, expect_ms=1000 / a.hz, gap_ms=a.gap_ms)

    http("PUT", f"{DEV}/config", original)
    print("\nconfig put back; restarting the recorder")
    restart_and_wait(a.serial)
    ok = result["total"] > 0
    return 0 if ok else 1


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    ap.add_argument("--serial", required=True, help="adb serial, e.g. 192.168.6.140:33757")
    ap.add_argument("--phone", required=True, help="the phone's IP on this WiFi")
    ap.add_argument("--signals", type=int, default=32)
    ap.add_argument("--hz", type=int, default=100)
    ap.add_argument("--minutes", type=float, default=3)
    ap.add_argument("--port", type=int, default=5555)
    ap.add_argument("--gap-ms", type=float, default=50)
    return run(ap.parse_args(argv))


if __name__ == "__main__":
    sys.exit(main())
