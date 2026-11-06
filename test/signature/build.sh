#!/usr/bin/env bash
# P0 · 签名冲突最小复现 —— 构建脚本
#
#   1. 编译 pkg.Signed / pkg.Unsigned（Java 8 字节码）
#   2. signed.jar   = pkg/Signed.class + jarsigner 自签证书
#      unsigned.jar = pkg/Unsigned.class，不签名
#   3. 编译 Repro
#
# 产物：build/signed.jar、build/unsigned.jar、build/repro/
# 用法：./build.sh [JDK8_HOME]
set -euo pipefail

export MSYS2_ARG_CONV_EXCL='*'

ROOT="$(cd "$(dirname "$0")" && pwd -W 2>/dev/null || pwd)"
cd "$ROOT"

JAVA_HOME="${1:-${JAVA_HOME:-C:/Program Files/Eclipse Adoptium/jdk-8.0.492.9-hotspot}}"
JAVAC="$JAVA_HOME/bin/javac"
JAR="$JAVA_HOME/bin/jar"
KEYTOOL="$JAVA_HOME/bin/keytool"
JARSIGNER="$JAVA_HOME/bin/jarsigner"

for tool in "$JAVAC" "$JAR" "$KEYTOOL" "$JARSIGNER"; do
  [ -x "$tool" ] || { echo "找不到工具：$tool" >&2; exit 1; }
done

echo "==> clean"
rm -rf build
mkdir -p build/classes build/repro

echo "==> javac pkg/*.java ($("$JAVAC" -version 2>&1 | head -1))"
# 相对路径 + 无空格工作目录：规避 javac 对含空格路径的解析问题
"$JAVAC" -source 8 -target 8 -encoding UTF-8 -nowarn \
  -d build/classes src/pkg/Signed.java src/pkg/Unsigned.java

echo "==> jar signed.jar (pkg/Signed.class)"
"$JAR" cf build/signed.jar -C build/classes pkg/Signed.class

echo "==> jar unsigned.jar (pkg/Unsigned.class)"
"$JAR" cf build/unsigned.jar -C build/classes pkg/Unsigned.class

echo "==> keytool: 自签证书"
rm -f build/test.keystore
"$KEYTOOL" -J-Duser.language=en -J-Duser.country=US -genkeypair \
  -alias testkey -keyalg RSA -keysize 2048 -validity 3650 \
  -dname "CN=Signature Conflict Repro, O=BackportAPI Test" \
  -keystore build/test.keystore -storetype JKS \
  -storepass changeit -keypass changeit 2>&1 | sed 's/^/    /'

echo "==> jarsigner: 给 signed.jar 签名"
"$JARSIGNER" -J-Duser.language=en -J-Duser.country=US -keystore build/test.keystore -storepass changeit \
  -sigalg SHA256withRSA -digestalg SHA-256 \
  build/signed.jar testkey 2>&1 | sed 's/^/    /'

echo "==> javac Repro"
"$JAVAC" -source 8 -target 8 -encoding UTF-8 -nowarn -d build/repro src/Repro.java

echo
echo "==> 产物"
"$JAR" tf build/signed.jar
echo "--- unsigned.jar ---"
"$JAR" tf build/unsigned.jar
echo
echo "==> jarsigner -verify signed.jar"
"$JARSIGNER" -J-Duser.language=en -J-Duser.country=US -verify -verbose:summary build/signed.jar 2>&1 | sed 's/^/    /' | head -12
echo
echo "OK. 运行： ./run.sh [JDK_HOME] [首个类名]"
