#!/usr/bin/env bash
# The screen-off recording test: a trial off the built-in simulator, the
# screen put to sleep, the recorder left alone, then the trial pulled and
# measured with tools/trial_gaps.py.
#
#   SERIAL=192.168.6.140:33757 bash tools/screenoff_test.sh [minutes]
#
# What the first run (2026-09-09, S23, Android 16) taught: wireless
# debugging re-advertises on a NEW port when the screen sleeps, so the adb
# session this script starts with goes stale mid-test. Do not poll the phone
# while it sleeps; wake it at the end, reconnect (`adb mdns services` shows
# the new port), then stop and pull. The interim measurement that run gave:
# 4.6 min screen-off, 2354 samples, max gap 146 ms, no gap over 1 s, the
# same rate as with the screen on; BEGIA must be on the Doze whitelist
# (the battery prompt on first launch).
set -u
ADB="$LOCALAPPDATA/Android/sdk/platform-tools/adb.exe"
SERIAL="${SERIAL:?set SERIAL to the phone's adb serial (ip:port)}"
MIN="${1:-5}"
PKG=com.sarralle.begia
OUT="$(dirname "$0")/../boot-test-data/screenoff"
mkdir -p "$OUT"
log() { echo "$(date +%H:%M:%S) $*"; }
phone() { "$ADB" -s "$SERIAL" shell "$@" 2>/dev/null | tr -d '\r'; }

"$ADB" -s "$SERIAL" forward tcp:18080 tcp:8080 >/dev/null
log "recorder pid $(phone pidof $PKG:recorder)"
log "doze whitelist: $(phone dumpsys deviceidle whitelist | grep -i begia || echo 'NOT whitelisted - accept the battery prompt in the app')"
R=$(curl -sS -m 6 -X POST -H 'Content-Type: application/json' -d '{"name":"screenoff_test","pretrigger_s":0}' http://127.0.0.1:18080/api/trial/start)
log "trial: $R"
FILE=$(echo "$R" | python -c "import json,sys; print(json.load(sys.stdin)['file'])")
sleep 15
log "screen off for $MIN min - adb will go stale now, that is expected"
phone input keyevent KEYCODE_SLEEP
sleep $((MIN * 60))
log "waking the screen; reconnect adb if the serial changed"
"$ADB" -s "$SERIAL" shell input keyevent KEYCODE_WAKEUP 2>/dev/null || {
  NEW=$("$ADB" mdns services 2>/dev/null | grep _adb-tls-connect | awk '{print $3}' | head -1)
  log "reconnecting on $NEW"; "$ADB" connect "$NEW" >/dev/null; SERIAL="$NEW"
  "$ADB" -s "$SERIAL" forward tcp:18080 tcp:8080 >/dev/null
  "$ADB" -s "$SERIAL" shell input keyevent KEYCODE_WAKEUP
}
sleep 3
log "recorder pid $(phone pidof $PKG:recorder)"
log "stop: $(curl -sS -m 6 -X POST -H 'Content-Type: application/json' -d '{}' http://127.0.0.1:18080/api/trial/stop | head -c 160)"
sleep 3
for x in "" -wal -shm; do
  "$ADB" -s "$SERIAL" exec-out run-as $PKG cat "files/data/trials/$FILE$x" > "$OUT/$FILE$x" 2>/dev/null
done
log "pulled $(stat -c %s "$OUT/$FILE") bytes"
python "$(dirname "$0")/trial_gaps.py" "$OUT/$FILE" --gap-ms 1000
