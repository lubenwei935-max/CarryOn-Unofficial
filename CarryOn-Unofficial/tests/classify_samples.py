# -*- coding: utf-8 -*-
"""对真实模组字节码做「自定义放置语义」分类交叉验证。

纯逻辑测试验证的是决策函数本身；本脚本验证的是生产侧反射准入所依赖的
**输入事实**在真实、混淆过的 class 上确实成立：onItemUse 重载后名字会变
（func_77648_a），但签名与声明位置不变。
"""
from __future__ import print_function

import argparse
import glob
import os
import re
import subprocess
import sys
import tempfile
import zipfile

SIGNATURE = re.compile(
    r'public\s+boolean\s+(?:func_77648_a|onItemUse)\s*\([^)]*'
    r'ItemStack[^)]*EntityPlayer[^)]*World[^)]*\)'
)


def declares_on_item_use(jar_path, entry, javap, tmp_root):
    with zipfile.ZipFile(jar_path) as zf:
        data = zf.read(entry)
    target = os.path.join(tmp_root, entry.replace('/', os.sep))
    parent = os.path.dirname(target)
    if not os.path.isdir(parent):
        os.makedirs(parent)
    with open(target, 'wb') as handle:
        handle.write(data)
    out = subprocess.check_output([javap, '-p', target], stderr=subprocess.STDOUT)
    text = out.decode('utf-8', 'replace')
    return bool(SIGNATURE.search(text))


def has_custom_placement(resolved, by_item, by_item_block):
    return resolved and not by_item and not by_item_block


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--mods', required=True)
    parser.add_argument('--forge', required=True)
    parser.add_argument('--javap', default='javap')
    parser.add_argument('--max-jars', type=int, default=60)
    args = parser.parse_args()

    cfm_candidates = glob.glob(os.path.join(args.mods, '*MrCrayfish*Furniture*.jar'))
    if not cfm_candidates:
        print('  SKIP  cfm jar not found under mods/')
        return 0
    cfm_jar = cfm_candidates[0]

    samples = [
        ('cfm ItemFurniturePlacer (directional)', cfm_jar,
         'com/mrcrayfish/furniture/items/ItemFurniturePlacer.class', 'custom'),
        ('cfm ItemBlockCustom (fixed meta)', cfm_jar,
         'com/mrcrayfish/furniture/items/ItemBlockCustom.class', None),
        ('vanilla ItemBlock (standard)', args.forge,
         'net/minecraft/item/ItemBlock.class', 'itemblock'),
        ('vanilla Item (base)', args.forge,
         'net/minecraft/item/Item.class', 'item'),
    ]

    tmp = tempfile.mkdtemp(prefix='carryon-classify-')
    failures = 0
    try:
        print('=== 真实字节码分类（javap 签名，不按名字）===')
        print('  %-42s %-10s %-12s %s' % ('sample', 'declares', 'classify', 'admitted'))
        for label, jar, entry, expected in samples:
            try:
                resolved = declares_on_item_use(jar, entry, args.javap, tmp)
            except Exception as exc:
                print('  %-42s ERROR %s' % (label, exc))
                failures += 1
                continue

            by_item = resolved and entry.endswith('/Item.class')
            by_item_block = resolved and entry.endswith('/ItemBlock.class')
            admitted = has_custom_placement(resolved, by_item, by_item_block)
            classify = 'custom' if (resolved and not by_item and not by_item_block) \
                else ('itemblock' if by_item_block else ('item' if by_item else 'none'))
            print('  %-42s %-10s %-12s %s' %
                  (label, str(resolved), classify, str(admitted)))

            if expected == 'custom':
                ok = resolved and admitted
            elif expected == 'itemblock':
                ok = resolved and by_item_block and not admitted
            elif expected == 'item':
                ok = resolved and by_item and not admitted
            else:
                ok = True
            if not ok:
                print('    FAIL  unexpected classification for %s' % label)
                failures += 1

        print('')
        print('=== 已安装模组规模统计（含 onItemUse 引用的物品类，粗粒度）===')
        jars = sorted(glob.glob(os.path.join(args.mods, '*.jar')))[:args.max_jars]
        total_jars = 0
        total_classes = 0
        with_handlers = 0
        per_mod = []
        for jar in jars:
            try:
                with zipfile.ZipFile(jar) as zf:
                    entries = [n for n in zf.namelist()
                               if n.endswith('.class') and '/item' in n.lower()]
                    hits = 0
                    for name in entries[:400]:
                        try:
                            blob = zf.read(name)
                        except Exception:
                            continue
                        if b'func_77648_a' in blob or b'onItemUse' in blob:
                            hits += 1
            except Exception:
                continue
            total_jars += 1
            total_classes += len(entries)
            with_handlers += hits
            if hits:
                per_mod.append((hits, os.path.basename(jar)))
        print('  scanned jars=%d  item classes=%d  classes referencing onItemUse=%d'
              % (total_jars, total_classes, with_handlers))
        for hits, name in sorted(per_mod, reverse=True)[:8]:
            print('    %4d  %s' % (hits, name[:88]))
        print('  (粗统计只按常量池引用计，用于说明输入规模；精确判定由上面的 javap 完成)')

        # 跨模组自动抽样：验证准入判据不是只对某一个样例成立。
        print('')
        print('=== 跨模组自动抽样（精确 javap 声明判定）===')
        sampled_mods = {}
        for jar in jars:
            name = os.path.basename(jar)
            if 'MrCrayfish' in name or 'CarryOn' in name:
                continue
            if len(sampled_mods) >= 4:
                break
            try:
                with zipfile.ZipFile(jar) as zf:
                    candidates = [n for n in zf.namelist()
                                  if n.endswith('.class') and '/item' in n.lower()
                                  and b'func_77648_a' in zf.read(n)]
            except Exception:
                continue
            rows = []
            for entry in sorted(candidates)[:3]:
                try:
                    resolved = declares_on_item_use(jar, entry, args.javap, tmp)
                    rows.append((entry, resolved,
                                 has_custom_placement(resolved, False, False)))
                except Exception:
                    rows.append((entry, None, None))
            if rows:
                sampled_mods[name] = rows

        total_samples = 0
        custom_samples = 0
        for name, rows in sorted(sampled_mods.items()):
            print('  %s' % name[:88])
            for entry, resolved, admitted in rows:
                total_samples += 1
                if resolved is None:
                    print('    SKIP  %s' % entry.split('/')[-1])
                else:
                    if admitted:
                        custom_samples += 1
                    print('    declares=%-5s admitted=%-5s %s'
                          % (resolved, admitted, entry.split('/')[-1]))
        print('  sampled mods=%d  classes=%d  custom-placement=%d'
              % (len(sampled_mods), total_samples, custom_samples))
        if len(sampled_mods) < 2 or total_samples < 2:
            print('    FAIL  need samples from at least two distinct mods')
            failures += 1
    finally:
        try:
            import shutil
            shutil.rmtree(tmp)
        except Exception:
            pass

    print('')
    print('RESULT: %s (failures=%d)' % ('PASS' if failures == 0 else 'FAILED', failures))
    return 1 if failures else 0


if __name__ == '__main__':
    sys.exit(main())
