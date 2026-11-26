#!/usr/bin/env bash
# instrumented test（connectedDebugAndroidTest）をエミュレータ（emulator-5554）で見張り付きで回す。
#   .claude/skills/emulator-check/run-instrumented.sh
#   .claude/skills/emulator-check/run-instrumented.sh -Pandroid.testInstrumentationRunnerArguments.class=com.pathly.xxx.YyyTest
#
# - 始める前に、エミュレータが応答して起動し終えているかを見る。駄目なら止めて起動し直す。
# - テスト中は 1 分ごとに、adb が応答するかと、終わったテストの数（logcat の TestRunner の finished:）が
#   増えているかを見る。どちらかが 3 回続けて駄目なら、gradle とエミュレータを止めて終了コード 125 で終える。
#   （起動し直しても 3 分で起動し終わらないときも 125。）
# - テストが始まる前（コンパイル・インストール中）は、ログが WATCH_STALL_MIN 分（既定 10）増えなければ止まったとみなす。
# - 終わったら tests= failures= errors= の 1 行を出す。それ以外は gradle の終了コードのまま。
# - 別の worktree で run-build.sh・run-instrumented.sh が走っていれば、終わるまで待ってから始める
#   （エミュレータに触る前に待つ。LOCK_WAIT_MIN 分（既定 60）待っても順番が来なければ終了コード 123）。
set -u
. "$(dirname "$0")/watch-lib.sh"

AVD=${AVD:-Pixel_10_API36}
export ANDROID_SERIAL=$SERIAL # gradle もエミュレータだけに入れる（Wi-Fi のスマホを巻き込まない）
RESULTS="$ANDROID_DIR/app/build/outputs/androidTest-results/connected"

emulator_ready() {
  [ "$(adb_e shell getprop sys.boot_completed 2> /dev/null | tr -d '\r')" = "1" ]
}

kill_emulator() {
  adb_e emu kill > /dev/null 2>&1 || true
  sleep 5
  # emu kill も効かないほど固まっているとき。使っている AVD は 1 つだけなので名前で止める。
  taskkill /F /IM qemu-system-x86_64.exe > /dev/null 2>&1 || true
}

start_emulator() {
  "$SDK/emulator/emulator" -avd "$AVD" -no-snapshot-save -no-audio -no-boot-anim > /dev/null 2>&1 &
  disown
  local i
  for i in $(seq 1 36); do
    sleep 5
    emulator_ready && return 0
  done
  return 1
}

acquire_check_lock "run-instrumented $*"

if ! emulator_ready; then
  # adb から見えなくても qemu が残っていることがある（そのまま起動すると 2 台目が別の番号で立ち上がる）。
  if tasklist 2> /dev/null | grep -qi '^qemu-system-x86_64' || timeout 10 "$ADB" devices 2> /dev/null | grep -q "^$SERIAL"; then
    echo "エミュレータが応答しない（固まっているか、起動し終えていない）。止めて起動し直す。"
    kill_emulator
  else
    echo "エミュレータを起動する。"
  fi
  if ! start_emulator; then
    echo "エミュレータが 3 分で起動し終わらなかった。"
    exit 125
  fi
fi

# 別のブランチの開発版が入っていると、versionCode（コミット数）が下がって入らないことがある。消してから始める
# （エミュレータのデータはテスト用なので、DB の形が同じ 3 でも消す）。
"$(dirname "$0")/../phone-check/installed-dev.sh" "$SERIAL" > /dev/null
case $? in
  2 | 3)
    echo "別のブランチの開発版が入っているので、アンインストールしてから始める。"
    adb_e uninstall com.pathly.debug > /dev/null 2>&1 || true
    ;;
esac

# 前の結果を読まないよう消す。終わったテストを数えるため、logcat を空にして大きくする（溢れて数が減らないように）。
rm -rf "$RESULTS"
adb_e logcat -G 16M > /dev/null 2>&1 || true
adb_e logcat -c > /dev/null 2>&1 || true

FINISHED=0
LAST_SIGNATURE=
check_progress() {
  if ! adb_e shell echo ok 2> /dev/null | grep -q ok; then
    return 2
  fi
  local out started signature
  out=$(ADB_TIMEOUT=20 adb_e logcat -d -s TestRunner 2> /dev/null)
  # 「run started: / run finished:」（全体の始まり・終わり）は数えない。
  started=$(grep -cE ': started:' <<< "$out")
  FINISHED=$(grep -cE ': finished:' <<< "$out")
  signature="$FINISHED $(grep -E ': finished:' <<< "$out" | tail -1)"
  if [ "$signature" != "$LAST_SIGNATURE" ]; then
    LAST_SIGNATURE=$signature
    return 0
  fi
  # テストが始まってから、終わったテストが増えない。
  [ "$started" -gt 0 ] && return 2
  return 1
}

stall_handler() {
  local task
  task=$(last_task)
  stop_gradle
  if [ "$1" = unresponsive ] || [ "$FINISHED" -gt 0 ]; then
    kill_emulator
    # 固まった後は logcat を読めないので、数は最後に確かめられたとき（最大 1 分前）のもの。
    echo "エミュレータが固まった（最後に確かめたときは ${FINISHED} 件まで終わっていた）。gradle とエミュレータを止めた。"
  else
    echo "ビルドが止まった（${WATCH_STALL_MIN} 分ログが進まない。最後のタスク: ${task:-不明}）。gradle を止めた。"
  fi
  return 125
}

start_gradle connectedDebugAndroidTest "$@"
watch_gradle
code=$?
[ -n "${STALLED:-}" ] && exit "$code"

# 端末ごとに 1 つの XML（<testsuites> の下にクラスごとの <testsuite>）。全クラスの <testsuite> の数を足す。
summary=$(find "$RESULTS" -name '*.xml' -exec grep -hoE '<testsuite [^>]*' {} + 2> /dev/null |
  awk -F'"' '{ for (i = 1; i < NF; i++) { if ($i ~ / tests=$/) t += $(i + 1); if ($i ~ / failures=$/) f += $(i + 1); if ($i ~ / errors=$/) e += $(i + 1) } }
    END { printf "tests=%d failures=%d errors=%d", t, f, e }')
grep -E 'FAILED|There were failing' "$WATCH_LOG" | head -5
echo "$summary"
echo "exit=$code"
exit "$code"
