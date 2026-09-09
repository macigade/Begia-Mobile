"""The entry points the Kotlin shell calls, exercised without a phone.

`start` is covered end to end by tools/boot_test.py through boot.py; what is
checked here is the part around it - the embedded payload's rules, the
install-then-activate split the confirmation dialog relies on, the report
the screen reads, and the loopback dev server the push script talks to.

    .venv-phone\\Scripts\\python -m pytest tests -q
"""
from __future__ import annotations

import hashlib
import json
import sys
import urllib.error
import urllib.request
import zipfile
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "runtime"))
sys.path.insert(0, str(Path(__file__).resolve().parent))

from begia_shell import android, devserver, payload as pl              # noqa: E402
from test_shell_payload import make_payload                             # noqa: E402


def test_embedded_first_run_becomes_the_active_slot(tmp_path):
    emb = make_payload(tmp_path / "embedded.begia", build="v0.9")
    m = android.ensure_embedded(str(tmp_path), str(emb))
    s = pl.Slots(tmp_path / "slots")
    assert m["build"] == "v0.9" and s.active == "v0.9"


def test_embedded_never_takes_over_a_hot_update(tmp_path):
    emb = make_payload(tmp_path / "embedded.begia", build="v0.9")
    android.ensure_embedded(str(tmp_path), str(emb))
    # the phone was updated over WiFi to v0.10 since the APK shipped
    newer = make_payload(tmp_path / "new.begia", build="v0.10")
    json.loads(android.install(str(newer), str(tmp_path)))
    android.activate("v0.10", str(tmp_path))
    android.ensure_embedded(str(tmp_path), str(emb))          # e.g. the service restarted
    assert pl.Slots(tmp_path / "slots").active == "v0.10"


def test_embedded_same_build_with_new_files_replaces_the_slot(tmp_path):
    """A new APK built from the same dirty tree carries the same build stamp
    with newer files; the slot must follow the APK, not keep the old files."""
    emb = make_payload(tmp_path / "embedded.begia", build="v0.9-dirty")
    android.ensure_embedded(str(tmp_path), str(emb))
    slot = tmp_path / "slots" / "v0.9-dirty"
    assert (slot / "app" / "main.py").read_bytes() == b"app = object()\n"
    # the same stamp, one file edited, re-hashed the way make_payload does
    with zipfile.ZipFile(emb) as z:
        m = json.loads(z.read(pl.MANIFEST))
        rest = {n: z.read(n) for n in z.namelist() if n not in (pl.MANIFEST, "app/main.py")}
    new = b"app = object()  # rebuilt\n"
    m["files"]["app/main.py"] = {"sha256": hashlib.sha256(new).hexdigest(), "size": len(new)}
    with zipfile.ZipFile(emb, "w") as z:
        z.writestr(pl.MANIFEST, json.dumps(m))
        z.writestr("app/main.py", new)
        for n, d in rest.items():
            z.writestr(n, d)
    android.ensure_embedded(str(tmp_path), str(emb))
    assert (slot / "app" / "main.py").read_bytes() == new


def test_install_is_not_activation(tmp_path):
    emb = make_payload(tmp_path / "embedded.begia", build="v0.9")
    android.ensure_embedded(str(tmp_path), str(emb))
    r = json.loads(android.install(str(make_payload(tmp_path / "n.begia", build="v0.10")), str(tmp_path)))
    assert r["build"] == "v0.10" and r["already_active"] is False
    assert pl.Slots(tmp_path / "slots").active == "v0.9", "install must not switch the running version"
    android.activate("v0.10", str(tmp_path))
    assert pl.Slots(tmp_path / "slots").active == "v0.10"
    again = json.loads(android.install(str(tmp_path / "n.begia"), str(tmp_path)))
    assert again["already_active"] is True


def test_activate_refuses_an_uninstalled_build(tmp_path):
    with pytest.raises(pl.PayloadError, match="not installed"):
        android.activate("v9.9", str(tmp_path))


