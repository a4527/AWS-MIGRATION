#!/usr/bin/env bash
# Post-deploy smoke test (CICD-02/03). Exits non-zero on the first failure.
#   scripts/smoke.sh <base-url> [stage-header-value]
# With a stage value, every request carries X-Deploy-Stage so the ALB routes it
# to that service (staging). Each request has its own X-Request-Id
# (smoke-<run>-<step>) so a failure can be traced in the backend log.
set -euo pipefail

BASE_URL=${1:?base url}
STAGE=${2:-}
RUN=${SMOKE_RUN_ID:-$(date +%s)}
SCAN_WAIT_SECONDS=${SMOKE_SCAN_WAIT_SECONDS:-180}
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
STEP=0

req() {  # req <expected-status> <curl args...> ; body -> $WORK/body
  local expected=$1; shift
  STEP=$((STEP + 1))
  local rid="smoke-$RUN-$STEP"
  local headers=(-H "X-Request-Id: $rid")
  [ -n "$STAGE" ] && headers+=(-H "X-Deploy-Stage: $STAGE")
  local code
  code=$(curl -sS -o "$WORK/body" -w '%{http_code}' --max-time 30 "${headers[@]}" "$@") || code=000
  if [ "$code" != "$expected" ]; then
    echo "SMOKE FAIL step=$STEP request_id=$rid expected=$expected got=$code body=$(head -c 300 "$WORK/body" 2>/dev/null)" >&2
    exit 1
  fi
}

EMAIL="smoke-$RUN@example.com"
PASSWORD='Smoke-Password-123!'

req 200 "$BASE_URL/api/health"
req 200 -X POST "$BASE_URL/api/auth/signup" -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\",\"name\":\"smoke\"}"
req 200 -X POST "$BASE_URL/api/auth/login" -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}"
TOKEN=$(jq -r '.data.accessToken' "$WORK/body")
AUTH=(-H "Authorization: Bearer $TOKEN")

printf 'smoke %s %s\n' "$RUN" "$STAGE" > "$WORK/upload.txt"
req 200 -X POST "$BASE_URL/api/files" "${AUTH[@]}" -F "file=@$WORK/upload.txt"
FILE_ID=$(jq -r '.data.fileId' "$WORK/body")

# FILE-03: a new file is downloadable only after the scan marks it CLEAN.
deadline=$(( $(date +%s) + SCAN_WAIT_SECONDS ))
while :; do
  req 200 "$BASE_URL/api/files/$FILE_ID" "${AUTH[@]}"
  scan=$(jq -r '.data.scanStatus' "$WORK/body")
  # NOT_SCANNED: environment without a scanner (on-prem), downloadable.
  if [ "$scan" = "CLEAN" ] || [ "$scan" = "NOT_SCANNED" ]; then break; fi
  if [ "$scan" != "PENDING" ] || [ "$(date +%s)" -ge "$deadline" ]; then
    echo "SMOKE FAIL file $FILE_ID scanStatus=$scan (waited ${SCAN_WAIT_SECONDS}s)" >&2; exit 1
  fi
  sleep 5
done

req 200 "$BASE_URL/api/files/$FILE_ID/download" "${AUTH[@]}"
cmp -s "$WORK/body" "$WORK/upload.txt" || { echo "SMOKE FAIL download content differs" >&2; exit 1; }
req 200 -X DELETE "$BASE_URL/api/files/$FILE_ID" "${AUTH[@]}"
req 404 "$BASE_URL/api/files/$FILE_ID" "${AUTH[@]}"

echo "SMOKE OK run=$RUN stage=${STAGE:-production} steps=$STEP file=$FILE_ID"
