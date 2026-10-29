#!/bin/sh
set -eu
TASK_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
exec /usr/bin/env python3 "$TASK_ROOT/scripts/launch-mango.py" "$@"
