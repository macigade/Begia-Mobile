"""Push N signals at F Hz to a recorder over its UDP fast channel.

The frame is the PLC's (plc/FB_TrialSender.scl, app/udp.py in the desktop
repo): a 20-byte header and count REALs, big-endian. This is the load the
10 ms measurement needs - the built-in simulator changes its values every
100 ms, and OPC UA reports changes only, so nothing else on a phone with no
PLC can make the recorder write 3 000 samples a second.

    python tools/udp_load.py --host 192.168.6.140 --signals 32 --hz 100 --seconds 180

Sends on a monotonic schedule (no drift), prints the achieved frame rate
every five seconds, and puts a sawtooth on signal 0 so a gap in the
recording is visible as a jump.
"""
from __future__ import annotations

import argparse
import math
import socket
import struct
import sys
import time

HDR = struct.Struct(">4sBBHIII")


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    ap.add_argument("--host", default="127.0.0.1")
    ap.add_argument("--port", type=int, default=5555)
    ap.add_argument("--signals", type=int, default=32)
    ap.add_argument("--hz", type=int, default=100)
    ap.add_argument("--seconds", type=float, default=0, help="0 = until Ctrl+C")
    a = ap.parse_args(argv)

    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    period = 1.0 / a.hz
    n = a.signals
    fmt = struct.Struct(f">{n}f")
    t0 = time.monotonic()
    next_at = t0
    seq = 0
    sent_at_report = 0
    report_at = t0 + 5
    print(f"udp load -> {a.host}:{a.port}  {n} signals @ {a.hz} Hz  ({n * a.hz} samples/s)", flush=True)
    try:
        while True:
            now = time.monotonic()
            if a.seconds and now - t0 >= a.seconds:
                break
            if now < next_at:
                time.sleep(min(0.002, next_at - now))
                continue
            next_at += period
            t = now - t0
            tick_ms = int(t * 1000) & 0xFFFFFFFF
            values = [((t * 10) % 100.0)]                             # sawtooth, 10 s period
            for i in range(1, n):
                values.append(100.0 * math.sin(2 * math.pi * (0.1 + 0.05 * i) * t + i))
            seq = (seq + 1) & 0xFFFFFFFF
            sock.sendto(HDR.pack(b"S7TR", 1, 0, n, seq, tick_ms, 0) + fmt.pack(*values), (a.host, a.port))
            if now >= report_at:
                print(f"  {t:6.0f} s  {(seq - sent_at_report) / 5:.0f} frames/s", flush=True)
                sent_at_report = seq
                report_at += 5
    except KeyboardInterrupt:
        pass
    print(f"sent {seq} frames in {time.monotonic() - t0:.1f} s", flush=True)
    return 0


if __name__ == "__main__":
    sys.exit(main())
