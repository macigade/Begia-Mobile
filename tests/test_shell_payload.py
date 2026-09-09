"""The shell's payload manager: what it refuses, and how it gets out of a bad update.

A phone in a pocket at a furnace has no one to ask. So a damaged download
must not install, a payload for a newer shell must not install, and a build
that was left "booting" must be abandoned for the previous one on the next
start - without a network, without judgement.

    .venv-phone\\Scripts\\python -m pytest tests -q
"""
from __future__ import annotations

import hashlib
import json
import sys
import zipfile
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "runtime"))

from begia_shell import payload as pl                              # noqa: E402


def make_payload(path: Path, build: str = "v0.9-8-gabcdef0", min_shell: int = 1,
                 tamper: bool = False, extra: bool = False) -> Path:
    files = {
        "app/__init__.py": b"",
        "app/main.py": b"app = object()\n",
        "app/version.py": f'__version__ = "0.9"\nBUILD = "{build}"\n'.encode(),
        "ui/index.html": b"<title>BEGIA</title>",
        "presets.json": b"{}",
        "vendor/snap7/__init__.py": b"",
    }
    manifest = {
        "format": pl.FORMAT, "name": "BEGIA", "version": "0.9", "build": build,
        "min_shell": min_shell, "sys_path": [".", "vendor"], "ui_dir": "ui",
        "entry": "app.main:app",
        "files": {k: {"sha256": hashlib.sha256(v).hexdigest(), "size": len(v)} for k, v in files.items()},
    }
    with zipfile.ZipFile(path, "w") as z:
        z.writestr(pl.MANIFEST, json.dumps(manifest))
        for k, v in files.items():
            if tamper and k == "app/main.py":
                v = b"app = None  # altered\n"
            z.writestr(k, v)
        if extra:
            z.writestr("vendor/extra.py", b"")
    return path


# ------------------------------------------------------------- refusing ----

def test_verify_accepts_a_good_payload(tmp_path):
    m = pl.verify_zip(make_payload(tmp_path / "a.begia"))
    assert m["build"] == "v0.9-8-gabcdef0"


def test_refuses_a_tampered_file(tmp_path):
    with pytest.raises(pl.PayloadError, match="checksum"):
        pl.verify_zip(make_payload(tmp_path / "a.begia", tamper=True))


def test_refuses_unlisted_files(tmp_path):
    with pytest.raises(pl.PayloadError, match="does not list"):
        pl.verify_zip(make_payload(tmp_path / "a.begia", extra=True))


def test_refuses_a_payload_for_a_newer_shell(tmp_path):
    p = make_payload(tmp_path / "a.begia", min_shell=pl.SHELL_VERSION + 1)
    with pytest.raises(pl.PayloadError, match="install the newer APK"):
        pl.install(p, tmp_path / "slots")


def test_refuses_not_a_payload(tmp_path):
    p = tmp_path / "x.begia"
    p.write_bytes(b"not a zip")
    with pytest.raises(pl.PayloadError, match="not a zip"):
        pl.read_manifest(p)
    with zipfile.ZipFile(p, "w") as z:
        z.writestr("hello.txt", b"hi")
    with pytest.raises(pl.PayloadError, match="no payload.json"):
        pl.read_manifest(p)


# ----------------------------------------------------------- installing ----

def test_install_extracts_verifies_and_is_idempotent(tmp_path):
    slots = tmp_path / "slots"
    slot, m = pl.install(make_payload(tmp_path / "a.begia"), slots)
    assert slot == slots / "v0.9-8-gabcdef0"
    assert (slot / "app" / "main.py").read_bytes() == b"app = object()\n"
    assert (slot / pl.MANIFEST).is_file() and (slot / "slot.json").is_file()
    assert json.loads((slot / "slot.json").read_text())["build"] == m["build"]
    assert not (slots / "v0.9-8-gabcdef0.installing").exists()
    # a second install of the same build leaves the slot alone
    (slot / "marker").write_text("kept")
    slot2, _ = pl.install(make_payload(tmp_path / "b.begia"), slots)
    assert slot2 == slot and (slot / "marker").read_text() == "kept"


