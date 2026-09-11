"""What the Kotlin shell calls. Every function takes plain strings and returns
a JSON string or raises PayloadError with a message written for the screen.

Two processes call in here. The recorder process calls `start` once, at
service start. The screen's process calls `install`, `activate` and `info`
for the update flow. They share slots/state.json through the slot manager,
which writes it atomically.
"""
from __future__ import annotations

import json
import traceback
from datetime import datetime, timezone
from pathlib import Path
from typing import Optional

from . import boot
from . import payload as pl

LAST_BOOT = "last_boot.json"
POLICY = "policy.json"           # {"require_signed": bool}; off until a site asks


def _dirs(files_dir: str):
    files = Path(files_dir)
    return files / "slots", files / "data"


def _now() -> str:
    return datetime.now(timezone.utc).replace(microsecond=0).isoformat()


def _read_json(p: Path) -> Optional[dict]:
    try:
        return json.loads(p.read_text(encoding="utf-8")) if p.is_file() else None
    except ValueError:
        return None


def ensure_embedded(files_dir: str, embedded_zip: str) -> dict:
    """The payload the APK shipped with becomes a slot. First run: it is the
    active one. Later: it is installed if that build is missing, but never
    takes over from a build the phone was updated to since."""
    slots, _ = _dirs(files_dir)
    s = pl.Slots(slots)
    # Always through install(): it is a manifest comparison when the same
    # files are already there, and a replacement when the same build stamp
    # arrives with different files - a "-dirty" tree rebuilt into a new APK.
    # Deciding here by "is the slot there" skipped exactly that case.
    _slot, m = pl.install(Path(embedded_zip), slots)
    if not s.active:
        s.activate(m["build"])
    return m


def start(files_dir: str, embedded_zip: Optional[str], port: int = 8080,
          restart=None, debug: bool = False) -> str:
    """Boot the active slot; returns the report as JSON. Raises after writing
    the same report with the error, so the screen can show it. `restart` is a
    java.lang.Runnable the dev server calls after installing a pushed payload."""
    slots, data = _dirs(files_dir)
    report = {"ok": False, "port": port, "at": _now()}
    try:
        if embedded_zip and Path(embedded_zip).is_file():
            try:
                ensure_embedded(files_dir, embedded_zip)
            except pl.PayloadError as e:
                # a broken embedded payload is not fatal while a slot is active
                report["embedded_error"] = str(e)
                print(f"embedded payload refused: {e}", flush=True)
        state = boot.start(slots, data, port)
        s = pl.Slots(slots)
        m = pl.slot_manifest(s.slot_path(s.active))
        report.update(ok=True, version=m["version"], build=m["build"],
                      rollback_note=state.get("rollback_note"))
        if debug and restart is not None:
            from . import devserver
            devserver.serve(files_dir, port + 1, restart)
    except Exception as e:
        report.update(error=f"{type(e).__name__}: {e}", trace=traceback.format_exc()[-3000:])
        _write(files_dir, report)
        raise
    _write(files_dir, report)
    return json.dumps(report)


def _write(files_dir: str, report: dict) -> None:
    p = Path(files_dir) / LAST_BOOT
    tmp = p.with_suffix(".json.tmp")
    tmp.write_text(json.dumps(report, indent=1), encoding="utf-8")
    tmp.replace(p)


def policy(files_dir: str) -> dict:
    p = _read_json(Path(files_dir) / POLICY) or {}
    return {"require_signed": bool(p.get("require_signed", False))}


def set_policy(files_dir: str, require_signed: bool) -> str:
    _write_json(Path(files_dir) / POLICY, {"require_signed": bool(require_signed)})
    return json.dumps(policy(files_dir))


def _write_json(p: Path, obj: dict) -> None:
    tmp = p.with_suffix(p.suffix + ".tmp")
    tmp.write_text(json.dumps(obj, indent=1), encoding="utf-8")
    tmp.replace(p)


def install(zip_path: str, files_dir: str) -> str:
    """Verify and extract; nothing is activated. Returns what to ask the
    operator. A pushed or picked payload is subject to the phone's policy;
    the APK's own embedded payload (ensure_embedded) is not - the APK is
    the trust root, and what it carries is its own."""
    slots, _ = _dirs(files_dir)
    slot, m = pl.install(Path(zip_path), slots, require_signed=policy(files_dir)["require_signed"])
    return json.dumps({
        "version": m["version"], "build": m["build"], "min_shell": m["min_shell"],
        "file_count": m.get("file_count", len(m["files"])), "created": m.get("created"),
        "already_active": pl.Slots(slots).active == m["build"],
        "signature": m.get("signature"),
    })


def activate(build: str, files_dir: str) -> None:
    slots, _ = _dirs(files_dir)
    s = pl.Slots(slots)
    if not (s.slot_path(build) / "slot.json").is_file():
        raise pl.PayloadError(f"{build} is not installed")
    s.activate(build)


def info_dict(files_dir: str) -> dict:
    slots, _ = _dirs(files_dir)
    s = pl.Slots(slots)
    out = {
        "shell": pl.SHELL_VERSION,
        "active": None,
        "previous": s.state.get("previous"),
        "installed": s.installed(),
        "bad": s.state.get("bad", [])[-5:],
        "last_boot": _read_json(Path(files_dir) / LAST_BOOT),
        "policy": policy(files_dir),
        "trusted_keys": {k: v.get("name", k) for k, v in __import__(
            "begia_shell.trust", fromlist=["TRUSTED_KEYS"]).TRUSTED_KEYS.items()},
    }
    for entry in out["installed"]:
        try:
            entry["signature"] = pl.slot_signature(s.slot_path(entry["build"]))
        except Exception:
            pass
    if s.active:
        try:
            m = pl.slot_manifest(s.slot_path(s.active))
            out["active"] = {"build": m["build"], "version": m["version"], "created": m.get("created"),
                             "signature": pl.slot_signature(s.slot_path(s.active))}
        except pl.PayloadError:
            out["active"] = {"build": s.active}
    return out


def info(files_dir: str) -> str:
    return json.dumps(info_dict(files_dir))
