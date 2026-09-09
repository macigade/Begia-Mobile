"""Payload slots: install a .begia, choose which one boots, roll a bad one back.

A payload (built by tools/make_payload.py in the desktop repo) is a zip with
payload.json listing every file and its sha256. Nothing in it is trusted
until it has been verified twice - in the zip, then on disk - because it
arrived over WiFi or from a USB stick and a truncated download must not boot.

Slots live under one directory:

    slots/
      state.json                 {"active", "previous", "booting", "bad"}
      v0.9-4-g1432fd2/           one slot per build, named after it
        payload.json
        slot.json
        app/  ui/  vendor/  presets.json

The rule that gets a phone out of a bad update needs no network and no
judgement: before the service starts, the shell writes the build it is about
to boot into `booting`; when /api/state answers, it clears it. A `booting`
still set when the shell next starts means that build never became healthy -
it is marked bad, `previous` becomes active, and the reason is kept so the
screen can say what happened. Rolling back therefore needs a fresh process,
which on the phone is the foreground service being restarted.
"""
from __future__ import annotations

import hashlib
import json
import os
import re
import shutil
import zipfile
from datetime import datetime, timezone
from pathlib import Path
from typing import Dict, List, Optional, Tuple

FORMAT = 1
SHELL_VERSION = 1
MANIFEST = "payload.json"
REQUIRED = ("format", "version", "build", "min_shell", "files", "ui_dir", "sys_path", "entry")


class PayloadError(Exception):
    """A payload that must not be installed or booted - the message is written
    to be shown on the phone's screen as it is."""


def _now() -> str:
    return datetime.now(timezone.utc).replace(microsecond=0).isoformat()


def _sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def _safe(arc: str) -> None:
    if arc.startswith(("/", "\\")) or ".." in arc.replace("\\", "/").split("/") or ":" in arc:
        raise PayloadError(f"the payload names an unsafe path: {arc}")


def slot_name(build: str) -> str:
    return re.sub(r"[^A-Za-z0-9._-]", "_", build)


# ------------------------------------------------------------- reading ------

def read_manifest(zip_path: Path) -> dict:
    try:
        with zipfile.ZipFile(zip_path) as z:
            raw = z.read(MANIFEST)
    except zipfile.BadZipFile:
        raise PayloadError("this is not a BEGIA payload: not a zip file")
    except KeyError:
        raise PayloadError("this is not a BEGIA payload: no payload.json inside")
    try:
        m = json.loads(raw)
    except ValueError:
        raise PayloadError("payload.json is not valid JSON")
    for k in REQUIRED:
        if k not in m:
            raise PayloadError(f"payload.json lacks {k!r}")
    if m["format"] != FORMAT:
        raise PayloadError(f"payload format {m['format']}; this app reads format {FORMAT} - update the app")
    return m


def check_compatible(m: dict, shell_version: int = SHELL_VERSION) -> None:
    if int(m["min_shell"]) > shell_version:
        raise PayloadError(
            f"BEGIA {m['version']} ({m['build']}) needs app shell {m['min_shell']}, "
            f"and this is shell {shell_version} - install the newer APK first")


def verify_zip(zip_path: Path) -> dict:
    """Every listed file present and matching; nothing unlisted."""
    m = read_manifest(zip_path)
    with zipfile.ZipFile(zip_path) as z:
        names = {n for n in z.namelist() if not n.endswith("/")} - {MANIFEST}
        listed = set(m["files"])
        if names - listed:
            raise PayloadError(f"the zip holds files the manifest does not list: {sorted(names - listed)[:3]}")
        if listed - names:
            raise PayloadError(f"the manifest lists files the zip lacks: {sorted(listed - names)[:3]}")
        for arc, info in m["files"].items():
            _safe(arc)
            data = z.read(arc)
            if len(data) != info["size"] or _sha256(data) != info["sha256"]:
                raise PayloadError(f"{arc} does not match its checksum - the file is damaged or was altered")
    return m


# ---------------------------------------------------------- installing ------

