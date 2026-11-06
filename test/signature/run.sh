#!/usr/bin/env bash
# P0 · 签名冲突最小复现 —— 运行脚本
#
#   ./run.sh                          # 用 JDK 8 跑，先加载签名类
#   ./run.sh "<jdk home>"             # 指定 JDK
#   ./run.sh "<jdk home>" pkg.Unsigned # 反序：先加载无签名类
#
# 退出码即 Repro 的判定：0 = 复现成功。
set -uo pipefail

export MSYS2_ARG_CONV_EXCL='*'

ROOT="$(cd "$(dirname "$0")" && pwd -W 2>/dev/null || pwd)"
cd "$ROOT"

JDK="${1:-C:/Program Files/Eclipse Adoptium/jdk-8.0.492.9-hotspot}"
FIRST="${2:-pkg.Signed}"
JAVA="$JDK/bin/java"

[ -f build/signed.jar ] || { echo "先跑 ./build.sh" >&2; exit 2; }
[ -x "$JAVA" ] || { echo "找不到 java：$JAVA" >&2; exit 2; }

"$JAVA" -cp build/repro Repro build "$FIRST"
