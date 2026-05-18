#!/usr/bin/env bash
set -euo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAR="${DIR}/kiwi-admin.jar"
PID_FILE="${DIR}/kiwi-admin.pid"
LOG_FILE="${DIR}/app.log"

stop_existing() {
  if [[ -f "$PID_FILE" ]]; then
    local pid
    pid=$(tr -d ' \n' <"$PID_FILE" || true)
    if [[ -n "$pid" ]] && kill -0 "$pid" 2>/dev/null; then
      kill -TERM "$pid" 2>/dev/null || true
      local i=0
      while kill -0 "$pid" 2>/dev/null && (( i < 3 )); do
        sleep 1
        ((i++)) || true
      done
      kill -KILL "$pid" 2>/dev/null || true
    fi
    rm -f "$PID_FILE"
  fi
  # 若无 pid 文件但仍有同 jar 进程，一并结束（便于手工启动后的重启）
  if pgrep -f "${JAR}" >/dev/null 2>&1; then
    pkill -f "${JAR}" 2>/dev/null || true
    sleep 1
  fi
}

stop_existing

nohup java \
  -Dspring.profiles.active=local,dev \
  -jar "$JAR" >>"$LOG_FILE" 2>&1 &

echo $! >"$PID_FILE"
echo "kiwi-admin 已启动 PID=$(cat "$PID_FILE")，日志：$LOG_FILE"

tail -200f $LOG_FILE