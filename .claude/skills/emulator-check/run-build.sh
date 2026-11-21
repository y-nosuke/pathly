#!/usr/bin/env bash
# ./gradlew build（または渡したタスク）を見張り付きで回す。
#   .claude/skills/emulator-check/run-build.sh                 # ./gradlew build
#   .claude/skills/emulator-check/run-build.sh spotlessCheck --rerun-tasks
# ログが WATCH_STALL_MIN 分（既定 10）増えなければ止まったとみなし、gradle を止めて終了コード 124 で終える。
# それ以外は gradle の終了コードのまま。
# 別の worktree で run-build.sh・run-instrumented.sh が走っていれば、終わるまで待ってから始める
# （LOCK_WAIT_MIN 分（既定 60）待っても順番が来なければ終了コード 123）。
set -u
. "$(dirname "$0")/watch-lib.sh"

stall_handler() {
  local task
  task=$(last_task)
  stop_gradle
  echo "ビルドが止まった（${WATCH_STALL_MIN} 分ログが進まない。最後のタスク: ${task:-不明}）。gradle を止めた。"
  return 124
}

[ $# -eq 0 ] && set -- build
acquire_check_lock "run-build $*"
start_gradle "$@"
watch_gradle
code=$?
[ -n "${STALLED:-}" ] && exit "$code"
tail -5 "$WATCH_LOG"
echo "exit=$code"
exit "$code"
