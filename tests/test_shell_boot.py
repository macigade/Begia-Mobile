"""boot.py: which failed starts roll back, and which do not.

A payload that never answers is rolled back on the next start. A payload that
says the phone's data folder cannot be used (app/config.py's StartupError, at
the import of app.main) is not: every other build would meet the same folder,
and a good update must not be thrown away for it.

    .venv-phone\\Scripts\\python -m pytest tests -q
"""
from __future__ import annotations

import sys
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "runtime"))
sys.path.insert(0, str(Path(__file__).resolve().parent))

from begia_shell import boot                                          # noqa: E402
from begia_shell import payload as pl                                 # noqa: E402
from test_shell_payload import make_payload                           # noqa: E402

# The desktop's shape (app/config.py raises while app.main imports it), so
# the failed import also takes app.config back out of sys.modules.
REFUSING_FOLDER = {
    "app/config.py": b"class StartupError(RuntimeError):\n    pass\n\n"
                     b"raise StartupError('BEGIA cannot write to the data folder"
                     b" (No space left on device).')\n",
    "app/main.py": b"from . import config\napp = object()\n",
}


@pytest.fixture
def own_app(monkeypatch):
    """Each test imports its own slot's app package, and leaves none behind."""
    monkeypatch.setattr(sys, "path", list(sys.path))
    for k in ("TRIALREC_DATA_DIR", "TRIALREC_UI_DIR"):
        monkeypatch.setenv(k, "")           # boot.prepare sets them; restored after

    def drop():
        for m in [m for m in sys.modules if m == "app" or m.startswith("app.")]:
            del sys.modules[m]
    drop()
    yield
    drop()


def updated_to(tmp_path, new_files: dict) -> Path:
    """v0.9 ran, then v0.10 (with these files) was installed and activated."""
    slots_dir = tmp_path / "slots"
    pl.install(make_payload(tmp_path / "old.begia", build="v0.9"), slots_dir)
    pl.install(make_payload(tmp_path / "new.begia", build="v0.10", more=new_files), slots_dir)
    s = pl.Slots(slots_dir)
    s.activate("v0.9")
    s.activate("v0.10")
    return slots_dir


def test_a_data_folder_that_refuses_is_not_rolled_back(tmp_path, own_app):
    slots_dir = updated_to(tmp_path, REFUSING_FOLDER)
    with pytest.raises(boot.DataError, match="No space left on device"):
        boot.start(slots_dir, tmp_path / "data", port=0, health_timeout=2)
    s = pl.Slots(slots_dir)
    assert s.state["booting"] is None
    assert s.state["active"] == "v0.10" and not s.bad_builds()
    # the next start boots the same build again, with no rollback note
    assert pl.Slots(slots_dir).resolve_for_boot() == (s.slot_path("v0.10"), None)


def test_any_other_failure_at_import_still_rolls_back(tmp_path, own_app):
    slots_dir = updated_to(tmp_path, {"app/main.py": b"raise RuntimeError('a bug in the payload')\n"})
    with pytest.raises(RuntimeError, match="a bug in the payload"):
        boot.start(slots_dir, tmp_path / "data", port=0, health_timeout=2)
    fresh = pl.Slots(slots_dir)
    assert fresh.state["booting"] == "v0.10"
    slot, note = fresh.resolve_for_boot()
    assert slot.name == "v0.9"
    assert note == "BEGIA v0.10 did not start; went back to v0.9"


def test_the_boot_test_says_failed_rather_than_a_traceback(tmp_path, own_app, capsys):
    slots_dir = tmp_path / "slots"
    payload = make_payload(tmp_path / "new.begia", build="v0.10", more=REFUSING_FOLDER)
    assert boot.main(["--payload", str(payload), "--slots", str(slots_dir),
                      "--data", str(tmp_path / "data"), "--port", "0",
                      "--health-timeout", "2", "--exit-when-healthy"]) == 1
    assert "FAILED: BEGIA cannot write to the data folder" in capsys.readouterr().out
    assert pl.Slots(slots_dir).state["booting"] is None
