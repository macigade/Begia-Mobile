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


def test_two_bad_updates_in_a_row_still_land_on_the_last_good_build(tmp_path):
    """Seen on the phone: after one rollback `previous` was empty, so a second
    bad push would have had nothing to fall back to although the build before
    was still installed and had booted fine."""
    slots_dir = tmp_path / "slots"
    for i, b in enumerate(["v0.8", "v0.9", "v0.10", "v0.11"]):
        pl.install(make_payload(tmp_path / f"{b}.begia", build=b), slots_dir)
        s = pl.Slots(slots_dir)
        s.activate(b)
        s.mark_booting(b)
        if i < 2:
            s.mark_good(b)            # 0.8 and 0.9 booted; 0.10 and 0.11 never did
        else:
            slot, note = pl.Slots(slots_dir).resolve_for_boot()
    # 0.11 was booting when the process died -> back to 0.10; 0.10 had never
    # become healthy either (it was left booting too) -> it is bad as well
    s = pl.Slots(slots_dir)
    assert s.state["bad"] and {b["build"] for b in s.state["bad"]} == {"v0.10", "v0.11"}
    assert s.active == "v0.9", s.state
    assert s.state["previous"] == "v0.8"
    slot, note = s.resolve_for_boot()
    assert slot.name == "v0.9"


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


# --- signatures ----------------------------------------------------------
# A payload signed by a key in trust.py is trusted; one signed by any other
# key, or altered after signing, is not; an unsigned one is neither. Only
# require_signed turns "not trusted" into a refusal - and then nothing is
# written - so a phone that never asked keeps installing what it always did.
def _sign(path: Path, monkeypatch=None, trusted=True, tamper=False):
    from cryptography.hazmat.primitives import serialization
    from cryptography.hazmat.primitives.asymmetric import ed25519
    from begia_shell import trust
    key = ed25519.Ed25519PrivateKey.generate()
    pub = key.public_key().public_bytes(serialization.Encoding.Raw, serialization.PublicFormat.Raw)
    key_id = hashlib.sha256(pub).hexdigest()[:16]
    with zipfile.ZipFile(path) as z:
        names = [n for n in z.namelist()]
        raw = z.read(pl.MANIFEST)
        others = {n: z.read(n) for n in names if n != pl.MANIFEST}
    sig = {"alg": "ed25519", "key_id": key_id, "public": pub.hex(), "sig": key.sign(raw).hex()}
    if tamper:
        raw = raw + b" "
    with zipfile.ZipFile(path, "w") as z:
        z.writestr(pl.MANIFEST, raw)
        z.writestr(pl.SIGNATURE, json.dumps(sig))
        for n, d in others.items():
            z.writestr(n, d)
    if monkeypatch is not None and trusted:
        monkeypatch.setitem(trust.TRUSTED_KEYS, key_id, {"name": "test", "public": pub.hex()})
    return key_id


def test_unsigned_is_reported_and_still_installs_by_default(tmp_path):
    z = tmp_path / "p.begia"; make_payload(z)
    st = pl.signature_status(z)
    assert st == {"signed": False, "key_id": None, "trusted": False, "why": "no signature"}
    slot, m = pl.install(z, tmp_path / "slots")
    assert m["signature"]["signed"] is False
    assert pl.slot_signature(slot)["signed"] is False


def test_signed_by_a_trusted_key(tmp_path, monkeypatch):
    z = tmp_path / "p.begia"; make_payload(z)
    kid = _sign(z, monkeypatch)
    st = pl.signature_status(z)
    assert st["signed"] and st["trusted"] and st["key_id"] == kid and st["name"] == "test"
    slot, m = pl.install(z, tmp_path / "slots", require_signed=True)
    assert pl.slot_signature(slot)["trusted"] is True
    assert pl.verify_zip(z)["build"]            # payload.sig is not an "unlisted file"


def test_an_unknown_key_is_not_trusted(tmp_path, monkeypatch):
    z = tmp_path / "p.begia"; make_payload(z)
    _sign(z, monkeypatch, trusted=False)
    st = pl.signature_status(z)
    assert st["signed"] and not st["trusted"] and "does not know" in st["why"]


def test_a_manifest_changed_after_signing_is_not_trusted(tmp_path, monkeypatch):
    z = tmp_path / "p.begia"; make_payload(z)
    _sign(z, monkeypatch, tamper=True)
    st = pl.signature_status(z)
    assert st["signed"] and not st["trusted"] and "does not verify" in st["why"]


def test_require_signed_refuses_before_writing(tmp_path):
    z = tmp_path / "p.begia"; make_payload(z)
    with pytest.raises(pl.PayloadError, match="only installs signed"):
        pl.install(z, tmp_path / "slots", require_signed=True)
    assert not (tmp_path / "slots").exists() or not any((tmp_path / "slots").iterdir())