def test_info_reports_what_the_screen_needs(tmp_path):
    emb = make_payload(tmp_path / "embedded.begia", build="v0.9")
    android.ensure_embedded(str(tmp_path), str(emb))
    (tmp_path / android.LAST_BOOT).write_text(json.dumps({"ok": True, "build": "v0.9", "at": "t"}))
    i = json.loads(android.info(str(tmp_path)))
    assert i["shell"] == pl.SHELL_VERSION
    assert i["active"] == {"build": "v0.9", "version": "0.9", "created": None}
    assert [x["build"] for x in i["installed"]] == ["v0.9"]
    assert i["last_boot"]["build"] == "v0.9"


class FakeRestart:
    """Stands in for the java.lang.Runnable the service hands over."""
    def __init__(self):
        self.calls = 0

    def run(self):
        self.calls += 1


def test_dev_server_installs_activates_and_asks_for_a_restart(tmp_path):
    emb = make_payload(tmp_path / "embedded.begia", build="v0.9")
    android.ensure_embedded(str(tmp_path), str(emb))
    restart = FakeRestart()
    srv = devserver.serve(str(tmp_path), 0, restart)          # port 0: any free one
    port = srv.server_address[1]
    try:
        body = make_payload(tmp_path / "pushed.begia", build="v0.10").read_bytes()
        req = urllib.request.Request(f"http://127.0.0.1:{port}/payload", data=body, method="PUT")
        with urllib.request.urlopen(req, timeout=10) as r:
            out = json.loads(r.read())
        assert out == {"installed": "v0.10", "version": "0.9", "restarting": True}
        assert pl.Slots(tmp_path / "slots").active == "v0.10"
        import time
        for _ in range(40):
            if restart.calls:
                break
            time.sleep(0.05)
        assert restart.calls == 1

        with urllib.request.urlopen(f"http://127.0.0.1:{port}/info", timeout=10) as r:
            info = json.loads(r.read())
        assert info["active"]["build"] == "v0.10"

        # the config door, for the rate test: read, change, write, restart
        with urllib.request.urlopen(f"http://127.0.0.1:{port}/config", timeout=10) as r:
            assert json.loads(r.read()) == {}, "no config.json yet"
        cfg = {"endpoint": "", "udp": {"enabled": True, "port": 5555, "signals": [{"name": "Load.S0"}]}}
        req = urllib.request.Request(f"http://127.0.0.1:{port}/config", data=json.dumps(cfg).encode(), method="PUT")
        with urllib.request.urlopen(req, timeout=10) as r:
            assert json.loads(r.read())["restart_to_apply"] is True
        assert json.loads((tmp_path / "data" / "config.json").read_text()) == cfg
        with urllib.request.urlopen(f"http://127.0.0.1:{port}/config", timeout=10) as r:
            assert json.loads(r.read()) == cfg
        req = urllib.request.Request(f"http://127.0.0.1:{port}/config", data=b"not json", method="PUT")
        with pytest.raises(urllib.error.HTTPError) as e:
            urllib.request.urlopen(req, timeout=10)
        assert e.value.code == 422
        req = urllib.request.Request(f"http://127.0.0.1:{port}/restart", data=b"", method="POST")
        with urllib.request.urlopen(req, timeout=10) as r:
            assert json.loads(r.read()) == {"restarting": True}
        for _ in range(40):
            if restart.calls >= 2:
                break
            time.sleep(0.05)
        assert restart.calls == 2

        bad = make_payload(tmp_path / "bad.begia", build="v0.11", tamper=True).read_bytes()
        req = urllib.request.Request(f"http://127.0.0.1:{port}/payload", data=bad, method="PUT")
        with pytest.raises(urllib.error.HTTPError) as e:
            urllib.request.urlopen(req, timeout=10)
        assert e.value.code == 422 and "checksum" in json.loads(e.value.read())["error"]
        assert pl.Slots(tmp_path / "slots").active == "v0.10", "a refused push changes nothing"
    finally:
        srv.shutdown()
        srv.server_close()
