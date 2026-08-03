#!/usr/bin/env bash
# Simplified publish script (requires python3 and curl)
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
exec python3 "${SCRIPT_DIR}/publish.py" "$@"
