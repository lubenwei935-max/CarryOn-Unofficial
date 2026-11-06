#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""删除 jar 里的签名条目，其余字节不动。

为什么不直接解包重打包：重打包会改变条目顺序、压缩方式、时间戳，
万一还有别的校验（例如 FML 的 package sealing / manifest 摘要）就会引入新变量。

为什么不用 .NET 的 ZipArchive Update 模式：它会顺手重写目录条目
（`cpw/mods/` 这类 0 字节 STORED 条目被写成 csize=2），`unzip -t` 会报 bad CRC。
虽然 Java 不看目录条目，但报告里出现"bad CRC"没法自证清白。

实现：只删中央目录里的对应记录，本地文件头与其余条目的字节完全保持原样。
Zip64 / 数据描述符都不需要处理 —— 1.7.10 的 jar 都是普通 zip，条目数 < 65535。

用法：
    python desig-jar.py <jar 路径> META-INF/XXX.SF META-INF/XXX.RSA [...]
    python desig-jar.py --list <jar 路径>
"""

import os
import shutil
import struct
import sys

EOCD_SIG = b"PK\x05\x06"
CD_SIG = b"PK\x01\x02"
ZIP64_LOCATOR_SIG = b"PK\x06\x07"


def find_eocd(data):
    # EOCD 至少 22 字节，注释最长 65535
    start = max(0, len(data) - 22 - 65535)
    pos = data.rfind(EOCD_SIG, start)
    if pos < 0:
        raise SystemExit("不是有效的 zip：找不到 EOCD")
    return pos


def parse_central_directory(data, cd_offset, cd_size, count):
    entries = []
    pos = cd_offset
    end = cd_offset + cd_size
    for _ in range(count):
        if data[pos:pos + 4] != CD_SIG:
            raise SystemExit("中央目录记录损坏（偏移 %d）" % pos)
        (name_len,) = struct.unpack_from("<H", data, pos + 28)
        (extra_len,) = struct.unpack_from("<H", data, pos + 30)
        (comment_len,) = struct.unpack_from("<H", data, pos + 32)
        name = data[pos + 46:pos + 46 + name_len].decode("utf-8", "replace")
        total = 46 + name_len + extra_len + comment_len
        entries.append((name, pos, total))
        pos += total
    if pos != end:
        raise SystemExit("中央目录长度与 EOCD 声明不一致（%d != %d）" % (pos, end))
    return entries


def list_signature_entries(jar):
    with open(jar, "rb") as fh:
        data = fh.read()
    eocd = find_eocd(data)
    count, cd_size, cd_offset = struct.unpack_from("<HII", data, eocd + 10)
    entries = parse_central_directory(data, cd_offset, cd_size, count)
    return [name for name, _, _ in entries
            if name.upper().startswith("META-INF/") and name.upper().endswith((".SF", ".RSA", ".DSA", ".EC"))]


def remove_entries(jar, targets):
    with open(jar, "rb") as fh:
        data = bytearray(fh.read())

    eocd = find_eocd(data)
    if data.rfind(ZIP64_LOCATOR_SIG, max(0, eocd - 40), eocd) >= 0:
        raise SystemExit("检测到 Zip64 结构，本工具不支持")
    (count,) = struct.unpack_from("<H", data, eocd + 10)
    (cd_size,) = struct.unpack_from("<I", data, eocd + 12)
    (cd_offset,) = struct.unpack_from("<I", data, eocd + 16)

    entries = parse_central_directory(bytes(data), cd_offset, cd_size, count)
    by_name = {name: (pos, total) for name, pos, total in entries}

    missing = [t for t in targets if t not in by_name]
    if missing:
        raise SystemExit("条目不存在：%s" % ", ".join(missing))

    # 逐条拷贝中央目录，跳过目标；本地文件头与数据区原样保留
    new_cd = bytearray()
    kept = 0
    for name, pos, total in entries:
        if name in targets:
            continue
        new_cd += data[pos:pos + total]
        kept += 1

    out = bytearray()
    out += data[:cd_offset]          # 本地数据区完全不动
    out += new_cd
    out += data[eocd:]               # EOCD 之后（通常只有注释）
    # 回填 EOCD 的条目数 / 中央目录大小 / 偏移
    # 注意：8..9 是"本磁盘上的条目数"，10..11 是"总条目数"。两个都要改，
    # 否则 .NET 的 ZipFile 会判定成"拆分/跨区存档"而拒绝打开。
    new_eocd = cd_offset + len(new_cd)
    struct.pack_into("<H", out, new_eocd + 8, kept)
    struct.pack_into("<H", out, new_eocd + 10, kept)
    struct.pack_into("<I", out, new_eocd + 12, len(new_cd))
    struct.pack_into("<I", out, new_eocd + 16, cd_offset)

    tmp = jar + ".tmp"
    with open(tmp, "wb") as fh:
        fh.write(out)
    shutil.move(tmp, jar)
    return count, kept


def main():
    args = sys.argv[1:]
    if not args:
        raise SystemExit(__doc__)

    quiet = "--quiet" in args
    args = [a for a in args if a != "--quiet"]

    if args[0] == "--list":
        if len(args) < 2 or not os.path.isfile(args[1]):
            raise SystemExit("用法：python desig-jar.py --list <jar>")
        print("\n".join(list_signature_entries(args[1])))
        return 0

    jar = args[0]
    if not os.path.isfile(jar):
        raise SystemExit("找不到 %s" % jar)

    targets = set(args[1:])
    if not targets:
        raise SystemExit(__doc__)

    before, after = remove_entries(jar, targets)
    if not quiet:
        print("   条目数 %d -> %d" % (before, after))
        left = list_signature_entries(jar)
        print("   剩余签名条目：%s" % (", ".join(left) if left else "<无>"))
    return 0 if not list_signature_entries(jar) else 1


if __name__ == "__main__":
    sys.exit(main())
