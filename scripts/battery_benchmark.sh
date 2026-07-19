#!/usr/bin/env bash
# Battery / memory / disk benchmark for the on-device local OpenCode runtime.
#
# Usage:
#   adb shell                             # on the host, then inside the device
#   sh /sdcard/opencode-android/battery_benchmark.sh
#
# Requirements on the device:
#   - termux or another shell that has `dumpsys`, `top`, `du`
#   - OpenCode Android installed with the local runtime ready
#
# Manual alternative: open Android Studio Profiler and record the same metrics.

set -euo pipefail

IDLE_MINUTES="${IDLE_MINUTES:-30}"
CHAT_MINUTES="${CHAT_MINUTES:-5}"

snapshot() {
  local label="$1"
  local stamp
  stamp="$(date +%Y-%m-%dT%H:%M:%S)"
  {
    echo "=== $label @ $stamp ==="
    echo "-- battery --"
    dumpsys battery | grep -E 'level|status|temperature' || true
    echo "-- process tree RSS (kB) --"
    top -m 20 -s rss -n 1 2>/dev/null | grep -E 'opencode|proot' || true
    echo "-- runtime disk --"
    du -sh /data/data/com.opencode.android/files/runtime 2>/dev/null || true
    du -sh /data/data/com.opencode.android/files/runtime/logs 2>/dev/null || true
    echo
  } | tee -a "opencode-benchmark-${stamp%%[+ ]*}.log"
}

echo "Starting OpenCode Android benchmark."
echo "Idle: ${IDLE_MINUTES} min  Chat: ${CHAT_MINUTES} min"
snapshot "before-idle"

echo "Keep the app backgrounded with the local runtime running."
echo "Sleeping for ${IDLE_MINUTES} minutes..."
sleep "$((IDLE_MINUTES * 60))"

snapshot "after-idle"

echo "Run a chat workload for ${CHAT_MINUTES} minutes now."
echo "Sleeping for ${CHAT_MINUTES} minutes..."
sleep "$((CHAT_MINUTES * 60))"

snapshot "after-chat"
echo "Done. Collect opencode-benchmark-*.log and copy values into docs/device-matrix.md"
