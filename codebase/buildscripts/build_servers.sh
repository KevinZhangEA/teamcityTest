#!/bin/bash
set -e
_script_name=$(basename "$0" .sh)
source "$(dirname "$0")/_build_common.sh" start "${_script_name}"
source "$(dirname "$0")/env.sh"

# ...existing code...

source "$(dirname "$0")/_build_common.sh" end "${_script_name}"
