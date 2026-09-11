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
SIGNATURE = "payload.sig"        # Ed25519 over the manifest bytes, by a key in trust.py
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


def signature_status(zip_path: Path) -> dict:
    """Is the payload signed, by which key, and is that key trusted here.

    The signature is over the manifest's exact bytes, and the manifest
    names every file with its sha256, so one signature covers the zip.
    It is checked against the public key this APK carries for the key id
    (trust.TRUSTED_KEYS) - never against the copy the payload brings, which
    anyone could write. Answers, never raises: policy decides what to do.
    """
    from . import trust
    out = {"signed": False, "key_id": None, "trusted": False, "why": "no signature"}
    try:
        with zipfile.ZipFile(zip_path) as z:
            raw = z.read(MANIFEST)
            try:
                sig = json.loads(z.read(SIGNATURE))
            except KeyError:
                return out
    except (zipfile.BadZipFile, KeyError, ValueError):
        return dict(out, why="unreadable")
    out["signed"] = True
    key_id = str(sig.get("key_id", ""))
    out["key_id"] = key_id
    known = trust.TRUSTED_KEYS.get(key_id)
    if not known:
        out["why"] = f"signed by a key this app does not know ({key_id or '?'})"
        return out
    try:
        from cryptography.hazmat.primitives.asymmetric import ed25519
        pub = ed25519.Ed25519PublicKey.from_public_bytes(bytes.fromhex(known["public"]))
        pub.verify(bytes.fromhex(sig["sig"]), raw)
    except Exception as e:  # InvalidSignature, a bad hex, no cryptography
        out["why"] = f"the signature by {known.get('name', key_id)} does not verify ({type(e).__name__})"
        return out
    out["trusted"] = True
    out["name"] = known.get("name", key_id)
    out["why"] = f"signed by {out['name']}"
    return out


def verify_zip(zip_path: Path) -> dict:
    """Every listed file present and matching; nothing unlisted."""
    m = read_manifest(zip_path)
    with zipfile.ZipFile(zip_path) as z:
        names = {n for n in z.namelist() if not n.endswith("/")} - {MANIFEST, SIGNATURE}
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

def install(zip_path: Path, slots_dir: Path, shell_version: int = SHELL_VERSION,
            require_signed: bool = False) -> Tuple[Path, dict]:
    """Verify, extract into a fresh slot, verify again on disk. Returns
    (slot_dir, manifest) - the manifest with a "signature" entry saying how
    it was signed. A build already installed is left as it is. With
    require_signed, a payload not signed by a trusted key is refused
    before anything is written."""
    zip_path, slots_dir = Path(zip_path), Path(slots_dir)
    m = verify_zip(zip_path)
    check_compatible(m, shell_version)
    sig = signature_status(zip_path)
    if require_signed and not sig["trusted"]:
        raise PayloadError("this phone only installs signed payloads, and this one is "
                           + ("unsigned" if not sig["signed"] else sig["why"]))
    m = dict(m, signature=sig)
    slots_dir.mkdir(parents=True, exist_ok=True)
    slot = slots_dir / slot_name(m["build"])
    if (slot / "slot.json").is_file():
        # The same build is already here. If its files are the same files,
        # there is nothing to do; if they differ - a "-dirty" build pushed
        # again after another edit - the slot is replaced, or the phone would
        # keep running the old files under the new payload's name.
        try:
            have = json.loads((slot / MANIFEST).read_text(encoding="utf-8"))
            if have.get("files") == m["files"]:
                return slot, m
        except (OSError, ValueError):
            pass
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
        "signature": sig,
    }, indent=1), encoding="utf-8")
    shutil.rmtree(slot, ignore_errors=True)
    os.replace(tmp, slot)
    return slot, m


def slot_manifest(slot: Path) -> dict:
    p = Path(slot) / MANIFEST
    if not p.is_file():
        raise PayloadError(f"{slot} is not an installed slot")
    return json.loads(p.read_text(encoding="utf-8"))


def slot_signature(slot: Path) -> dict:
    """How an installed slot was signed, as recorded when it was installed."""
    try:
        return json.loads((Path(slot) / "slot.json").read_text(encoding="utf-8")).get(
            "signature") or {"signed": False, "key_id": None, "trusted": False, "why": "no signature"}
    except (OSError, ValueError):
        return {"signed": False, "key_id": None, "trusted": False, "why": "unknown"}


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

    def bad_builds(self) -> set:
        return {b["build"] for b in self.state.get("bad", [])}

    def mark_bad(self, build: str, why: str) -> Optional[str]:
        """Record the failure and fall back. Returns the build now active.

        The fallback is `previous` when it is a good build, else the newest
        other installed build that has not failed - so two bad updates in a
        row still land on the last version that worked, as long as it is on
        the phone. And `previous` is refilled the same way, so the one after
        that has somewhere to go too."""
        self.state.setdefault("bad", []).append({"build": build, "why": why, "at": _now()})
        if self.state["active"] == build:
            self.state["active"] = self._fallback(exclude={build})
            self.state["previous"] = self._fallback(exclude={build, self.state["active"]})
        self.state["booting"] = None
        self.save()
        return self.state["active"]

    def _fallback(self, exclude: set) -> Optional[str]:
        bad = self.bad_builds()
        prev = self.state.get("previous")
        if prev and prev not in exclude and prev not in bad:
            return prev
        good = [s for s in self.installed()
                if s["build"] not in exclude and s["build"] not in bad]
        good.sort(key=lambda s: s.get("installed_at", ""), reverse=True)
        return good[0]["build"] if good else None

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
