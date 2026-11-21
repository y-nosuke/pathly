#!/usr/bin/env bash
# 端末に入っている開発版（com.pathly.debug）が、今のブランチに上書きで入れてよいものかを見る。
#   .claude/skills/phone-check/installed-dev.sh <adb のシリアル>     # 例: "$PHONE" / emulator-5554
#
# 終了コード: 0 = 上書きでよい（入っているコミットが HEAD の祖先） / 1 = 入っていない /
#             2 = 入れ替え（別のブランチ・見分けられない）。アンインストールしてから入れる。
# 版名（versionName）は 0.5.0+dev.3.abc1234 / 0.0.0+dev.abc1234 / 0.5.0（タグちょうど）。build.gradle.kts の gitVersionName。
set -u
export MSYS_NO_PATHCONV=1
ADB=${ADB:-/c/Users/yoichi/AppData/Local/Android/Sdk/platform-tools/adb}
serial=${1:?adb のシリアルを渡す}

name=$(timeout 20 "$ADB" -s "$serial" shell dumpsys package com.pathly.debug 2> /dev/null |
  sed -n 's/^ *versionName=//p' | head -1 | tr -d '\r')
if [ -z "$name" ]; then
  echo "開発版は入っていない。"
  exit 1
fi

case $name in
  *+dev.*) rev=${name##*.} ;; # 最後の . の後がコミットの短い ID
  *) rev=v$name ;;            # タグちょうど
esac

if commit=$(git rev-parse --verify -q "$rev^{commit}") && git merge-base --is-ancestor "$commit" HEAD; then
  echo "入っている開発版: $name（$(git log -1 --format='%h %s' "$commit")）。今のブランチの祖先なので上書きでよい。"
  exit 0
fi
if [ -n "${commit:-}" ]; then
  where=$(git branch -a --contains "$commit" --format='%(refname:short)' 2> /dev/null | head -3 | tr '\n' ' ')
  echo "入っている開発版: $name（$(git log -1 --format='%h %s' "$commit")。ブランチ: ${where:-不明}）。"
else
  echo "入っている開発版: $name（このリポジトリに無いコミット）。"
fi
echo "今のブランチの祖先ではないので、アンインストールしてから入れる。"
exit 2
