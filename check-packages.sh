#!/usr/bin/env bash
# BackportAPI 包冲突护栏
#
# 检查什么：
#   BackportAPI 的产物里，是否有类落进了「1.7.10 游戏 jar 里已存在、且已签名」的包。
#   这类类在运行时会触发 JVM 的同包签名一致性检查：
#       SecurityException: class "..."'s signer information does not match ...
#   典型受害者：net.minecraftforge.common.config.ConfigManager、net.minecraft.util.EnumHand。
#
# 为什么要用 dev 产物当对照：
#   必须用 deps/forgebin.jar + deps/mcp.jar（反混淆、包名可读）。
#   生产版 jar 的包名是混淆过的（net/minecraft/util → net/minecraft/util 里那些 a、b、c），
#   拿它比出来的交集没有意义。
#
# 用法：
#   ./check-packages.sh                  # 用 build/ 下最新的 backportapi-*.jar
#   ./check-packages.sh path/to/x.jar    # 指定产物（验收时用）
#
# 退出码：
#   0 = 没有新增冲突（白名单里已登记的 19 个不算）
#   1 = 出现了白名单之外的冲突类
#   0 = 前置条件缺失（没构建、没 deps）—— 只提示，不阻断
set -uo pipefail

export MSYS2_ARG_CONV_EXCL='*'

ROOT="$(cd "$(dirname "$0")" && pwd -W 2>/dev/null || pwd)"
BP="$ROOT/backportapi"
DEPS="$BP/deps"
WHITELIST="$BP/conf/known-conflicts.txt"

if [ $# -ge 1 ] && [ -n "${1:-}" ]; then
  JAR="$1"
else
  JAR="$(ls -t "$BP"/build/backportapi-*.jar 2>/dev/null | head -1)"
fi

# 统一成绝对路径：下面会 cd 到仓库根，相对路径会失效
if [ -n "${JAR:-}" ] && [ -f "$JAR" ]; then
  JAR="$(cd "$(dirname "$JAR")" && pwd -W 2>/dev/null)/$(basename "$JAR")"
fi

cd "$ROOT" || exit 1

# 临时目录用相对路径：本机的 rm 包装器不接受带盘符的路径，会拒绝删除并留下垃圾
mkdir -p "$BP/build"
TMP="backportapi/build/.check-packages.$$"
rm -rf "$TMP" 2>/dev/null
mkdir -p "$TMP"
trap 'rm -rf "$TMP"' EXIT

echo "== BackportAPI 包冲突护栏 =="
echo "产物     : ${JAR:-<未找到>}"
echo "对照     : deps/forgebin.jar + deps/mcp.jar"
echo "白名单   : ${WHITELIST#$ROOT/}"
echo ""

if [ -z "${JAR:-}" ] || [ ! -f "$JAR" ]; then
  echo "[跳过] 找不到产物 jar —— 先跑 backportapi/build.sh"
  exit 0
fi
for dep in forgebin.jar mcp.jar; do
  if [ ! -f "$DEPS/$dep" ]; then
    echo "[跳过] 缺少对照用的 dev 产物：deps/$dep"
    exit 0
  fi
done
if [ ! -f "$WHITELIST" ]; then
  echo "[跳过] 缺少白名单：backportapi/conf/known-conflicts.txt"
  exit 0
fi

list_classes() {
  unzip -Z1 "$1" | grep '\.class$' | sed 's|\.class$||; s|/|.|g' | sort -u
}

pkg_of() {
  sed 's|\.[^.]*$||' | sort -u
}

signer_of() {
  case "$1" in
    net.minecraftforge.*) echo "Forge" ;;
    net.minecraft.*)       echo "Mojang" ;;
    *)                     echo "未知" ;;
  esac
}

list_classes "$JAR" > "$TMP/ours.txt"
{ list_classes "$DEPS/forgebin.jar"; list_classes "$DEPS/mcp.jar"; } | sort -u > "$TMP/game.txt"