def install(zip_path: Path, slots_dir: Path, shell_version: int = SHELL_VERSION) -> Tuple[Path, dict]:
    """Verify, extract into a fresh slot, verify again on disk. Returns
    (slot_dir, manifest). A build already installed is left as it is."""
    zip_path, slots_dir = Path(zip_path), Path(slots_dir)
    m = verify_zip(zip_path)
    check_compatible(m, shell_version)
    slots_dir.mkdir(parents=True, exist_ok=True)
    slot = slots_dir / slot_name(m["build"])
    if (slot / "slot.json").is_file():
        return slot, m
    tmp = slots_dir / (slot.name + ".installing")
    shutil.rmtree(tmp, ignore_errors=True)
    with zipfile.ZipFile(zip_path) as z:
        for arc in m["files"]:
            z.extract(arc, tmp)
        z.extract(MANIFEST, tmp)
    for arc, info in m["files"].items():
        data = (tmp / arc).read_bytes()
        if len(data) != info["size"] or _sha256(data) != info["sha256"]:
            shutil.rmtree(tmp, ignore_errors=True)
            raise PayloadError(f"{arc} was not written correctly - is the storage full?")
    (tmp / "slot.json").write_text(json.dumps({
        "version": m["version"], "build": m["build"],
        "installed_at": _now(), "source": zip_path.name,
    }, indent=1), encoding="utf-8")
    shutil.rmtree(slot, ignore_errors=True)
    os.replace(tmp, slot)
    return slot, m


def slot_manifest(slot: Path) -> dict:
    p = Path(slot) / MANIFEST
    if not p.is_file():
        raise PayloadError(f"{slot} is not an installed slot")
    return json.loads(p.read_text(encoding="utf-8"))


# ----------------------------------------------------------------- slots ----

class Slots:
    """Which build boots, and the rollback rule (module docstring)."""

    def __init__(self, slots_dir: Path):
        self.dir = Path(slots_dir)
        self.state_path = self.dir / "state.json"
        self.state: dict = {"active": None, "previous": None, "booting": None, "bad": []}
        if self.state_path.is_file():
            try:
                self.state.update(json.loads(self.state_path.read_text(encoding="utf-8")))
            except ValueError:
                pass                      # a torn write: start from nothing rather than refuse

    def save(self) -> None:
        self.dir.mkdir(parents=True, exist_ok=True)
        tmp = self.state_path.with_suffix(".json.tmp")
        tmp.write_text(json.dumps(self.state, indent=1), encoding="utf-8")
        os.replace(tmp, self.state_path)

    # what is on disk
    def slot_path(self, build: str) -> Path:
        return self.dir / slot_name(build)

    def installed(self) -> List[dict]:
        out = []
        for p in sorted(self.dir.glob("*/slot.json")):
            try:
                out.append(json.loads(p.read_text(encoding="utf-8")))
            except ValueError:
                continue
        return out

    @property
    def active(self) -> Optional[str]:
        return self.state.get("active")

    # transitions
    def activate(self, build: str) -> None:
        if self.state["active"] != build:
            self.state["previous"] = self.state["active"]
            self.state["active"] = build
        self.state["booting"] = None
        self.save()

    def mark_booting(self, build: str) -> None:
        self.state["booting"] = build
        self.save()

    def mark_good(self, build: str) -> None:
        if self.state.get("booting") == build:
            self.state["booting"] = None
            self.save()

    def mark_bad(self, build: str, why: str) -> Optional[str]:
        """Record the failure and fall back. Returns the build now active."""
        self.state.setdefault("bad", []).append({"build": build, "why": why, "at": _now()})
        if self.state["active"] == build:
            prev = self.state.get("previous")
            self.state["active"] = prev if prev and prev != build else None
            self.state["previous"] = None
        self.state["booting"] = None
        self.save()
        return self.state["active"]

    def resolve_for_boot(self) -> Tuple[Path, Optional[str]]:
        """The slot to boot now, and a sentence for the screen if a rollback
        just happened. Raises PayloadError when nothing bootable is installed."""
        note = None
        left = self.state.get("booting")
        if left:
            now = self.mark_bad(left, "did not become healthy on the last start")
            note = (f"BEGIA {left} did not start; went back to {now}"
                    if now else f"BEGIA {left} did not start, and there is no earlier version to go back to")
        active = self.state.get("active")
        if not active:
            raise PayloadError("no BEGIA payload is installed" + (f" ({note})" if note else ""))
        slot = self.slot_path(active)
        if not (slot / "slot.json").is_file():
            raise PayloadError(f"the active payload {active} is missing from {self.dir}")
        return slot, note

    def prune(self) -> List[str]:
        """Delete every installed slot that is neither active nor previous."""
        keep = {self.state.get("active"), self.state.get("previous")}
        gone = []
        for s in self.installed():
            if s["build"] not in keep:
                shutil.rmtree(self.slot_path(s["build"]), ignore_errors=True)
                gone.append(s["build"])
        return gone
