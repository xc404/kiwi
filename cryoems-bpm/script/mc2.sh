#!/bin/bash
#SBATCH -N 1
#SBATCH -n 1
#SBATCH --cpus-per-task=3
#SBATCH --gres=gpu:1
#SBATCH --job-name=MotionCor2
#SBATCH --output=/home/cryoems/logs/mocor2-%J.log
#SBATCH --error=/home/cryoems/logs/mocor2-%J.log
module load  MotionCor2/1.4.5 

echo "[$(date '+%F %T')] [$$] ===== Job ENVIRONMENT ====="
echo "Hostname: $(hostname)"
echo ""
echo "nvidia-smi 输出："
nvidia-smi
echo ""
echo "Loaded modules (module list)："
module list 2>&1
echo ""
echo "which MotionCor2:"
which MotionCor2 || echo "[Warning] MotionCor2 未在 PATH 中找到！"
echo "MotionCor2 --version："
MotionCor2 --version 2>&1 || echo "[Warning] 无法运行 MotionCor2 --version"
echo ""
echo "which inotifywait:"
which inotifywait || echo "[Warning] inotifywait 未找到！监控逻辑会失败。"
echo "=============================="
echo ""

####### 变量定义 #######
LOCKFILE="/tmp/cryoems-mocor2.lock"
WATCH_DIR="/tmp"
TARGET_FILE="MotionCor2_FreeGpus.txt"
TMPIDFILE="/tmp/mc2_pid_${SLURM_JOB_ID}.txt"

# —— 定义监控并删除函数 ——
monitor_and_delete() {
  echo "[$(date '+%F %T')] [$$] 监控子进程启动，开始检测 $TARGET_FILE"
  local max_checks=600    # 最多检测 60 秒（根据需要可调小或调大）
  local count=0

  # 后台启动 inotifywait 监听 WATCH_DIR 下的 create 事件
  inotifywait -q -e create --format '%f' "$WATCH_DIR" &
  local inotify_pid=$!

  while [[ $count -lt $max_checks ]]; do
    ((count++))
    # 方法1：如果文件一开始就已经存在
    if [[ -f "$WATCH_DIR/$TARGET_FILE" ]]; then
      echo "[$(date '+%F %T')] [$$] 第 $count 次检测到已有 $TARGET_FILE，等待 0.5s 后删除"
      sleep 0.5
      sudo rm -f "$WATCH_DIR/$TARGET_FILE"
      kill $inotify_pid 2>/dev/null
      echo "[$(date '+%F %T')] [$$] 已删除 $TARGET_FILE，监控子进程准备退出"
      return 0
    fi

    # 方法2：检查 inotifywait 是否已捕获到新建事件并自行结束
    if ! kill -0 $inotify_pid 2>/dev/null; then
      wait $inotify_pid 2>/dev/null
      if [[ -f "$WATCH_DIR/$TARGET_FILE" ]]; then
        echo "[$(date '+%F %T')] [$$] inotifywait 捕获到 $TARGET_FILE，等待 0.5s 删除"
        sleep 0.5
        rm -f "$WATCH_DIR/$TARGET_FILE"
        echo "[$(date '+%F %T')] [$$] 已删除 $TARGET_FILE，监控子进程准备退出"
      fi
      return 0
    fi

    # 每隔 100 次循环打印一次心跳日志
    if (( count % 100 == 0 )); then
      echo "[$(date '+%F %T')] [$$] 正在轮询 (第 $count 次)，尚未发现 $TARGET_FILE"
    fi

    sleep 0.1
  done

  # 超时未检测到文件
  kill $inotify_pid 2>/dev/null
  echo "[$(date '+%F %T')] [$$] 监控超时 ($((max_checks * 0.1)) 秒)，退出监控子进程"
  return 1
}

# —— 定义清理函数：在意外错误时，也要释放锁 ——
cleanup_and_exit() {
  local code="$1"
  echo "[$(date '+%F %T')] [$$] cleanup_and_exit($code)：开始释放锁（如果还在持有）"
  # 只要 FD9 还在，这一步就把锁给放掉
  if [[ -e /proc/$$/fd/9 ]]; then
    exec 9>&-    # 关闭 FD9 → 真正释放 /tmp/motioncor2_delete.lock
    echo "[$(date '+%F %T')] [$$] FD9 已关闭，锁已释放"
  else
    echo "[$(date '+%F %T')] [$$] FD9 已经关闭，无需再释放"
  fi
  exit "$code"
}
# 任何命令返回非零、或收到 INT/TERM 信号，都会走 cleanup_and_exit 1
trap 'cleanup_and_exit 1' ERR INT TERM

########## 下面才是真正的逻辑 ##########

# —— 第 1 步：父进程在主 shell 打开 FD9 指向锁文件，并尝试拿锁 ——
exec 9>"$LOCKFILE"
echo "[$(date '+%F %T')] [$$] ===== 等待获取锁 $LOCKFILE ====="
flock -x 9
echo "[$(date '+%F %T')] [$$] 已获取锁 $LOCKFILE，准备 fork 子进程监控并删除 $TARGET_FILE"

# —— 第 2 步：在同一 shell 下 fork 一个子进程，让它继承 FD9（此时锁已在父子共享的描述符上）——
(
  # 此处在“监控子进程”里，FD9 依然是打开状态，锁依然保持
  monitor_and_delete
  MONITOR_EXIT=$?
  echo "[$(date '+%F %T')] [$$] 监控子进程结束 (exit=$MONITOR_EXIT)，准备释放锁"
  # 关闭自身的 FD9 → 释放锁
  exec 9>&-
  echo "[$(date '+%F %T')] [$$] 监控子进程已关闭 FD9，锁真正释放"
  exit "$MONITOR_EXIT"
) &
MONITOR_PID=$!

# —— 第 3 步：父进程 fork 完子进程后，立即关闭自己的 FD9（只留子进程持锁）——
exec 9>&-
echo "[$(date '+%F %T')] [$$] 父进程已关闭 FD9 → 锁由监控子进程 (PID=$MONITOR_PID) 持有"

# —— 第 4 步：父进程现在可以安全地启动 MotionCor2，因为它没有再持有锁 ——
echo "[$(date '+%F %T')] [$$] ===== 启动 MotionCor2 ====="

MotionCor2 "$@"

MC2_PID=$!
echo "[$(date '+%F %T')] [$$] MotionCor2 启动完毕，PID=$MC2_PID，父进程进入 wait"

# —— 第 5 步：父进程先等待 MotionCor2 结束 ——
wait $MC2_PID
EXIT_CODE_MC2=$?
echo "[$(date '+%F %T')] [$$] MotionCor2 (PID=$MC2_PID) 执行完成，退出码=$EXIT_CODE_MC2"

# —— 第 6 步（可选）：如果你希望父进程等监控子进程也完全退出，可以再 wait——
wait $MONITOR_PID
MONITOR_EXIT_FINAL=$?
echo "[$(date '+%F %T')] [$$] 监控子进程 (PID=$MONITOR_PID) 退出码=$MONITOR_EXIT_FINAL"

# —— 全部完成后，父进程自己退出 ——
exit $EXIT_CODE_MC2
