"""Measure a trial file: per signal, how many samples, at what rate, and
every gap that should not be there.

    python tools/trial_gaps.py path/to/trial.db [--expect-ms 10] [--gap-ms 50]

--expect-ms is the nominal interval; the report compares the count against
what that interval would have produced over the trial's length. --gap-ms is
the largest gap that counts as continuous; larger ones are listed with
their wall-clock time so they can be matched to what was happening.
"""
from __future__ import annotations

import argparse
import sqlite3
import sys
from datetime import datetime
from pathlib import Path


def analyze(db: Path, expect_ms: float | None, gap_ms: float | None, show: int = 15) -> dict:
    # A file pulled off a phone mid-life comes with its -wal and -shm, and
    # SQLite cannot recover the write-ahead log read-only. Work on a copy,
    # opened normally, so the recovery happens there and the original stays
    # exactly as pulled.
    import shutil
    import tempfile
    tmp = Path(tempfile.mkdtemp(prefix="trial_gaps_"))
    for suffix in ("", "-wal", "-shm"):
        src = Path(str(db) + suffix)
        if src.is_file():
            shutil.copy2(src, tmp / (db.name + suffix))
    con = sqlite3.connect(tmp / db.name)
    try:
        meta = dict(con.execute("SELECT key, value FROM meta").fetchall())
    except sqlite3.OperationalError:
        raise SystemExit(f"{db.name}: no tables - a mid-life snapshot whose schema is still in a "
                         "write-ahead log that no longer matches the file. Stop the trial and pull it again.")
    sigs = con.execute("SELECT id, name FROM signals ORDER BY id").fetchall()
    started = int(meta.get("started_ms") or 0)
    stopped = int(meta.get("stopped_ms") or 0)
    out = {"file": db.name, "name": meta.get("name"), "build": meta.get("begia_build"),
           "driver": meta.get("driver"), "signals": [], "started_ms": started, "stopped_ms": stopped}
    print(f"{db.name}: {meta.get('name')}  build {meta.get('begia_build')}  driver {meta.get('driver')}")
    total = 0
    for sid, name in sigs:
        ts = [r[0] for r in con.execute("SELECT t_ms FROM samples WHERE signal_id=? ORDER BY t_ms", (sid,))]
        total += len(ts)
        if len(ts) < 2:
            print(f"  {name}: {len(ts)} sample(s)")
            out["signals"].append({"name": name, "count": len(ts)})
            continue
        t_first, t_last = ts[0], ts[-1]
        span_s = (max(stopped, t_last) - min(started or t_first, t_first)) / 1000
        gaps = [(ts[i] - ts[i - 1], ts[i - 1]) for i in range(1, len(ts))]
        gs = sorted(g for g, _ in gaps)
        median = gs[len(gs) // 2]
        limit = gap_ms if gap_ms else max(3 * median, 1000)
        big = [(g, t) for g, t in gaps if g > limit]
        expect = span_s * 1000 / expect_ms if expect_ms else None
        rate = len(ts) / span_s if span_s > 0 else 0
        line = f"  {name}: {len(ts)} samples over {span_s:.1f} s = {rate:.1f}/s, median gap {median} ms, max {gs[-1]} ms"
        if expect:
            line += f", {len(ts) / expect * 100:.1f}% of the {expect:.0f} a {expect_ms:g} ms interval gives"
        line += f", gaps over {limit:.0f} ms: {len(big)}"
        print(line)
        for g, t in big[:show]:
            print(f"      {g / 1000:8.2f} s at {datetime.fromtimestamp(t / 1000).strftime('%H:%M:%S.%f')[:-3]}")
        out["signals"].append({"name": name, "count": len(ts), "rate": rate, "median_ms": median,
                               "max_ms": gs[-1], "big_gaps": len(big), "expected": expect})
    dur = (stopped - started) / 1000 if started and stopped else None
    print(f"  total {total} samples" + (f" in {dur:.1f} s = {total / dur:.0f} samples/s into SQLite" if dur else ""))
    out["total"] = total
    out["duration_s"] = dur
    return out


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    ap.add_argument("db", type=Path)
    ap.add_argument("--expect-ms", type=float, default=None)
    ap.add_argument("--gap-ms", type=float, default=None)
    a = ap.parse_args(argv)
    analyze(a.db, a.expect_ms, a.gap_ms)
    return 0


if __name__ == "__main__":
    sys.exit(main())
