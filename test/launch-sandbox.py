#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""P1 · 从命令行直接启动沙箱实例，并自动判定启动结果。

不经过启动器（PCL / HMCL），因此没有"点一次启动"的环节，也不会有启动器
"修复文件"把签名装回去的干扰。启动参数完全由版本 json 推导。

判定：
    READY   —— 日志出现主菜单前最后一段（SoundManager 加载完毕 / FML 成功加载完成）
    CRASH   —— 日志出现签名冲突或 FML 致命错误
    TIMEOUT —— 超时未见上述任一标记

退出码：0 = READY，2 = CRASH，3 = TIMEOUT，4 = 启动失败（找不到 java / json / 库）。

用法：
    python test/launch-sandbox.py                     # 默认沙箱 + JDK 8
    python test/launch-sandbox.py --dry-run           # 只打印命令行
    python test/launch-sandbox.py --out port-analysis/logs/xxx.log
"""

import argparse
import json
import os
import shutil
import subprocess
import sys
import time

DEFAULT_MC = r"E:\WM-test\.minecraft"
DEFAULT_VER = "1.7.10-Forge_10.13.4.1614"
# 崩溃日志第 5 行的运行环境就是这一份 JRE；基线对照必须用它，否则
# 新版 JDK 8 会把 SHA-1 签名的 Forge jar 当作"未签名"，触发点会变。
DEFAULT_JAVA = r"D:\Program Files (x86)\Java\jre1.8.0_251\bin\java.exe"

CRASH_MARKERS = (
    "signer information does not match",
    "Fatal errors were detected during the transition",
    "There was a severe problem during mod loading",
)

# 1.7.10 进主菜单前，SoundManager 会打完这两行；FML 也会打一行"成功加载 N 个模组"。
READY_MARKERS = (
    "Forge Mod Loader has successfully loaded",
    "SoundManager: Loading sounds",
)


def _version_key(version):
    """把 '17.0' / '3.3.2' / '2.9.1' 变成可比较的元组，用于同 artifact 择优。"""
    key = []
    for chunk in version.replace("-", ".").replace("_", ".").split("."):
        digits = "".join(ch for ch in chunk if ch.isdigit())
        key.append(int(digits) if digits else -1)
    return tuple(key)


def build_classpath(mc_dir, version_dir, meta):
    """按 group:artifact 去重，同 artifact 保留版本号最高的一个。

    1.7.10 的 Forge json 是在原版 json 上追加的，所以 guava 会同时出现
    15.0（原版）与 17.0（Forge）。若不去重，classpath 上前者先命中，
    FML 会踩 `NoSuchMethodError: CharSource.readLines(LineProcessor)`。
    """
    lib_root = os.path.join(mc_dir, "libraries")
    picked, order, missing = {}, [], []

    for lib in meta.get("libraries", []):
        if lib.get("natives"):
            continue  # 本地库已解压到 natives 目录，不需要进 classpath
        name = lib.get("name", "")
        parts = name.split(":")
        if len(parts) < 3:
            continue
        artifact_key = parts[0] + ":" + parts[1]
        version = parts[2]

        artifact = (lib.get("downloads") or {}).get("artifact") or {}
        rel = artifact.get("path")
        if not rel:
            rel = "%s/%s/%s/%s-%s.jar" % (
                parts[0].replace(".", "/"), parts[1], version, parts[1], version)
        path = os.path.join(lib_root, rel.replace("/", os.sep))
        if not os.path.isfile(path):
            missing.append(path)
            continue

        if artifact_key in picked:
            if _version_key(version) > _version_key(picked[artifact_key][0]):
                picked[artifact_key] = (version, path)
            continue
        picked[artifact_key] = (version, path)
        order.append(artifact_key)

    entries = [picked[k][1] for k in order]

    client_jar = os.path.join(version_dir, meta["id"] + ".jar")
    if not os.path.isfile(client_jar):
        missing.append(client_jar)
    else:
        entries.insert(0, client_jar)

    return entries, missing


def substitute(template, mapping):
    out = template
    for key, value in mapping.items():
        out = out.replace("${%s}" % key, value)
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--mc", default=DEFAULT_MC)
    ap.add_argument("--version", default=DEFAULT_VER)
    ap.add_argument("--java", default=DEFAULT_JAVA)
    ap.add_argument("--user", default="SandboxBot")
    ap.add_argument("--timeout", type=int, default=300)
    ap.add_argument("--xmx", default="4G")
    ap.add_argument("--out", default=None, help="把 latest.log 复制到该路径")
    ap.add_argument("--ignore-mc-certs", action="store_true",
                    help="追加 -Dfml.ignoreInvalidMinecraftCertificates=true"
                         "（去掉游戏 jar 签名后，FML 的防篡改检查需要它放行）")
    ap.add_argument("--dry-run", action="store_true")
    args = ap.parse_args()

    version_dir = os.path.join(args.mc, "versions", args.version)
    json_path = os.path.join(version_dir, args.version + ".json")
    log_path = os.path.join(version_dir, "logs", "latest.log")

    if not os.path.isfile(json_path):
        print("找不到版本 json：%s" % json_path, file=sys.stderr)
        return 4
    if not os.path.isfile(args.java):
        print("找不到 java：%s" % args.java, file=sys.stderr)
        return 4

    with open(json_path, "r", encoding="utf-8") as fh:
        meta = json.load(fh)

    cp, missing = build_classpath(args.mc, version_dir, meta)
    if missing:
        print("警告：%d 个库缺失，前 3 个：" % len(missing), file=sys.stderr)
        for item in missing[:3]:
            print("  " + item, file=sys.stderr)

    natives = os.path.join(version_dir, args.version + "-natives")
    mapping = {
        "auth_player_name": args.user,
        "version_name": args.version,
        "game_directory": version_dir,
        "assets_root": os.path.join(args.mc, "assets"),
        "assets_index_name": meta.get("assets", "1.7.10"),
        "auth_uuid": "00000000000000000000000000000000",
        "auth_access_token": "0",
        "user_properties": "{}",
        "user_type": "legacy",
    }

    cmd = [
        args.java,
        "-Xmx" + args.xmx,
        "-Djava.library.path=" + natives,
        "-Dfml.ignorePatchDiscrepancies=true",
        "-cp", os.pathsep.join(cp),
        meta["mainClass"],
    ]
    if args.ignore_mc_certs:
        cmd.insert(1, "-Dfml.ignoreInvalidMinecraftCertificates=true")
    cmd += substitute(meta["minecraftArguments"], mapping).split(" ")

    print("java      : %s" % args.java)
    print("gameDir   : %s" % version_dir)
    print("classpath : %d 个条目" % len(cp))
    print("日志      : %s" % log_path)
    print()

    if args.dry_run:
        print(" ".join('"%s"' % c if " " in c else c for c in cmd))
        return 0

    # 清掉上一次的 latest.log，避免读到旧标记
    if os.path.isfile(log_path):
        os.remove(log_path)

    proc = subprocess.Popen(cmd, cwd=version_dir)

    deadline = time.time() + args.timeout
    verdict, hit = "TIMEOUT", None
    while time.time() < deadline:
        time.sleep(2)
        if os.path.isfile(log_path):
            try:
                with open(log_path, "r", encoding="utf-8", errors="replace") as fh:
                    text = fh.read()
            except OSError:
                text = ""
            for marker in CRASH_MARKERS:
                if marker in text:
                    verdict, hit = "CRASH", marker
                    break
            if verdict == "CRASH":
                break
            for marker in READY_MARKERS:
                if marker in text:
                    verdict, hit = "READY", marker
                    break
            if verdict == "READY":
                break
        if proc.poll() is not None:
            verdict, hit = "CRASH", "进程已退出（code=%s）" % proc.returncode
            break

    # 给尾部日志一点落盘时间，再快照
    time.sleep(3)
    if os.path.isfile(log_path) and args.out:
        os.makedirs(os.path.dirname(os.path.abspath(args.out)), exist_ok=True)
        shutil.copyfile(log_path, args.out)
        print("已保存日志：%s" % args.out)

    if proc.poll() is None:
        proc.terminate()
        try:
            proc.wait(timeout=10)
        except subprocess.TimeoutExpired:
            proc.kill()

    print()
    print("判定: %s  (命中标记: %s)" % (verdict, hit))
    return 0 if verdict == "READY" else 2 if verdict == "CRASH" else 3


if __name__ == "__main__":
    sys.exit(main())
