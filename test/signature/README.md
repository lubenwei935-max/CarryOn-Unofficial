# 签名冲突最小复现（P0）

离线复现 `SecurityException: ... signer information does not match`，不依赖游戏、不依赖 Forge。
用于证明：**这是 JVM 的「同包签名一致性」检查，与 JDK 版本、模组代码、混淆全部无关。**

## 结构

```
test/signature/
├── src/
│   ├── pkg/Signed.java      # 模拟被签名 jar 里的类（Forge 的 Configuration / Mojang 的 EnumFacing）
│   ├── pkg/Unsigned.java    # 模拟 BackportAPI 塞进同包的类（ConfigManager / EnumHand）
│   └── Repro.java           # 用 URLClassLoader 先加载一个、再加载另一个，观察是否抛异常
├── build.sh                 # 编译 → 打两个 jar → 自签 keystore → jarsigner 签 signed.jar
└── run.sh                   # 用指定 JDK 运行，退出码 0 = 复现成功
```

产物（`build/`，不提交）：

| 文件 | 内容 |
|---|---|
| `signed.jar` | `pkg/Signed.class` + `META-INF/TESTKEY.SF|.RSA` |
| `unsigned.jar` | `pkg/Unsigned.class`，无签名 |
| `repro/` | `Repro.class` |

关键点：两个类**必须来自不同的 CodeSource**（两个 jar），同一个 jar 里没法同时有「签过名」和「没签过」的条目
—— 这正是游戏里的情形：签名来自游戏 jar，BackportAPI 是另一个 jar。

## 用法

```bash
cd test/signature
./build.sh                                        # 默认用 Temurin JDK 8
./run.sh                                          # 默认 JDK 8，先加载 pkg.Signed
./run.sh "D:/Program Files (x86)/Java/jdk21"      # 换 JDK
./run.sh "<jdk>" pkg.Unsigned                     # 反序加载，对照实验
```

退出码：`0` = 复现成功（抛出了预期的 `SecurityException`），`1` = 没复现。

## 预期输出

```
[1] loaded  pkg.Signed   signers=[1] X.509:CN=Signature Conflict Repro, O=BackportAPI Test
[2] java.lang.SecurityException: class "pkg.Unsigned"'s signer information does not match signer information of other classes in the same package
	at java.lang.ClassLoader.checkCerts(...)
	at java.lang.ClassLoader.preDefineClass(...)
	at java.lang.ClassLoader.defineClass(...)
	at java.security.SecureClassLoader.defineClass(...)
	at java.net.URLClassLoader.defineClass(...)
REPRO-OK
```

栈帧顺序与崩溃日志第 291–295 行逐帧一致（日志里 `URLClassLoader` 换成了 `LaunchClassLoader`，
因为 Forge 的 `LaunchClassLoader` 继承自 `URLClassLoader` 并复用了 `defineClass` 的实现）。

## 实测结果

见 `port-analysis/logs/repro-*.txt`，以及报告 §2。

| JVM | 结果 |
|---|---|
| jre 1.8.0_251（崩溃日志同款） | REPRO-OK |
| jdk 1.8.0_492 | REPRO-OK |
| jdk 9.0.7.1 | REPRO-OK |
| jdk 21.0.3 | REPRO-OK |
| jdk 25.0.3 | REPRO-OK |
| jdk 8，反序加载 | REPRO-OK（异常改指向 `pkg.Signed`） |

本机未装 JDK 17；9 / 21 / 25 三点已覆盖 17 前后的实现，`ClassLoader.checkCerts` 在该区间没有行为分叉。

## 为什么不能靠参数绕过

签名一致性检查在 `ClassLoader.preDefineClass() → checkCerts()` 里，硬编码，没有 `-D` 开关。

`-Dfml.ignoreInvalidMinecraftCertificates=true` 关掉的是 **FML 自己的证书指纹校验**
（日志第 37–38 行的 `Found valid fingerprint ...` 那段），
和 `checkCerts` 完全无关 —— 加上它，本复现照样抛异常，游戏照样崩。
