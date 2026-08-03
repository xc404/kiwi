#!/usr/bin/env bash
# Create Kiwi market Raw + Maven hosted repositories on Nexus 3.
set -euo pipefail

NEXUS_URL="${NEXUS_URL:-http://localhost:8081}"
NEXUS_USER="${NEXUS_USER:-admin}"
NEXUS_PASSWORD="${NEXUS_PASSWORD:?Set NEXUS_PASSWORD}"

auth=(-u "${NEXUS_USER}:${NEXUS_PASSWORD}")
api="${NEXUS_URL}/service/rest/v1/repositories"

create_raw() {
  local name="$1"
  if curl -sf "${auth[@]}" "${api}/${name}" >/dev/null 2>&1; then
    echo "Repository ${name} already exists"
    return
  fi
  curl -sf "${auth[@]}" -X POST "${api}/hosted/raw" \
    -H 'Content-Type: application/json' \
    -d "{
      \"name\": \"${name}\",
      \"online\": true,
      \"storage\": {
        \"blobStoreName\": \"default\",
        \"strictContentTypeValidation\": false,
        \"writePolicy\": \"ALLOW\"
      },
      \"raw\": { \"contentDisposition\": \"ATTACHMENT\" }
    }"
  echo "Created raw repository: ${name}"
}

create_maven() {
  local name="$1"
  if curl -sf "${auth[@]}" "${api}/${name}" >/dev/null 2>&1; then
    echo "Repository ${name} already exists"
    return
  fi
  curl -sf "${auth[@]}" -X POST "${api}/hosted/maven" \
    -H 'Content-Type: application/json' \
    -d "{
      \"name\": \"${name}\",
      \"online\": true,
      \"storage\": {
        \"blobStoreName\": \"default\",
        \"strictContentTypeValidation\": true,
        \"writePolicy\": \"ALLOW\"
      },
      \"maven\": {
        \"versionPolicy\": \"RELEASE\",
        \"layoutPolicy\": \"STRICT\"
      }
    }"
  echo "Created maven repository: ${name}"
}

echo "Waiting for Nexus at ${NEXUS_URL}..."
for i in $(seq 1 60); do
  if curl -sf "${NEXUS_URL}/service/rest/v1/status" >/dev/null 2>&1; then
    break
  fi
  sleep 5
done

create_raw "kiwi-market-raw"
create_maven "kiwi-market-plugins"
echo "Done. Raw base: ${NEXUS_URL}/repository/kiwi-market-raw/"
