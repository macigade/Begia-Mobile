"""The phone's own licence check, outside any payload.

The shell decides two things a payload cannot be trusted to decide for
itself: whether this phone may show a laptop's BEGIA (companion mode), and
what the native screens say about the licence before a page exists. So it
carries a verifier of its own - a port of verify() from the Licence
Manager's reference (IBA-LICENCE-CODE begialic/format.py, record v2),
replaying the same vectors (tests/vectors/licence-vectors.json). Design:
IBA-CODE docs/LICENSING-DESIGN.md, sections 2.3 and 4.

A licence file is {"licence": record, "key_id", "alg": "ed25519", "sig"};
the signature is Ed25519 over canonical(record) only. The keys are the
APK's (trust.LICENCE_KEYS): a licence cannot bring the key that vouches for
it. The file lives at <filesDir>/data/licence.json, where the service
(POST /api/licence) writes it; the shell only reads it.

Python 3.11, cryptography only; never raises from verify() or status().
"""
from __future__ import annotations

import base64
import hashlib
import json
from datetime import date, timedelta
from pathlib import Path
from typing import Optional

from . import trust

PRODUCT = "BEGIA"
RECORD_V = 2                 # the newest record this code understands
CODE_GROUPS = 5              # XXXX-XXXX-XXXX-XXXX-XXXX: 100 bits
GRACE_DAYS = 14              # after expiry: still runs, says so, then stops
DEVICE = "phone"             # what this verifier runs on (a tablet is a phone)
LICENCE_FILE = "licence.json"


# ------------------------------------------------------------------ codes ---

def code_of(guid: str) -> str:
    """The device code from its identity, "and:<ANDROID_ID>" on a phone. The
    raw id never leaves the phone: only this code is shown or sent."""
    raw = base64.b32encode(hashlib.sha256(guid.encode("utf-8")).digest()).decode("ascii")
    flat = raw[:CODE_GROUPS * 4]
    return "-".join(flat[i:i + 4] for i in range(0, len(flat), 4))


def norm_code(code: str) -> str:
    """As typed: case, dashes and spaces are not what makes it wrong."""
    return "".join(ch for ch in (code or "").upper() if ch.isalnum())


def pretty_code(code: str) -> str:
    c = norm_code(code)
    return "-".join(c[i:i + 4] for i in range(0, len(c), 4))


def valid_code(code: str) -> bool:
    c = norm_code(code)
    return len(c) == CODE_GROUPS * 4 and set(c) <= set("ABCDEFGHIJKLMNOPQRSTUVWXYZ234567")


def canonical(record: dict) -> bytes:
    """The one serialisation that is signed and verified."""
    return json.dumps(record, sort_keys=True, separators=(",", ":"), ensure_ascii=True).encode("utf-8")


def _day(text) -> Optional[date]:
    if text in (None, ""):
        return None
    return date.fromisoformat(str(text)[:10])


# ----------------------------------------------------------------- verify ---