# 包名 -> 类名，供后面按包筛类
awk '{ c=$0; p=c; sub(/\.[^.]*$/, "", p); print p "\t" c }' "$TMP/ours.txt" > "$TMP/ours-pkg.txt"
awk '{ c=$0; p=c; sub(/\.[^.]*$/, "", p); print p "\t" c }' "$TMP/game.txt" > "$TMP/game-pkg.txt"

cut -f1 "$TMP/ours-pkg.txt" | sort -u > "$TMP/ours.pkg"
cut -f1 "$TMP/game-pkg.txt" | sort -u > "$TMP/game.pkg"
comm -12 "$TMP/ours.pkg" "$TMP/game.pkg" > "$TMP/conflict.pkg"

# 落在冲突包里的自有类（精确到包，不能用前缀匹配：
# net.minecraft.util.text 在 1.7.10 里并不存在，不该算进来）
awk -F'\t' 'NR==FNR { p[$0]=1; next } ($1 in p) { print $2 }' \
  "$TMP/conflict.pkg" "$TMP/ours-pkg.txt" | sort -u > "$TMP/conflict.cls"

CONFLICT_PKGS=$(wc -l < "$TMP/conflict.pkg" | tr -d ' ')
CONFLICT_CLS=$(wc -l < "$TMP/conflict.cls" | tr -d ' ')

echo "冲突包 $CONFLICT_PKGS 个，落进去的类 $CONFLICT_CLS 个："
while IFS= read -r pkg; do
  [ -z "$pkg" ] && continue
  n=$(awk -F'\t' -v p="$pkg" '$1 == p' "$TMP/ours-pkg.txt" | wc -l | tr -d ' ')
  printf '  %-45s %-7s %s 个\n' "$pkg" "$(signer_of "$pkg")" "$n"
done < "$TMP/conflict.pkg"
echo ""

# 白名单：<签名方> | <类名> | <说明>
awk -F'|' '
  /^[[:space:]]*#/ { next }
  NF >= 2 {
    s = $1; c = $2;
    gsub(/^[[:space:]]+|[[:space:]]+$/, "", s);
    gsub(/^[[:space:]]+|[[:space:]]+$/, "", c);
    if (c != "") print c "\t" s;
  }' "$WHITELIST" | sort -u > "$TMP/known.txt"
cut -f1 "$TMP/known.txt" | sort -u > "$TMP/known.cls"

NEW=$(comm -23 "$TMP/conflict.cls" "$TMP/known.cls")
GONE=$(comm -13 "$TMP/conflict.cls" "$TMP/known.cls")

if [ -n "$NEW" ]; then
  echo "!! 新增冲突类（不在白名单里）："
  while IFS= read -r cls; do
    [ -z "$cls" ] && continue
    pkg="${cls%.*}"
    printf '   %-7s %s   （包 %s）\n' "$(signer_of "$pkg")" "$cls" "$pkg"
  done <<< "$NEW"
  echo ""
  echo "   这些类会让游戏在 preInit 阶段抛 SecurityException（同包签名不一致）。"
  echo "   两条出路："
  echo "     1) 换包名（如 net.backportapi.shim.xxx），调用方改 import；"
  echo "     2) 确认无解后，把它登记到 backportapi/conf/known-conflicts.txt，并说明为什么必须沿用原包名。"
  echo ""
  echo "结果：FAIL"
  exit 1
fi

if [ -n "$GONE" ]; then
  echo "提示：白名单里有 $(echo "$GONE" | wc -l | tr -d ' ') 个类已不在产物中，可以把它们从 known-conflicts.txt 删掉（可收紧）："
  while IFS= read -r cls; do
    [ -z "$cls" ] && continue
    echo "   $cls"
  done <<< "$GONE"
  echo ""
fi

echo "结果：PASS（$CONFLICT_CLS 个冲突类全部在白名单内，无新增）"
exit 0
