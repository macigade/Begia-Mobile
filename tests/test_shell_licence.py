"""The shell's licence check: the same answers as the Licence Manager's reference.

runtime/begia_shell/licence.py is a port of verify() from IBA-LICENCE-CODE
begialic/format.py. It replays the reference's vectors - copied here as
tests/vectors/licence-vectors.json, pinned by its sha256 - with each case's
own device kind, and reads this phone's licence file the way the native
screens and the companion gate will.

    .venv-phone\\Scripts\\python -m pytest tests -q
"""
from __future__ import annotations

import hashlib
import json
import sys
from datetime import date
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "runtime"))

from begia_shell import licence as L                                  # noqa: E402
from begia_shell import trust                                         # noqa: E402

VEC = Path(__file__).resolve().parent / "vectors" / "licence-vectors.json"
DATA = json.loads(VEC.read_text(encoding="utf-8"))
KEYS = {DATA["key_id"]: DATA["public"]}


def _day(s):
    return date.fromisoformat(s) if s else None


def test_the_vectors_are_the_pinned_ones():
    # CRLF normalised, as the manager's own test does
    sha = hashlib.sha256(VEC.read_bytes().replace(b"\r\n", b"\n")).hexdigest()
    assert VEC.with_suffix(".sha256").read_text(encoding="utf-8").split()[0] == sha


def test_the_vector_key_is_not_trusted():
    # signed by a throwaway key whose private half was never written
    assert DATA["key_id"] == "4bb7ca0e479043a8"
    assert DATA["key_id"] not in trust.LICENCE_KEYS
    assert DATA["public"] not in trust.LICENCE_KEYS.values()


def test_the_licence_key_is_the_managers():
    assert trust.LICENCE_KEYS == {
        "43fb53eea16a0945": "f470837787fcc13c49b72888ee2a921ca82e24f64ceede67112f1dfb80555124"}
    # a key id is the first 16 hex digits of the public key's sha256
    for kid, pub in trust.LICENCE_KEYS.items():
        assert hashlib.sha256(bytes.fromhex(pub)).hexdigest()[:16] == kid


def test_the_format_matches_the_reference():
    assert (L.PRODUCT, L.RECORD_V, L.GRACE_DAYS, L.CODE_GROUPS) == ("BEGIA", DATA["record_v"],
                                                                    DATA["grace_days"], 5)


@pytest.mark.parametrize("c", DATA["cases"], ids=[c["name"] for c in DATA["cases"]])
def test_case(c):
    v = L.verify(c["doc"], c["machine"], _day(c["today"]), KEYS, device=c["device"],
                 clock_max=_day(c["clock_max"]))
    assert v["ok"] is c["ok"], v["why"]
    assert c["why_has"] in v["why"]


@pytest.mark.parametrize("p", DATA["packs"], ids=lambda p: p["name"])
def test_pack(p):
    if p.get("error_has"):
        with pytest.raises(ValueError, match=p["error_has"]):
            L.pick_from_pack(p["pack"], p["machine"])
        return
    doc = L.pick_from_pack(p["pack"], p["machine"])
    assert doc["licence"]["id"] == p["picks"] and doc["licence"]["device"] == p["device"]


def test_a_phone_verifies_as_a_phone_by_default():
    # the cases a phone must refuse, judged with the default device kind
    pc = next(c for c in DATA["cases"] if c["name"] == "pc-on-phone")
    v = L.verify(pc["doc"], pc["machine"], _day(pc["today"]), KEYS)
    assert v["ok"] is False and "not this phone" in v["why"]
    ph = next(c for c in DATA["cases"] if c["name"] == "v2-phone-ok")
    assert L.verify(ph["doc"], ph["machine"], _day(ph["today"]), KEYS)["ok"] is True


def test_the_untrusted_vector_key_is_refused_with_the_builds_keys():
    c = next(c for c in DATA["cases"] if c["name"] == "v2-phone-ok")
    v = L.verify(c["doc"], c["machine"], _day(c["today"]))     # trust.LICENCE_KEYS
    assert v["ok"] is False and "does not know" in v["why"]


# ------------------------------------------------------------- codes ----------

def test_a_device_code_is_five_groups_from_the_id():
    code = L.code_of("and:0123456789abcdef")
    assert L.valid_code(code) and len(code) == 24 and code.count("-") == 4
    # the same id gives the same code; another gives another
    assert code == L.code_of("and:0123456789abcdef") != L.code_of("and:0123456789abcdee")
    # typed with spaces, small letters or no dashes it is the same code
    assert L.norm_code(code.lower().replace("-", " ")) == L.norm_code(code)
    assert L.pretty_code(L.norm_code(code)) == code


def test_the_code_is_the_references(tmp_path):
    # the Licence Manager computes the same code from the same identity
    ref = Path(r"C:\Users\user\IBA-LICENCE-CODE\begialic\format.py")
    if not ref.is_file():
        pytest.skip("the Licence Manager repo is not on this machine")
    import importlib.util
    spec = importlib.util.spec_from_file_location("begialic_format", ref)
    F = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(F)
    for guid in ("and:0123456789abcdef", "and:ffffffffffffffff", "win:{12345678-ABCD}"):
        assert L.code_of(guid) == F.code_of(guid)


# ------------------------------------------------------------- status ---------

def _phone(tmp_path, doc=None, raw=None):
    data = tmp_path / "data"
    data.mkdir()
    if doc is not None:
        (data / "licence.json").write_text(json.dumps(doc), encoding="utf-8")
    if raw is not None:
        (data / "licence.json").write_text(raw, encoding="utf-8")
    return str(tmp_path)


def test_no_file_says_so_and_shows_the_code(tmp_path):
    s = L.status(_phone(tmp_path), "and:0123456789abcdef")
    assert s["ok"] is False and s["present"] is False
    assert s["why"] == "no licence on this phone yet"
    assert s["code"] == L.code_of("and:0123456789abcdef")


def test_a_damaged_file_is_said_not_raised(tmp_path):
    s = L.status(_phone(tmp_path, raw="{not json"), "and:0123456789abcdef")
    assert s["ok"] is False and s["present"] is True and "could not be read" in s["why"]


def test_status_judges_the_file_against_this_phones_code(tmp_path, monkeypatch):
    # the vector key stands in for the manager's, for this test only
    monkeypatch.setattr(trust, "LICENCE_KEYS", KEYS)
    c = next(c for c in DATA["cases"] if c["name"] == "v2-phone-ok")
    path = _phone(tmp_path, doc=c["doc"])
    # a phone whose code is not the licence's is refused, by its own code
    s = L.status(path, "and:0123456789abcdef", today=_day(c["today"]))
    assert s["ok"] is False and "another device" in s["why"]
    assert s["code"] == L.code_of("and:0123456789abcdef")
    # the dates come back as text, for the page and the bridge
    assert s["issued"] == c["doc"]["licence"]["issued"]
    json.dumps(s)
