# shellcheck shell=bash
# gradle を見張り付きで回す共通部分（run-build.sh・run-instrumented.sh から source する）。
#
# gradle を裏で走らせ、1 分ごとに進み具合を見る。止まったとみなしたら gradle（とエミュレータ）を止め、
# 理由を出して非 0 で終える。Claude Desktop では呼び出し側を run_in_background で回せば、
# 終わったとき（止めたときも）に知らせが来る。

SDK=${ANDROID_SDK_ROOT:-/c/Users/yoichi/AppData/Local/Android/Sdk}
ADB="$SDK/platform-tools/adb"
SERIAL=emulator-5554
export MSYS_NO_PATHCONV=1

# 見張りの間隔（秒）と、ログが増えないまま止まったとみなすまでの分。
WATCH_INTERVAL=${WATCH_INTERVAL:-60}
WATCH_STALL_MIN=${WATCH_STALL_MIN:-10}

ANDROID_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../android" && pwd)"

# adb はすべて timeout 付きで呼ぶ（固まったエミュレータが相手だと戻ってこない）。必ず -s emulator-5554。
adb_e() { timeout "${ADB_TIMEOUT:-10}" "$ADB" -s "$SERIAL" "$@"; }

# git は cd してから呼ぶ（MSYS_NO_PATHCONV=1 なので、git -C /d/... は git.exe に読めない）。
git_a() { (cd "$ANDROID_DIR" && git "$@"); }

# --- 順番待ち（worktree をまたいだロック）---
# エミュレータは 1 台、gradle のデーモンを止めると全部止まるので、run-build.sh・run-instrumented.sh は
# どの worktree から呼ばれても 1 つずつ走らせる。ロックは本体の .git の下（全 worktree で共通）に mkdir で作る。
# 持ち主のシェルが死んでいれば（強制終了されて片付けられなかったとき）取り上げる。
CHECK_LOCK=${CHECK_LOCK:-"$(git_a rev-parse --path-format=absolute --git-common-dir)/pathly-check.lock"}
LOCK_WAIT_MIN=${LOCK_WAIT_MIN:-60}

lock_owner() { cat "$CHECK_LOCK/owner" 2> /dev/null; }

release_check_lock() {
  # 自分のものだけ消す。
  [ "$(sed -n 's/^pid=//p' "$CHECK_LOCK/owner" 2> /dev/null)" = "$$" ] && rm -rf "$CHECK_LOCK"
  return 0
}

# ロックを取るまで待つ。LOCK_WAIT_MIN 分待っても取れなければ終了コード 123 で終える。
acquire_check_lock() {
  local what=$1 waited=0 pid said=
  while ! mkdir "$CHECK_LOCK" 2> /dev/null; do
    if [ ! -d "$CHECK_LOCK" ]; then
      echo "ロック（$CHECK_LOCK）を作れない。"
      exit 123
    fi
    pid=$(sed -n 's/^pid=//p' "$CHECK_LOCK/owner" 2> /dev/null)
    if [ -z "$pid" ]; then
      # mkdir した直後で owner がまだ無いか、壊れている。2 回続けて無ければ取り上げる。
      if [ "$said" = noowner ]; then rm -rf "$CHECK_LOCK"; said=; continue; fi
      said=noowner
    elif ! kill -0 "$pid" 2> /dev/null; then
      echo "前の持ち主（pid $pid）はもう居ないので、ロックを取り上げる。"
      rm -rf "$CHECK_LOCK"
      continue
    elif [ "$said" != "$pid" ]; then
      echo "順番待ち: 別の確認が走っている（$(lock_owner | tr '\n' ' ')）。終わるまで待つ。"
      said=$pid
    fi
    if [ "$waited" -ge $((LOCK_WAIT_MIN * 60)) ]; then
      echo "${LOCK_WAIT_MIN} 分待っても順番が来ない。持ち主: $(lock_owner | tr '\n' ' ')"
      echo "走っていないのに残っているなら、$CHECK_LOCK を消してやり直す。"
      exit 123
    fi
    sleep 10
    waited=$((waited + 10))
  done
  printf 'pid=%s\nwhat=%s\nbranch=%s\nworktree=%s\nsince=%s\n' "$$" "$what" \
    "$(git_a branch --show-current)" "$(git_a rev-parse --show-toplevel)" \
    "$(date '+%H:%M:%S')" > "$CHECK_LOCK/owner"
  trap release_check_lock EXIT
  trap 'exit 130' INT TERM
  [ "$waited" -gt 0 ] && echo "順番が来た（$((waited / 60)) 分 $((waited % 60)) 秒待った）。"
  return 0
}

