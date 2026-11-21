#!/usr/bin/env bash
# エミュレータで画面を手で触って確かめる間、順番待ちのロックを持っておく。
# その間、別の worktree の run-build.sh・run-instrumented.sh は待つ（instrumented test は開発版を入れ直して消すので、
# 触っている最中に走ると画面が消える）。
#   .claude/skills/emulator-check/hold-check-lock.sh            # run_in_background で起動。順番が来たら「ロックを持った」と出す
#   .claude/skills/emulator-check/hold-check-lock.sh release    # 確かめ終わったら手放す
# 手放し忘れても HOLD_MIN 分（既定 30）で自分で手放す。
set -u
. "$(dirname "$0")/watch-lib.sh"

HOLD_MIN=${HOLD_MIN:-30}
here=$(git_a rev-parse --show-toplevel)

if [ "${1:-}" = release ]; then
  owner=$(lock_owner)
  if grep -qx 'what=hold' <<< "$owner" && grep -qxF "worktree=$here" <<< "$owner"; then
    touch "$CHECK_LOCK/release"
    echo "ロックを手放す。"
  else
    echo "この worktree が持っているロックは無い。"
  fi
  exit 0
fi

acquire_check_lock hold
echo "ロックを持った（${HOLD_MIN} 分で自分で手放す）。"
end=$(($(date +%s) + HOLD_MIN * 60))
while [ ! -e "$CHECK_LOCK/release" ] && [ "$(date +%s)" -lt "$end" ]; do
  sleep 5
done
[ -e "$CHECK_LOCK/release" ] && echo "手放した。" || echo "${HOLD_MIN} 分経ったので手放した。"
