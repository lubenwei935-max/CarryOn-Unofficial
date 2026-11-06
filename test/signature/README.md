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

### 换成真实 jar 跑

`Repro` 支持显式指定两个类，目录里除 `signed.jar`/`unsigned.jar` 之外的 jar 会自动挂上（真实类需要 guava 之类的依赖才能完成链接）：

```bash
mkdir -p build/real
cp <forge jar>            build/real/signed.jar     # 有签名的那个
cp backportapi-1.0.0.jar  build/real/unsigned.jar   # 无签名的那个
cp guava-17.0.jar         build/real/guava.jar

"<jre1.8.0_251>/bin/java" -cp build/repro Repro build/real \
    net.minecraftforge.common.config.Configuration \
    net.minecraftforge.common.config.ConfigManager
```

实测结果见下表和 `port-analysis/logs/repro-realjars-*.txt`。

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
| 真实 jar（Forge + backportapi），jre 1.8.0_251 | REPRO-OK，异常点名 `ConfigManager`，签名方 `CN=MinecraftForge` |
| 真实 jar（Forge + backportapi），jdk 1.8.0_492 | **REPRO-FAIL** —— 见下 |

本机未装 JDK 17；9 / 21 / 25 三点已覆盖 17 前后的实现，`ClassLoader.checkCerts` 在该区间没有行为分叉。

## 一个反直觉的细节：换新版 JDK 8 会「看不见」这个冲突

同一对真实 jar，两个 JDK 8 给出相反结果。原因是 `jdk.jar.disabledAlgorithms`：

- Forge 1.7.10 的 universal jar 是 **SHA-1 签名的**。从 8u4xx 起，SHA-1 的 jar 签名默认被禁用，
  `jarsigner -verify` 会直接说 `The jar will be treated as unsigned, because it is signed with a weak algorithm`。
- 于是 8u492 上，Forge jar 里的类 **拿不到证书**（`getSigners()` 返回 null），
  和 BackportAPI 那些无签名类「签名一致」，冲突自然不触发；
- 8u251（崩溃日志里的运行环境）还认 SHA-1，Forge 的类带着 `CN=MinecraftForge` 证书，
  一撞就抛 `SecurityException`。

而 Mojang 的 `1.7.10-Forge_10.13.4.1614.jar` 是 **SHA-256 签名**的，两个 JDK 上都能拿到
`CN=Mojang` 证书链（见 `port-analysis/logs/repro-mojang-signers.txt`）。
也就是说：**换新版 JDK 8 只是让 Forge 那一半的冲突隐身了，Mojang 那一半照样是定时炸弹** ——
`EnumHand`、`GlStateManager` 这些类一旦被加载就会炸，只是启动阶段没碰到它们。

结论不变：这不是 JDK 问题，去签名或换包名才是。

## 为什么不能靠参数绕过

签名一致性检查在 `ClassLoader.preDefineClass() → checkCerts()` 里，硬编码，没有 `-D` 开关。

`-Dfml.ignoreInvalidMinecraftCertificates=true` 关掉的是 **FML 自己的证书指纹校验**
（日志第 37–38 行的 `Found valid fingerprint ...` 那段）与**防篡改检查**，
和 `checkCerts` 完全无关 —— 加上它，本复现照样抛异常（见 `repro-with-fml-flag.txt`）。

但要注意：**去掉游戏 jar 的签名之后，必须加这个参数**，否则 FML 会在
`FMLSanityChecker` 里判定 `CRITICAL TAMPERING WITH MINECRAFT` 并拒绝启动。
两者是不同的闸门，不能混为一谈。
