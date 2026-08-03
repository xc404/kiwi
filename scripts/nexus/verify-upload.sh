#!/usr/bin/env bash
# Upload a test file to kiwi-market-raw and verify download.
set -euo pipefail

NEXUS_URL="${NEXUS_URL:-http://localhost:8081}"
NEXUS_USER="${NEXUS_USER:-admin}"
NEXUS_PASSWORD="${NEXUS_PASSWORD:?Set NEXUS_PASSWORD}"
REPO="kiwi-market-raw"
PATH_IN_REPO="market/verify/ping.txt"
UPLOAD_URL="${NEXUS_URL}/repository/${REPO}/${PATH_IN_REPO}"

auth=(-u "${NEXUS_USER}:${NEXUS_PASSWORD}")
tmp=$(mktemp)
echo "kiwi-nexus-verify-$(date +%s)" >"$tmp"

echo "Uploading to ${UPLOAD_URL}..."
curl -sf "${auth[@]}" --upload-file "$tmp" "${UPLOAD_URL}"

echo "Downloading..."
body=$(curl -sf "${auth[@]}" "${UPLOAD_URL}")
if [[ "$body" != "$(cat "$tmp")" ]]; then
  echo "Download mismatch" >&2
  exit 1
fi

rm -f "$tmp"
echo "Nexus upload/download verification OK"
