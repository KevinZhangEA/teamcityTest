#!/bin/bash
set -e
_script_name=$(basename "$0" .sh)
source "$(dirname "$0")/_build_common.sh" start "${_script_name}"
source "$(dirname "$0")/env.sh"

# 简化生成 placeholder.out，内容为 build number
: "${BUILD_NUMBER:=unknown}"
echo "$BUILD_NUMBER" > placeholder.out

source "$(dirname "$0")/_build_common.sh" end "${_script_name}"
