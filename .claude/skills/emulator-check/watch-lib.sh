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

log_size() { wc -c < "$WATCH_LOG" 2>/dev/null | tr -d ' ' || echo 0; }

last_task() { grep -E '^> Task ' "$WATCH_LOG" 2>/dev/null | tail -1 | sed 's/^> Task //'; }

# gradle を止める（デーモンごと止めないと、裏でビルドが続く）。
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
