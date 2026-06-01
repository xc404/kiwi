#!/usr/bin/env bash
set -euo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAR="${DIR}/kiwi-admin.jar"
PID_FILE="${DIR}/kiwi-admin.pid"

if [[ -f "$PID_FILE" ]]; then
  pid=$(tr -d ' \n' <"$PID_FILE" || true)
  if [[ -n "$pid" ]] && kill -0 "$pid" 2>/dev/null; then
    echo "发送 SIGTERM 到 kiwi-admin (PID=$pid)…"
    kill -TERM "$pid" 2>/dev/null || true
    i=0
    while kill -0 "$pid" 2>/dev/null && (( i < 3 )); do
      sleep 1
      ((i++)) || true
    done
    if kill -0 "$pid" 2>/dev/null; then
      echo "进程未退出，发送 SIGKILL…"
      kill -KILL "$pid" 2>/dev/null || true
    fi
  fi
  rm -f "$PID_FILE"
fi

if pgrep -f "${JAR}" >/dev/null 2>&1; then
  echo "未发现有效 pid 文件，按 jar 路径结束残留进程…"
  pkill -f "${JAR}" 2>/dev/null || true
  sleep 1
fi

if pgrep -f "${JAR}" >/dev/null 2>&1; then
  echo "仍有 kiwi-admin 相关进程运行，请检查：pgrep -af kiwi-admin.jar" >&2
  exit 1
fi
