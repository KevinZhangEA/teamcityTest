#!/bin/bash
# Usage: source _build_common.sh start|end script_name

now_ms() {
  # Linux / GNU date（快路径）
  if ts="$(date +%s%3N 2>/dev/null)" && [[ "$ts" != *N* ]]; then
    echo "$ts"; return
  fi
  # macOS 若安装了 coreutils 的 gdate
  if command -v gdate >/dev/null 2>&1; then
    gdate +%s%3N; return
  fi
  # 通用兜底（python3）
  if command -v python3 >/dev/null 2>&1; then
    python3 - <<'PY'
import time; print(int(time.time()*1000))
PY
    return
  fi
  # 最后兜底：秒→毫秒
  echo $(( $(date +%s) * 1000 ))
}

pretty_now() { date '+%F %T'; }

case "$1" in
  start)
    export _start_ts
    _start_ts="$(now_ms)"
    echo "[$2] Start: $(pretty_now)"
    ;;
  end)
    _end_ts="$(now_ms)"
    echo "[$2] End:   $(pretty_now)"
    if [[ -z "${_start_ts:-}" ]]; then
      echo "[$2] WARN: _start_ts is empty; did you call 'start' first?"
      return 0
    fi
    _elapsed=$((_end_ts - _start_ts))
    _ms=$((_elapsed % 1000))
    _s=$(( (_elapsed / 1000) % 60 ))
    _m=$(( (_elapsed / 1000 / 60) % 60 ))
    _h=$(( _elapsed / 1000 / 60 / 60 ))
    echo "[$2] Total elapsed: ${_h}h ${_m}m ${_s}s ${_ms}ms"
    ;;
esac
