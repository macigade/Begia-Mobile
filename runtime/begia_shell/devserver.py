"""A loopback door for the developer's push script - debug builds only.

    adb forward tcp:8081 tcp:8081
    curl -T dist\\begia-payload.begia http://127.0.0.1:8081/payload

installs the payload, activates it and restarts the recorder: the ten-second
loop from an edit on the laptop to the phone running it, with no Gradle in
between. Loopback only, and only when the shell says it is a debug build,
because a payload is code.
"""
from __future__ import annotations

import json
import os
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

from . import payload as pl


def _post_licence(service_port: int, text: str):
    """POST the licence to the phone's own service, which verifies it for this
    phone and writes it (the shell never writes data/licence.json itself).
    Returns (status, answer)."""
    import urllib.error
    import urllib.request
    req = urllib.request.Request(f"http://127.0.0.1:{service_port}/api/licence",
                                 data=json.dumps({"text": text}).encode("utf-8"), method="POST",
                                 headers={"Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=10) as r:
            return r.status, json.loads(r.read() or b"{}")
    except urllib.error.HTTPError as e:
        try:
            return e.code, json.loads(e.read() or b"{}")
        except ValueError:
            return e.code, {"error": f"the service answered {e.code}"}
    except OSError as e:
        return 502, {"error": f"the service is not answering: {e}"}


def serve(files_dir: str, port: int, restart=None, service_port: int = 0) -> ThreadingHTTPServer:
    files = Path(files_dir)
    slots = files / "slots"
    service_port = service_port or port - 1

    class Handler(BaseHTTPRequestHandler):
        def log_message(self, fmt, *args):      # logcat gets python.stdout
            print("dev: " + fmt % args, flush=True)

        def _send(self, code: int, obj: dict) -> None:
            body = json.dumps(obj, indent=1).encode("utf-8")
            self.send_response(code)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        def do_GET(self):
            if self.path == "/info":
                from .android import info_dict
                self._send(200, info_dict(files_dir))
            elif self.path == "/config":
                # the recorder's config.json as it is on disk, for a test
                # harness to read, change and PUT back
                p = files / "data" / "config.json"
                self._send(200, json.loads(p.read_text(encoding="utf-8")) if p.is_file() else {})
            elif self.path == "/licence":
                # this phone's licence as the shell judges it; the device id is
                # the one boot.prepare handed a licensable payload
                from . import licence
                dev = os.environ.get("BEGIA_DEVICE_ID", "")
                self._send(200, licence.status(files_dir, dev) if dev else
                           {"error": "no device id: the active payload is not licensable"})
            else:
                self._send(404, {"error": "GET /info, GET /config, GET /licence, PUT /payload, "
                                          "PUT /config, PUT /licence, POST /restart"})

        def do_POST(self):
            if self.path != "/restart":
                return self._send(404, {"error": "POST /restart"})
            self._send(200, {"restarting": restart is not None})
            if restart is not None:
                threading.Timer(0.5, restart.run).start()

        def _read_body(self) -> bytes:
            n = int(self.headers.get("Content-Length") or 0)
            out = bytearray()
            while len(out) < n:
                chunk = self.rfile.read(min(65536, n - len(out)))
                if not chunk:
                    break
                out += chunk
            return bytes(out)

        def do_PUT(self):
            if self.path == "/licence":
                # a licence file's text, handed to the service's own install
                # route (IBA-CODE docs/LICENSING-DESIGN.md, flow 10)
                body = self._read_body()
                code, answer = _post_licence(service_port, body.decode("utf-8", "replace"))
                return self._send(code, answer)
            if self.path == "/config":
                # what the UI cannot set (the UDP channel's signal list lives
                # only in config.json): written whole, applied on restart
                body = self._read_body()
                try:
                    json.loads(body)
                except ValueError:
                    return self._send(422, {"error": "the body is not JSON"})
                data = files / "data"
                data.mkdir(parents=True, exist_ok=True)
                tmp = data / "config.json.tmp"
                tmp.write_bytes(body)
                os.replace(tmp, data / "config.json")
                return self._send(200, {"written": "data/config.json", "bytes": len(body), "restart_to_apply": True})
            if self.path != "/payload":
                return self._send(404, {"error": "PUT /payload or PUT /config"})
            n = int(self.headers.get("Content-Length") or 0)
            incoming = files / "incoming-dev.begia"
            with open(incoming, "wb") as f:
                left = n
                while left > 0:
                    chunk = self.rfile.read(min(65536, left))
                    if not chunk:
                        break
                    f.write(chunk)
                    left -= len(chunk)
            try:
                _slot, m = pl.install(incoming, slots)
                pl.Slots(slots).activate(m["build"])
            except pl.PayloadError as e:
                return self._send(422, {"error": str(e)})
            self._send(200, {"installed": m["build"], "version": m["version"],
                             "restarting": restart is not None})
            if restart is not None:
                threading.Timer(0.5, restart.run).start()

    srv = ThreadingHTTPServer(("127.0.0.1", port), Handler)
    srv.daemon_threads = True
    threading.Thread(target=srv.serve_forever, name="begia-dev", daemon=True).start()
    print(f"dev server on 127.0.0.1:{port} (PUT /payload, GET /info)", flush=True)
    return srv
