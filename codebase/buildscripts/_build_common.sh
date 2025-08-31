#!/bin/bash
# Usage: source _build_common.sh start|end script_name
if [[ $1 == start ]]; then
  export _start_ts
  _start_ts=$(date +%s%3N)
  echo "[$2] Start: $(date '+%F %T')"
elif [[ $1 == end ]]; then
  _end_ts=$(date +%s%3N)
  echo "[$2] End: $(date '+%F %T')"
  _elapsed=$((_end_ts-_start_ts))
  _ms=$((_elapsed%1000))
  _s=$(((_elapsed/1000)%60))
  _m=$(((_elapsed/1000/60)%60))
  _h=$(((_elapsed/1000/60/60)))
  echo "[$2] Total elapsed: ${_h}h ${_m}m ${_s}s ${_ms}ms"
fi
