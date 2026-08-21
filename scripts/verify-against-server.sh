#!/usr/bin/env bash
#
# Replays the app's REAL payloads against a REAL server.
#
#   ./scripts/verify-against-server.sh
#   BASE_URL=https://tenant.dodealcrm.com/api TENANT_HOST=tenant.dodealcrm.com \
#     LOGIN_ID=x PASSWORD=y ./scripts/verify-against-server.sh
#
# Unit tests only prove the app agrees with itself. This proves the app and the
# server agree — the check nobody had run, which is why command polling was dead
# for weeks without one error line anywhere.
#
# The payloads come from PayloadDumpTest, so they are the exact bytes the app
# puts on the wire, not a hand-written approximation that can drift.
set -euo pipefail
cd "$(dirname "$0")/.."

BASE_URL="${BASE_URL:-http://127.0.0.1:8000/api}"
TENANT_HOST="${TENANT_HOST:-tenant.localhost}"
LOGIN_ID="${LOGIN_ID:-nasir3}"
PASSWORD="${PASSWORD:-local123}"
DEVICE_UID="${DEVICE_UID:-contract-check-device}"

DUMP=app/build/contract
[[ -f "$DUMP/heartbeat.json" ]] || { echo "payloads missing — run ./scripts/test-tracking.sh first"; exit 1; }

PASS=0; FAIL=0
body=/tmp/dt_contract_body.json

call() { # name expected method path [json-file|-] [extra headers...]
    local name="$1" expect="$2" method="$3" path="$4" file="${5:--}"; shift 5 || shift 4
    local args=(-s -o "$body" -w '%{http_code}' -H "Host: ${TENANT_HOST}" -H 'Accept: application/json'
                -X "$method" "${BASE_URL}${path}")
    [[ "$file" != "-" ]] && args+=(-H 'Content-Type: application/json' --data-binary "@${file}")
    for h in "$@"; do args+=(-H "$h"); done
    local code; code="$(curl "${args[@]}")"
    if [[ "$code" == "$expect" ]]; then
        echo "  ✓ ${name} (${code})"; PASS=$((PASS+1))
    else
        echo "  ✗ ${name} — expected ${expect}, got ${code}"; head -c 300 "$body"; echo; FAIL=$((FAIL+1))
    fi
}

json() { python3 -c "import json,sys;print(eval(sys.argv[1],{'d':json.load(open('$body'))}))" "$1"; }

echo "▸ Sign in"
call "login" 200 POST /login <(printf '{"loginId":"%s","password":"%s"}' "$LOGIN_ID" "$PASSWORD")
TOKEN="$(json "d.get('data',d)['token']")"
AUTH="Authorization: Bearer ${TOKEN}"
DEV="X-Device-Id: ${DEVICE_UID}"

echo "▸ The app's own heartbeat payload"
call "heartbeat accepted verbatim" 200 POST /device/heartbeat "$DUMP/heartbeat.json" "$AUTH" "$DEV"

echo "▸ The app's own batch upload (points with different fields)"
call "batch accepted verbatim" 200 POST /device/locations "$DUMP/locations-batch.json" "$AUTH" "$DEV"
echo "    stored=$(json "d['stored']") rejected=$(json "d['rejected']")"

echo "▸ Policy, read the way the app reads it"
call "policy" 200 GET /device/policy - "$AUTH" "$DEV"
python3 - "$body" <<'PY'
import json, sys
d = json.load(open(sys.argv[1]))
# Every field the app's DTO actually binds to.
required = [
    "tracking_enabled", "timezone", "working_hours", "location_interval_seconds",
    "distance_filter_meters", "high_accuracy_enabled", "heartbeat_interval_seconds",
    "offline_queue_limit", "policy_version",
]
missing = [k for k in required if k not in d]
assert not missing, f"server does not send: {missing}"
assert isinstance(d["working_hours"], dict) and len(d["working_hours"]) == 7
print("    every field the agent binds to is present")
PY

echo "▸ A command, end to end, with a FAILED ack"
DEVICE_ID="$(json "d['device_id']")"
call "queue SYNC_NOW" 200 POST "/device-tracking/devices/${DEVICE_ID}/command" \
     <(printf '{"command":"SYNC_NOW"}') "$AUTH"
COMMAND_ID="$(json "d['command']['id']")"

call "device polls" 200 GET /device/commands - "$AUTH" "$DEV"
python3 - "$body" <<'PY'
import json, sys
d = json.load(open(sys.argv[1]))
c = d["commands"][0]
# The three fields whose names the app had wrong. If any is missing, polling dies silently.
for key in ("command_id", "command", "issued_at"):
    assert key in c, f"server response has no {key}: {c}"
print(f"    command {c['command_id']} = {c['command']}")
PY

sed "s/REPLACED_BY_SCRIPT/${COMMAND_ID}/" "$DUMP/ack-failed.json" > /tmp/dt_ack.json
call "ack it as FAILED" 200 POST "/device/commands/${COMMAND_ID}/ack" /tmp/dt_ack.json "$AUTH" "$DEV"

call "server recorded the failure, not a success" 200 GET "/device-tracking/devices/${DEVICE_ID}" - "$AUTH"
python3 - "$body" "$COMMAND_ID" <<'PY'
import json, sys
d = json.load(open(sys.argv[1])); wanted = int(sys.argv[2])
row = next(c for c in d["commands"] if c["id"] == wanted)
assert row["status"] == "failed", f"expected failed, server stored {row['status']!r}"
print("    status =", row["status"])
PY

echo
echo "── ${PASS} passed, ${FAIL} failed ──"
rm -f "$body" /tmp/dt_ack.json
[[ "$FAIL" == "0" ]]
