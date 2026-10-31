#!/usr/bin/env bash
# 构建后校验：找出所有未被重混淆改写的原版方法引用。
#
# 为什么需要它：
#   forgebin.jar（编译用）与 conf/mcp2srg.srg（重混淆用）来自不同的映射集，
#   少数方法名在两边不一致（例如 ItemStack 的 getItemDamage vs getMetadata）。
#   这类方法重混淆时会被静默跳过，编译通过、构建通过，直到运行时才
#   NoSuchMethodError。必须在构建后静态扫一遍。
#
# 判定规则：
#   - 以 func_ 开头的，说明已被改写为 srg 名 -> 安全
#   - net/backportapi、net/minecraft/util/math 等本工程自带类 -> 安全
#   - java/lang、java/util 等 JDK 类 -> 安全
#   - 其余 net/minecraft 下的非 func_ 方法 -> 需人工确认
#     （只有 Forge 附加方法才允许保留原名，如 getEntityData / getInstance / isCreatureType）
set -euo pipefail

JAR="${1:-build/carryon-1.0.0.jar}"
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

cd "$(dirname "$0")"
unzip -o -q "$JAR" -d "$TMP"

for f in $(find "$TMP" -name "*.class"); do
  javap -c -p "$f" 2>/dev/null
done \
  | grep -oE "Method net/minecraft/[a-zA-Z/$]+\.[a-zA-Z_0-9]+" \
  | sed 's|Method ||' \
  | sort -u \
  | grep -vE "\.func_[0-9]+_" \
  | grep -vE "^net/minecraft/(util/math|block/state|block/properties|util/text|util/EnumHand|util/EnumActionResult|util/SoundEvent|util/SoundCategory|util/IThreadListener|init/MobEffects|init/SoundEvents|client/renderer/GlStateManager|entity/player/EnumPlayerModelParts)" \
  > /tmp/carryon-unmapped.txt || true

COUNT=$(wc -l < /tmp/carryon-unmapped.txt | tr -d ' ')
echo "未改写为 srg 的原版方法引用：$COUNT 条"
cat /tmp/carryon-unmapped.txt

echo ""
echo "允许清单（Forge 附加方法，运行时保留原名）："
echo "  net/minecraft/entity/Entity.getEntityData"
echo "  net/minecraft/entity/Entity.isCreatureType"
echo "  net/minecraft/client/renderer/RenderBlocks.getInstance"

if [ "$COUNT" -gt 0 ]; then
  echo ""
  echo "请逐条确认上面每一条都是 Forge 附加方法；若属于原版方法，"
  echo "说明映射缺失，必须改用 conf/mcp2srg.srg 中存在的名字。"
fi

echo ""
echo "=== 字段引用检查（非 field_ 的原版字段） ==="
for f in $(find "$TMP" -name "*.class"); do
  javap -c -p "$f" 2>/dev/null
done \
  | grep -oE "Field net/minecraft/[a-zA-Z/$]+\.[a-zA-Z_0-9]+" \
  | sed 's|Field ||' \
  | sort -u \
  | grep -vE "\.field_[0-9]+_" \
  | grep -vE "^net/minecraft/(util/math|block/state|block/properties|util/text|util/EnumHand|util/EnumActionResult|util/SoundEvent|util/SoundCategory|util/IThreadListener|init/MobEffects|init/SoundEvents|client/renderer/GlStateManager|entity/player/EnumPlayerModelParts)" \
  > /tmp/carryon-unmapped-fields.txt || true
cat /tmp/carryon-unmapped-fields.txt
echo "字段条目数：$(wc -l < /tmp/carryon-unmapped-fields.txt | tr -d ' ')"

echo ""
echo "=== 字段引用检查（非 field_ 的原版字段） ==="
for f in $(find "$TMP" -name "*.class"); do
  javap -c -p "$f" 2>/dev/null
done \
  | grep -oE "Field net/minecraft/[a-zA-Z/$]+\.[a-zA-Z_0-9]+" \
  | sed 's|Field ||' \
  | sort -u \
  | grep -vE "\.field_[0-9]+_" \
  | grep -vE "^net/minecraft/(util/math|block/state|block/properties|util/text|util/EnumHand|util/EnumActionResult|util/SoundEvent|util/SoundCategory|util/IThreadListener|init/MobEffects|init/SoundEvents|client/renderer/GlStateManager|entity/player/EnumPlayerModelParts)" \
  > /tmp/carryon-unmapped-fields.txt || true
cat /tmp/carryon-unmapped-fields.txt
echo "字段条目数：$(wc -l < /tmp/carryon-unmapped-fields.txt | tr -d ' ')"