def verify(doc, machine: str, today: Optional[date] = None, keys: Optional[dict] = None,
           device: Optional[str] = DEVICE, clock_max: Optional[date] = None) -> dict:
    """What a licence document is worth on this device, today. Never raises.

    The reference's verify(), word for word in what it decides. `device` is
    this verifier's kind - "phone" unless a test says otherwise; None skips
    the check, as a v1 verifier does. `clock_max` is the latest date the
    phone has run on: expiry is judged against the later of it and today, so
    turning the clock back cannot extend a licence. `keys` defaults to the
    APK's licence keys.
    Returns {ok, why, customer, expires, days_left, issued, key_id, machine,
    id, device, name, holder, v}."""
    keys = trust.LICENCE_KEYS if keys is None else keys
    today = today or date.today()
    judge = max(today, clock_max) if clock_max else today
    out = {"ok": False, "why": "", "customer": "", "expires": None, "days_left": None,
           "issued": None, "key_id": "", "machine": "", "id": "", "device": "pc",
           "name": "", "holder": "", "v": 1}
    if not isinstance(doc, dict) or not isinstance(doc.get("licence"), dict):
        out["why"] = "this is not a BEGIA licence file"
        return out
    rec = doc["licence"]
    out.update(customer=str(rec.get("customer", "")), key_id=str(doc.get("key_id", "")),
               machine=str(rec.get("machine", "")), id=str(rec.get("id", "") or ""),
               device=str(rec.get("device", "pc") or "pc"), name=str(rec.get("name", "") or ""),
               holder=str(rec.get("holder", "") or ""))
    try:
        out["v"] = int(rec.get("v", 1) or 1)
        out["expires"] = _day(rec.get("expires"))
        out["issued"] = _day(rec.get("issued"))
    except (ValueError, TypeError):
        out["why"] = "the licence carries a date that is not a date"
        return out
    pub = keys.get(out["key_id"])
    if not pub:
        out["why"] = "the licence is signed by a key this build does not know"
        return out
    try:
        from cryptography.exceptions import InvalidSignature
        from cryptography.hazmat.primitives.asymmetric import ed25519
        ed25519.Ed25519PublicKey.from_public_bytes(bytes.fromhex(pub)).verify(
            bytes.fromhex(str(doc.get("sig", ""))), canonical(rec))
    except (InvalidSignature, ValueError, TypeError):
        out["why"] = "the licence has been altered since it was issued (the signature does not match)"
        return out
    if rec.get("product") != PRODUCT:
        out["why"] = f"the licence is for {rec.get('product') or 'another product'}, not {PRODUCT}"
        return out
    if out["v"] > RECORD_V:
        out["why"] = "the licence was issued by a newer Licence Manager than this build understands - update BEGIA"
        return out
    if norm_code(out["machine"]) != norm_code(machine):
        out["why"] = (f"the licence is for device {out['machine']}, and this is "
                      f"{pretty_code(machine)} - it was issued for another device")
        return out
    if device and out["device"] != device:
        out["why"] = ("this licence is for a phone, not this computer" if out["device"] == "phone"
                      else "this licence is for a computer, not this phone")
        return out
    if out["issued"] and out["issued"] > today + timedelta(days=1):
        out["why"] = "the licence is dated in the future - set the clock"
        return out
    if out["expires"] is not None:
        out["days_left"] = (out["expires"] - judge).days
        if out["days_left"] < -GRACE_DAYS:
            out["why"] = (f"the licence expired on {out['expires'].isoformat()}, and the "
                          f"{GRACE_DAYS}-day grace period after it ended on "
                          f"{(out['expires'] + timedelta(days=GRACE_DAYS)).isoformat()}")
            return out
    out["ok"] = True
    return out


# ------------------------------------------------------------ documents -----

def parse(text: str):
    """A licence or a pack, as pasted, scanned or read: the parsed dict.
    Raises ValueError with a sentence."""
    text = (text or "").strip().strip("`")
    try:
        doc = json.loads(text)
    except ValueError:
        raise ValueError("this is not a licence: the text is not the JSON a licence file holds")
    if not isinstance(doc, dict):
        raise ValueError("this is not a licence file")
    return doc


def pick_from_pack(doc: dict, machine: str) -> dict:
    """A pack's entry for this device, or the document itself if it is not a
    pack. Raises ValueError when none of the pack is for it."""
    if not doc.get("begia_licence_pack"):
        return doc
    entries = [d for d in doc.get("licences") or [] if isinstance(d, dict)]
    for d in entries:
        if norm_code((d.get("licence") or {}).get("machine", "")) == norm_code(machine):
            return d
    raise ValueError(f"none of the {len(entries)} licences in this pack is for this device "
                     f"(code {pretty_code(machine)})")


# --------------------------------------------------------------- status -----

def status(files_dir: str, device_id: str, today: Optional[date] = None,
           clock_max: Optional[date] = None) -> dict:
    """This phone's licence as the shell sees it: verify() of
    <files_dir>/data/licence.json against this phone's code, plus "code"
    (what the native screens and the Licence Manager show) and "present"
    (whether a file is there at all). `device_id` is "and:<ANDROID_ID>".
    Never raises."""
    code = code_of(device_id) if device_id else ""
    path = Path(files_dir) / "data" / LICENCE_FILE
    present = path.is_file()
    if not present:
        out = verify(None, code, today, clock_max=clock_max)
        out["why"] = "no licence on this phone yet"
    else:
        try:
            out = verify(json.loads(path.read_text(encoding="utf-8")), code, today, clock_max=clock_max)
        except (OSError, ValueError) as e:
            out = verify(None, code, today, clock_max=clock_max)
            out["why"] = f"the licence file could not be read ({e})"
    out["code"] = code
    out["present"] = present
    for k in ("expires", "issued"):
        if out.get(k) is not None:
            out[k] = out[k].isoformat()
    return out
