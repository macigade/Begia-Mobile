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
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

from . import payload as pl


def serve(files_dir: str, port: int, restart=None) -> ThreadingHTTPServer:
    files = Path(files_dir)
    slots = files / "slots"

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
            else:
                self._send(404, {"error": "GET /info or PUT /payload"})

        def do_PUT(self):
            if self.path != "/payload":
                return self._send(404, {"error": "PUT /payload"})
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
