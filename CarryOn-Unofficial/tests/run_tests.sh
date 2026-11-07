#!/usr/bin/env bash
# 通用放置语义方案的多输入验证入口。
# 用法：JAVA_HOME=<JDK8> ./tests/run_tests.sh
set -e

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JDK="${JAVA_HOME:-C:/Program Files/Eclipse Adoptium/jdk-8.0.492.9-hotspot}"
JAVAC="$JDK/bin/javac"
JAVA="$JDK/bin/java"
JAVAP="$JDK/bin/javap"
[ -x "$JAVAC" ] || JAVAC=javac
[ -x "$JAVA" ] || JAVA=java
[ -x "$JAVAP" ] || JAVAP=javap

GAME="${CARRYON_GAME:-E:/Weapon And Magic/.minecraft/versions/1.7.10}"
MODS="$GAME/mods"
FORGE="$ROOT/deps/forgebin.jar"

TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$TMP/classes"

echo "==> 编译纯决策层 + 测试（裸 JDK，class path 不含任何游戏 jar）"
"$JAVAC" -encoding UTF-8 -d "$TMP/classes" \
  "$ROOT/src/main/java/tschipp/carryon/common/handler/PlacementPolicy.java" \
  "$ROOT/tests/PlacementPolicyTest.java"

echo ""
"$JAVA" -Dfile.encoding=UTF-8 -cp "$TMP/classes" tschipp.carryon.common.handler.PlacementPolicyTest

echo ""
echo "==> 真实模组字节码分类交叉验证"
if [ -d "$MODS" ] && [ -f "$FORGE" ]; then
  python "$ROOT/tests/classify_samples.py" \
    --mods "$MODS" --forge "$FORGE" --javap "$JAVAP" || exit 1
else
  echo "  SKIP  mods/ 或 deps/forgebin.jar 不可用"
fi

echo ""
echo "==> 全部验证通过"
