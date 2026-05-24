#!/usr/bin/env bash
# mdoc_stack.sh —— conda env: cryoems → /home/cryoems/bin/py/mdoc/mrc_stack.py
# 等价后端 SoftwareExe.mdoc_stack；参数透传：--files a,b,c --output xxx
set -euo pipefail
exec conda run --no-capture-output -n cryoems \
    python /home/cryoems/bin/py/mdoc/mrc_stack.py "$@"
