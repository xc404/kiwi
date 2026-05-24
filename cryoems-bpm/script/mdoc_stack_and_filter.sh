#!/usr/bin/env bash
# mdoc_stack_and_filter.sh —— conda env: cryoems → /home/cryoems/ET_test/stack_exclude.py
# 等价后端 SoftwareExe.stack_and_filter；参数透传：--files a,b,c --input_tilt rawtlt --output xxx
set -euo pipefail
exec conda run --no-capture-output -n cryoems \
    python /home/cryoems/ET_test/stack_exclude.py "$@"