log_size() { wc -c < "$WATCH_LOG" 2>/dev/null | tr -d ' ' || echo 0; }

last_task() { grep -E '^> Task ' "$WATCH_LOG" 2>/dev/null | tail -1 | sed 's/^> Task //'; }

# gradle を止める（デーモンごと止めないと、裏でビルドが続く）。
# --stop はこの PC の同じ版のデーモンをすべて止めるので、順番待ちの外で別の worktree が gradle を
# 動かしていれば（compileDebugKotlin など）、そちらも落ちる。
stop_gradle() {
  (cd "$ANDROID_DIR" && timeout 60 ./gradlew --stop > /dev/null 2>&1) || true
  kill "$GRADLE_PID" 2> /dev/null || true
  wait "$GRADLE_PID" 2> /dev/null || true
}

# gradle を裏で始める。WATCH_LOG に --console=plain の出力を書く。
start_gradle() {
  WATCH_LOG=${WATCH_LOG:-$(mktemp -t gradle-watch.XXXXXX.log)}
  echo "ログ: $WATCH_LOG"
  (cd "$ANDROID_DIR" && exec ./gradlew --console=plain "$@") > "$WATCH_LOG" 2>&1 &
  GRADLE_PID=$!
}

# gradle が終わるまで見張る。止まったとみなしたら STALLED に理由（idle / unresponsive）を入れ、
# stall_handler（呼び出し側で定義。gradle を止めて知らせる）を呼んで、その終了コードで返す。
# 1 回ごとに check_progress（呼び出し側で定義。0 = 進んでいる／1 = 進んでいない／2 = 相手が応答しない）を呼ぶ。
# 進んでいないことが WATCH_STALL_MIN 分続く、または応答しないことが 3 回続くと止まったとみなす。
watch_gradle() {
  local last_size=0 idle=0 unresponsive=0 size status waited
  local stall_ticks=$(((WATCH_STALL_MIN * 60 + WATCH_INTERVAL - 1) / WATCH_INTERVAL))
  while kill -0 "$GRADLE_PID" 2> /dev/null; do
    # 終わったらすぐ抜けられるよう、5 秒ずつ待つ。
    waited=0
    while [ "$waited" -lt "$WATCH_INTERVAL" ] && kill -0 "$GRADLE_PID" 2> /dev/null; do
      sleep 5
      waited=$((waited + 5))
    done
    kill -0 "$GRADLE_PID" 2> /dev/null || break
    size=$(log_size)
    status=1
    if [ "$size" != "$last_size" ]; then status=0; fi
    last_size=$size
    if declare -F check_progress > /dev/null; then
      check_progress
      case $? in
        0) status=0 unresponsive=0 ;;
        2) unresponsive=$((unresponsive + 1)) ;;
        *) unresponsive=0 ;;
      esac
    fi
    if [ "$status" = 0 ]; then idle=0; else idle=$((idle + 1)); fi
    if [ "$unresponsive" -ge 3 ]; then
      STALLED=unresponsive
      stall_handler unresponsive
      return $?
    fi
    if [ "$idle" -ge "$stall_ticks" ]; then
      STALLED=idle
      stall_handler idle
      return $?
    fi
  done
  wait "$GRADLE_PID"
}
