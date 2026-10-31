#!/usr/bin/env bash
# BackportAPI - ForgeGradle-free build pipeline (bash / Git Bash)
#
#   1. javac (MCP names)                            -> build/classes
#   2. jar                                          -> build/classes-mcp.jar
#   3. SpecialSource reobf (mcp2srg + inheritance)  -> build/classes-srg.jar
#   4. package                                      -> build/backportapi-<version>.jar
#
# Usage:  ./build.sh [version] [--skip-reobf]
#
# NOTE ON PATHS
# This project lives under a directory containing a space ("WorkBuddy AI").
# Windows javac/java do not reliably receive ';'-separated classpaths that
# contain spaces when spawned from a script, so:
#   * javac is driven entirely through an @argfile (no options on the command line);
#   * every other tool is invoked from inside build/ with relative paths.
set -euo pipefail

export MSYS2_ARG_CONV_EXCL='*'

ROOT_WIN="$(cd "$(dirname "$0")" && pwd -W 2>/dev/null || pwd)"
cd "$ROOT_WIN"

VERSION="1.0.0"
SKIP_REOBF=0
for arg in "$@"; do
  case "$arg" in
    --skip-reobf) SKIP_REOBF=1 ;;
    -*) ;;
    *) VERSION="$arg" ;;
  esac
done

JAVA_HOME="${JAVA_HOME:-C:/Program Files/Eclipse Adoptium/jdk-8.0.492.9-hotspot}"
JAVAC="$JAVA_HOME/bin/javac"
JAR="$JAVA_HOME/bin/jar"
JAVA="$JAVA_HOME/bin/java"

[ -x "$JAVAC" ] || JAVAC="javac"
[ -x "$JAR" ] || JAR="jar"
[ -x "$JAVA" ] || JAVA="java"

BUILD="$ROOT_WIN/build"
DEPS="$ROOT_WIN/deps"
CONF="$ROOT_WIN/conf"

# forgebin.jar MUST be first: it is the only artifact that still carries
# InnerClasses attributes, which javac needs to resolve nested vanilla types
# such as ClickEvent.Action or PlayerInteractEvent.RightClickBlock.
CP="$DEPS/backportapi-1.0.0.jar;$DEPS/forgebin.jar;$DEPS/mcp.jar;$DEPS/forge-mcp.jar;$DEPS/lwjgl-2.9.3.jar;$DEPS/netty-all-4.0.23.Final.jar;$DEPS/guava-17.0.jar;$DEPS/authlib-1.5.21.jar"

echo "==> clean"
rm -rf "$BUILD/classes" "$BUILD/pkg"
mkdir -p "$BUILD/classes"

echo "==> write javac argfile"
{
  echo "-encoding"
  echo "UTF-8"
  echo "-source"
  echo "8"
  echo "-target"
  echo "8"
  echo "-nowarn"
  echo "-proc:none"
  echo "-cp"
  echo "\"$CP\""
  echo "-d"
  echo "\"$BUILD/classes\""
  find "$ROOT_WIN/src/main/java" -name '*.java' | sed 's/.*/"&"/'
} > "$BUILD/javac.args"

COUNT=$(find "$ROOT_WIN/src/main/java" -name '*.java' | wc -l | tr -d ' ')

echo "==> javac ($COUNT files)"
( cd "$BUILD" && "$JAVAC" @javac.args )

echo "==> jar classes-mcp.jar"
rm -f "$BUILD/classes-mcp.jar"
( cd "$BUILD/classes" && "$JAR" cf ../classes-mcp.jar . )

if [ "$SKIP_REOBF" = "1" ]; then
  echo "==> skip reobf requested"
  exit 0
fi

echo "==> reobf mcp2srg"
rm -f "$BUILD/classes-srg.jar"
( cd "$BUILD" && "$JAVA" -jar ../deps/SpecialSource-1.11.4-shaded.jar \
    --in-jar classes-mcp.jar \
    --out-jar classes-srg.jar \
    --srg-in ../conf/mcp2srg.srg \
    --read-inheritance ../conf/inheritance.map -q )

echo "==> package"
mkdir -p "$BUILD/pkg"
( cd "$BUILD/pkg" && "$JAR" xf ../classes-srg.jar )
cp -r "$ROOT_WIN/src/main/resources/." "$BUILD/pkg/"

OUT="$BUILD/carryon-$VERSION.jar"
rm -f "$OUT"
( cd "$BUILD/pkg" && "$JAR" cf "../carryon-$VERSION.jar" . )

# 中间产物（classes / pkg / 两个中间 jar / argfile）用完就删，build/ 只留交付 jar。
# 需要看中间产物时用 --skip-reobf，那条路径会提前 return。
rm -rf "$BUILD/classes" "$BUILD/pkg" "$BUILD/classes-mcp.jar" "$BUILD/classes-srg.jar" "$BUILD/javac.args"

echo "==> built: $OUT ($(wc -c < "$OUT" | tr -d ' ') bytes)"
