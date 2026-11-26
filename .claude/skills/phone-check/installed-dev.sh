#!/usr/bin/env bash
# 端末に入っている開発版（com.pathly.debug）が、今のブランチに上書きで入れてよいものかを見る。
#   .claude/skills/phone-check/installed-dev.sh <adb のシリアル>     # 例: "$PHONE" / emulator-5554
#
# 終了コード: 0 = 上書きでよい（入っているコミットが HEAD の祖先） / 1 = 入っていない /
#             2 = 入れ替え（DB の形が違う・見分けられない）。アンインストールしてから入れる /
#             3 = 祖先ではないが DB の形（android/app/schemas）が同じ。上書きでよい。versionCode が下がるときは
#                 installDebug が INSTALL_FAILED_VERSION_DOWNGRADE で落ちるので、adb install -r -d で入れる。
# 版名（versionName）は 0.5.0+dev.3.abc1234 / 0.0.0+dev.abc1234 / 0.5.0（タグちょうど）。build.gradle.kts の gitVersionName。
# versionCode は HEAD までのコミット数（gitVersionCode）。
set -u
export MSYS_NO_PATHCONV=1
ADB=${ADB:-/c/Users/yoichi/AppData/Local/Android/Sdk/platform-tools/adb}
serial=${1:?adb のシリアルを渡す}

pkg=$(timeout 20 "$ADB" -s "$serial" shell dumpsys package com.pathly.debug 2> /dev/null | tr -d '\r')
name=$(printf '%s\n' "$pkg" | sed -n 's/^ *versionName=//p' | head -1)
if [ -z "$name" ]; then
  echo "開発版は入っていない。"
  exit 1
fi
installed_code=$(printf '%s\n' "$pkg" | grep -oE 'versionCode=[0-9]+' | head -1 | cut -d= -f2)

case $name in
  *+dev.*) rev=${name##*.} ;; # 最後の . の後がコミットの短い ID
  *) rev=v$name ;;            # タグちょうど
esac

if commit=$(git rev-parse --verify -q "$rev^{commit}") && git merge-base --is-ancestor "$commit" HEAD; then
  echo "入っている開発版: $name（$(git log -1 --format='%h %s' "$commit")）。今のブランチの祖先なので上書きでよい。"
  exit 0
fi
if [ -z "${commit:-}" ]; then
  echo "入っている開発版: $name（このリポジトリに無いコミット）。"
  echo "見分けられないので、アンインストールしてから入れる。"
  exit 2
fi

where=$(git branch -a --contains "$commit" --format='%(refname:short)' 2> /dev/null | head -3 | tr '\n' ' ')
echo "入っている開発版: $name（$(git log -1 --format='%h %s' "$commit")。ブランチ: ${where:-不明}）。"

# DB の版と形は schemas/ に出る（版を上げれば N.json が増え、同じ版で形を変えれば N.json が変わる）。
# 差が無ければ、入っている DB をそのまま読めるので上書きでよい。
if git diff --quiet "$commit" HEAD -- android/app/schemas; then
  head_code=$(git rev-list --count HEAD)
  if [ -n "$installed_code" ] && [ "$installed_code" -gt "$head_code" ]; then
    echo "今のブランチの祖先ではないが、DB の形は同じなので上書きでよい。versionCode が下がる（$installed_code → $head_code）ので adb install -r -d で入れる。"
  else
    echo "今のブランチの祖先ではないが、DB の形は同じなので上書きでよい（installDebug で入る）。"
  fi
  exit 3
fi
echo "今のブランチの祖先ではなく、DB の形も違うので、アンインストールしてから入れる。"
exit 2