def test_same_build_with_different_files_replaces_the_slot(tmp_path):
    """A "-dirty" build pushed twice with an edit in between has the same
    stamp and different content: the second push must win, or the phone runs
    the old files under the new payload's name."""
    slots = tmp_path / "slots"
    slot, _ = pl.install(make_payload(tmp_path / "a.begia"), slots)
    assert (slot / "app" / "main.py").read_bytes() == b"app = object()\n"
    # the same build stamp, one file changed (and re-hashed by make_payload)
    p = make_payload(tmp_path / "b.begia", tamper=False)
    with zipfile.ZipFile(p) as z:
        m = json.loads(z.read(pl.MANIFEST))
        others = {n: z.read(n) for n in z.namelist() if n not in (pl.MANIFEST, "app/main.py")}
    new = b"app = object()  # edited\n"
    m["files"]["app/main.py"] = {"sha256": hashlib.sha256(new).hexdigest(), "size": len(new)}
    with zipfile.ZipFile(p, "w") as z:
        z.writestr(pl.MANIFEST, json.dumps(m))
        z.writestr("app/main.py", new)
        for n, d in others.items():
            z.writestr(n, d)
    slot2, _ = pl.install(p, slots)
    assert slot2 == slot
    assert (slot / "app" / "main.py").read_bytes() == new
    assert not (slots / (slot.name + ".installing")).exists()


def test_slot_names_are_filesystem_safe(tmp_path):
    slot, _ = pl.install(make_payload(tmp_path / "a.begia", build="v0.9-8-gabc-dirty"), tmp_path / "s")
    assert slot.name == "v0.9-8-gabc-dirty"
    assert pl.slot_name('v0.9/odd"name') == "v0.9_odd_name"


# ------------------------------------------------------------- rollback ----

def two_builds(tmp_path):
    slots_dir = tmp_path / "slots"
    pl.install(make_payload(tmp_path / "old.begia", build="v0.9"), slots_dir)
    pl.install(make_payload(tmp_path / "new.begia", build="v0.10"), slots_dir)
    s = pl.Slots(slots_dir)
    s.activate("v0.9")
    s.activate("v0.10")
    return slots_dir


def test_a_healthy_boot_keeps_the_new_build(tmp_path):
    slots_dir = two_builds(tmp_path)
    s = pl.Slots(slots_dir)
    slot, note = s.resolve_for_boot()
    assert slot.name == "v0.10" and note is None
    s.mark_booting("v0.10")
    s.mark_good("v0.10")
    again = pl.Slots(slots_dir)                # a fresh process reads the file
    assert again.state["booting"] is None
    assert again.resolve_for_boot() == (slot, None)


def test_a_boot_that_never_became_healthy_rolls_back(tmp_path):
    slots_dir = two_builds(tmp_path)
    s = pl.Slots(slots_dir)
    s.mark_booting("v0.10")                    # ...and the process died here
    fresh = pl.Slots(slots_dir)
    slot, note = fresh.resolve_for_boot()
    assert slot.name == "v0.9"
    assert note == "BEGIA v0.10 did not start; went back to v0.9"
    assert fresh.state["active"] == "v0.9" and fresh.state["booting"] is None
    assert fresh.state["bad"][0]["build"] == "v0.10"
    assert "did not become healthy" in fresh.state["bad"][0]["why"]
    # and the rollback itself is not repeated on the next start
    assert pl.Slots(slots_dir).resolve_for_boot() == (slot, None)


def test_nothing_to_go_back_to_is_said_plainly(tmp_path):
    slots_dir = tmp_path / "slots"
    pl.install(make_payload(tmp_path / "only.begia", build="v0.10"), slots_dir)
    s = pl.Slots(slots_dir)
    s.activate("v0.10")
    s.mark_booting("v0.10")
    with pytest.raises(pl.PayloadError, match="no earlier version"):
        pl.Slots(slots_dir).resolve_for_boot()


def test_nothing_installed(tmp_path):
    with pytest.raises(pl.PayloadError, match="no BEGIA payload is installed"):
        pl.Slots(tmp_path / "empty").resolve_for_boot()


def test_prune_keeps_active_and_previous(tmp_path):
    slots_dir = two_builds(tmp_path)
    pl.install(make_payload(tmp_path / "x.begia", build="v0.8"), slots_dir)
    s = pl.Slots(slots_dir)
    assert s.prune() == ["v0.8"]
    assert sorted(b["build"] for b in s.installed()) == ["v0.10", "v0.9"]


def test_torn_state_file_starts_clean(tmp_path):
    slots_dir = tmp_path / "slots"
    slots_dir.mkdir()
    (slots_dir / "state.json").write_text('{"active": "v0.9", "boo')
    assert pl.Slots(slots_dir).state["active"] is None
