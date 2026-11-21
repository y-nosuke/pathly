#!/usr/bin/env bash
# 新しい worktree で、git に入らないファイルを本体（最初の worktree）からコピーする。worktree で作業を始めたら 1 回。
#   .claude/skills/start-issue/setup-worktree.sh
#
# - android/local.properties（Google Maps のキー・sdk.dir）。無いとビルドは通るが、開発版の地図が出ない。
# - すでにあるファイルは上書きしない（本体と違えば知らせる）。
# - 本体で実行したら何もしない。
set -u

top=$(git rev-parse --show-toplevel) || exit 1
main=$(git worktree list --porcelain | sed -n '1s/^worktree //p')

if [ "$top" = "$main" ]; then
  echo "本体（$main）なので、コピーするものはない。"
  exit 0
fi

code=0
for f in android/local.properties; do
  if [ ! -f "$main/$f" ]; then
    echo "本体に $f が無い。本体で作ってからやり直す。"
    code=1
  elif [ -f "$top/$f" ]; then
    if cmp -s "$main/$f" "$top/$f"; then
      echo "$f はコピー済み。"
    else
      echo "$f は本体と中身が違う。上書きしない（必要なら消してからやり直す）。"
    fi
  else
    cp "$main/$f" "$top/$f" && echo "$f を本体からコピーした。"
  fi
done
exit "$code"
