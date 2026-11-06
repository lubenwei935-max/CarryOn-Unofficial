# 签名冲突专项测试报告（test/signature-conflict）

**分支**：`test/signature-conflict`（基线 `5311559`，`CarryOn.VERSION` = 1.0.3；**不合并回主干**）
**被测对象**：`CarryOn-Unofficial` 1.0.3 + 前置 `BackportAPI` 1.0.0
**沙箱实例**：`E:\WM-test\.minecraft`（从 `E:\Weapon And Magic\.minecraft` 复制，原件全程只读）
**测试时间**：2026-11-06 13:52 – 14:30（GMT+8）
**结论一句话**：**A 可用（已实测进主菜单）——但要「去签名 + 加一个 JVM 参数」两步；B 代价高于预期（迁 2 个类约 12 分钟，但 preInit 照样崩，必须整包迁移 7 个包/19 个类，估 2–4 小时）；护栏已就位（新增冲突退出码 1）。**

---

## 0. 先纠正两处事实（任务单/旧报告里的偏差）

实测发现两处与技术预期不符，先说清楚，后面的结论都建立在这上面：

| # | 原说法 | 实测 | 证据 |
|---|---|---|---|
| 1 | 要删签名的是 `...-universal.jar` | **不是**。实际被挂上 classpath 的是同目录下**没有** `-universal` 后缀的那个；`-universal` 是安装器残留，版本 json 里根本没有 `downloads.artifact` | 崩溃日志 MOD 列表写的是 `(forge-1.7.10-10.13.4.1614-1.7.10.jar)`；json 里该 library 无 `downloads.artifact`；两个文件内容同为 3018833 字节，但只有前者被加载 |
| 2 | `-Dfml.ignoreInvalidMinecraftCertificates=true` 完全无用 | **对 `checkCerts` 无用（已证），但去签名后必须加**，否则 FML 的防篡改检查直接拒绝启动 | 只去签名不加参数 → `CRITICAL TAMPERING WITH MINECRAFT`（`after-desig` 首跑日志）；加参数后正常 |

两条都不改变"根因是包签名冲突"的判断，但直接影响"照文档操作能不能成功"。

---

## 1. 一句话结论展开

| 问题 | 回答 | 依据 |
|---|---|---|
| 这个模组到底能不能跑起来 | **能**。沙箱里 16 个模组全部加载成功，进入主菜单 | §3，`logs/after-desig.log:247` |
| 用哪条路修 | **方案 A：删签名**（0 行代码）+ 启动参数加 `-Dfml.ignoreInvalidMinecraftCertificates=true` | §3.3 |
| 代价多少 | A：每实例 2 分钟、3 个 jar、可脚本化；B：2 类切片 12 分钟仍不通过，全量 2–4 小时 | §3、§4 |
| 与 JDK 有关吗 | **没有**。同一个类加载检查在 1.8.0_251 / 1.8.0_492 / 9 / 21 / 25 上抛同一句话 | §2.1 |
| 换新版 JDK 8 会不会"自己好" | **会伪装成好了**：8u492 把 SHA-1 签名的 Forge jar 视为未签名，Forge 那半边的冲突隐身；Mojang 那半边（`net.minecraft.util` 等 9 个类）依然是定时炸弹 | §2.3 |
| 护栏 | 已就位，新增冲突退出码 1 并点名 | §5 |

---

## 2. 复现实验（P0）

### 2.1 最小复现：五个 JVM，同一句话

`test/signature/` 用两个 jar（一个自签、一个不签）复现"同包签名不一致"：

```
[1] loaded  pkg.Signed   signers=[1] X.509:CN=Signature Conflict Repro, O=BackportAPI Test
[2] java.lang.SecurityException: class "pkg.Unsigned"'s signer information does not match
    signer information of other classes in the same package
	at java.lang.ClassLoader.checkCerts(...)
	at java.lang.ClassLoader.preDefineClass(...)
	at java.lang.ClassLoader.defineClass(...)
	at java.security.SecureClassLoader.defineClass(...)
	at java.net.URLClassLoader.defineClass(...)
REPRO-OK
```

栈帧顺序与线上日志第 291–298 行**逐帧一致**（日志里 `URLClassLoader` 换成 `LaunchClassLoader`，
因为后者继承自 `URLClassLoader` 并复用了 `defineClass`）。

| JVM | java.version | 结果 |
|---|---|---|
| jre 1.8.0_251（**崩溃日志同款**） | 1.8.0_251 | REPRO-OK |
| Temurin JDK 8 | 1.8.0_492 | REPRO-OK |
| JDK 9 | 9.0.7.1 | REPRO-OK |
| JDK 21 | 21.0.3 | REPRO-OK |
| JDK 25 | 25.0.3 | REPRO-OK |
| jdk 8 反序加载 | 1.8.0_492 | REPRO-OK（异常改指向 `pkg.Signed`） |

本机没有 JDK 17；9 / 21 / 25 三点已覆盖 17 前后，`checkCerts` 在该区间没有行为分叉。
完整输出：`logs/repro-*.txt`（共 10 份）。

### 2.2 换成真实 jar 仍然成立

把 `signed.jar` 换成 Forge universal、`unsigned.jar` 换成 `backportapi-1.0.0.jar`，
直接加载 `Configuration` → `ConfigManager`：

```
### jre 1.8.0_251
[1] loaded  net.minecraftforge.common.config.Configuration
            signers=[1] X.509:CN=MinecraftForge, OU=MinecraftForge, ...
[2] java.lang.SecurityException: class "net.minecraftforge.common.config.ConfigManager"'s
            signer information does not match signer information of other classes in the same package
```

证据：`logs/repro-realjars-jre251.txt`。这条实验把"日志推断"钉成了"可复现"。

### 2.3 反直觉发现：8u492 上同一对 jar **不抛异常**

```
### jdk 1.8.0_492
[1] loaded  net.minecraftforge.common.config.Configuration   signers=<none>
[2] loaded  net.minecraftforge.common.config.ConfigManager   signers=<none>
```

原因不是 JVM 行为变了，是 **"证书还在不在"变了**：

```
$ jarsigner -verify forge-1.7.10-10.13.4.1614-1.7.10-universal.jar
The jar will be treated as unsigned, because it is signed with a weak algorithm that is now disabled.
```

Forge 1.7.10 的 jar 是 **SHA-1 签名**。新版 JDK 8 的 `jdk.jar.disabledAlgorithms` 默认禁用
SHA-1 的 jar 签名，于是 Forge jar 里的类 `getSigners()` 返回 null，和无签名的 BackportAPI「签名一致」，
冲突自然不触发。而 Mojang 的版本 jar 是 **SHA-256 签名**，两个 JDK 上都能拿到 `CN=Mojang` 证书链：

```
### jre 1.8.0_251             ### jdk 1.8.0_492
[1] ... ClientBrandRetriever  signers=[4] X.509:CN=Mojang, ...   （两边相同）
```

证据：`logs/repro-mojang-signers.txt`。

**这条直接解释了 §3.5 的现象**：把沙箱 JDK 从 251 换成 492，**不去签名也能进主菜单**
（实测：`logs/baseline-jdk8u492.log`，判定 READY，16 个模组加载成功）。
但那是假象 —— Mojang 那半边的包仍是签名的，`EnumHand` / `GlStateManager` 这些类一旦被加载就会炸。
启动阶段恰好没碰到它们而已。

> ⚠️ 推断部分：具体"第一次右键"是否会立刻触发 `EnumHand` 的加载，本次**没有实测**
> （无法自动操作鼠标）。机制侧证据齐全，时机侧属推断，请按 §6 的自测步骤确认。

### 2.4 参数绕不过去（已实测）

带 `-Dfml.ignoreInvalidMinecraftCertificates=true` 跑最小复现，异常照旧：
`logs/repro-with-fml-flag.txt`。该参数关的是 FML 自己的指纹/防篡改检查，和 `checkCerts` 无交集。

---

## 3. 方案 A 实测（去签名）

### 3.1 沙箱

```
E:\Weapon And Magic\.minecraft  →  E:\WM-test\.minecraft     （929 MB）
  versions\1.7.10-Forge_10.13.4.1614\   62 MB    真实复制（含 mods/、natives/、options.txt）
  libraries\                            848 MB   真实复制
  assets\                               2.0 GB   **目录联接（junction）指向原件，只读**
  config\ / asm\ / addons\ …            小目录   真实复制
  saves\ / backups\ / screenshots\      未复制（不碰真实存档）
```

真实实例全程只读；`E:\Weapon And Magic` 一个字节都没改。

启动不经过启动器（避开"启动器修复会装回签名"这条坑），用 `test/launch-sandbox.py`
按版本 json 拼命令行直接拉起，并按日志标记判定 READY / CRASH / TIMEOUT。

### 3.2 基线（未去签名）：与线上崩溃同款

`logs/baseline-crash.log`（445 行）：

```
L55   Found valid fingerprint for Minecraft Forge. ... e3c3d50c7c986df74c645c0ac54639741c90a557
L56   Found valid fingerprint for Minecraft.       ... cd99959656f753dc28d863b46769f7f8fbaefcfc
L230  Fatal errors were detected during the transition from PREINITIALIZATION to INITIALIZATION
L247  UCE   backportapi{1.0.0}
L248  UCE   carryonunoffical{1.0.3}
L340  Caused by: java.lang.SecurityException: class "net.minecraftforge.common.config.ConfigManager"'s
      signer information does not match signer information of other classes in the same package
```

与 `C:\Users\36039\Desktop\崩溃报告.txt` 的指纹、状态、异常文本完全一致。

### 3.3 去签名：3 个 jar，只动中央目录

| jar | 删除的条目 | 条目数 | 加载? |
|---|---|---|---|
| `versions\...\1.7.10-Forge_10.13.4.1614.jar` | `MOJANGCS.SF`、`MOJANGCS.RSA` | 2847 → 2845 | 是（Mojang 签） |
| `libraries\...\forge-1.7.10-10.13.4.1614-1.7.10.jar` | `FORGE.SF`、`FORGE.DSA` | 976 → 974 | **是（实际加载的那个）** |
| `libraries\...\forge-...-universal.jar` | `FORGE.SF`、`FORGE.DSA` | 976 → 974 | 否，防御性处理 |

实现：`test/desig-jar.py` 只重写中央目录记录，**本地数据区一个字节不动**。
验证：

```
$ unzip -t 1.7.10-Forge_10.13.4.1614.jar        → No errors detected
$ unzip -t forge-...-1.7.10-universal.jar       → No errors detected
$ jarsigner -verify 1.7.10-Forge_10.13.4.1614.jar → jar is unsigned
```

> 踩过的坑，供实现者参考：
> ① .NET 的 `ZipArchive` Update 模式会把 0 字节的目录条目重写成 `csize=2`，`unzip -t` 报 bad CRC；
> ② 自己拼 EOCD 时只改了「总条目数」没改「本磁盘条目数」，.NET 判定成"拆分存档"直接拒开。
> 两个坑都在 `test/desig-jar.py` 里修掉了，脚本可直接复用。

回滚一条命令：

```powershell
powershell -ExecutionPolicy Bypass -File test\desig-sandbox.ps1 -Revert
# 等价于：Copy-Item "<jar>.orig" "<jar>" -Force
```

（实测回滚 → 签名条目全部回来 → 再跑一次仍是原来的崩溃，见 `logs/desig-revert.txt`。）

### 3.4 结果：从 UCE 到主菜单

`logs/after-desig.log`（289 行）：

```
L215  Forge Mod Loader has identified 16 mods to load
L228  BackportAPI 1.0.0 initialized
L241  Offhand provider (Backhand) detected
L246  Sound engine started
L247  Forge Mod Loader has successfully loaded 16 mods
```

| 硬指标 | 结果 |
|---|---|
| 日志出现 `signer information does not match` | **0 次**（全文 grep） |
| `backportapi` / `carryonunoffical` 的 FML 状态 | **不再出现 `E`** —— 无异常表（FML 只在出错时打这张表），最终 `successfully loaded 16 mods` |
| 进主菜单 | **是**（`Sound engine started` 是主菜单前的最后一步） |
| 进世界搬放方块 | **未测**。无法自动操作鼠标键盘；需人工按 §6 走一遍 |

`2 个 mod 的状态不是 E` 这条，用"反向证据"表述：崩溃时 FML 会打 `UCE backportapi` / `UCE carryonunoffical`
（基线日志 L247-248、L435-436 各一次），修复后这张表根本不存在，且 `has successfully loaded 16 mods`
只会在没有 `E` 状态时打印。

### 3.5 副作用与前提（务必写进用户说明）

1. **必须加 JVM 参数** `-Dfml.ignoreInvalidMinecraftCertificates=true`。
   只去签名不加参数 → `The minecraft jar ... appears to be corrupt! There has been CRITICAL TAMPERING
   WITH MINECRAFT` → FML 拒绝启动。**这不是可选项。**
2. FML 的指纹日志会退化：不再有 `Found valid fingerprint for Minecraft Forge`。
   想消掉 `FML appears to be missing any signature data` 这条 ERROR 也没有正当办法 —— 它说的就是事实。
3. 沙箱 JDK 从 8u251 换成 8u492 时，**不去签名也能进主菜单**（见 §2.3）。
   千万别据此认为"升级 JDK 修好了"。
4. 其它观察：MOD 数量 16、Backhand 副手识别正常、资源包/材质无异常；
   唯一的 ERROR 是 GTNHLib 的 `domain minecraft has 2 locations`（与签名无关，基线里也有）。

### 3.6 耗时

| 步骤 | 墙钟 |
|---|---|
| 复制沙箱（929 MB，同盘） | ~2 min |
| 基线启动到崩溃 | ~40 s |
| 写去签名脚本 + 调试两个 zip 坑 | ~15 min |
| 去签名 3 个 jar | < 5 s |
| 启动到主菜单 | ~90 s |

**给别人复用的成本**：`test/desig-sandbox.ps1` 一次调用，约 2 分钟/实例。

---

## 4. 方案 B 切片实测（限时）

### 4.1 做了什么

在 `E:\WM-test\p2` 的**独立副本**里（不动工作区，避免与手头未提交的 1.0.3 改动纠缠）：

- `net.minecraftforge.common.config.ConfigManager` → `net.backportapi.shim.config.ConfigManager`
- `net.minecraft.util.EnumHand` → `net.backportapi.shim.util.EnumHand`
- 改 5 处 import（backportapi 2 处、carryon 3 处）

### 4.2 规模（实测，非估算）

```
backportapi：4 文件，+8 -4     （2 个移动 + 2 处 import）
carryon   ：3 文件，+3 -3     （3 处 import）
合计      ：7 文件，+11 -7
```

diff 备份：`test/fixtures/p2-shim-slice.patch`。

编译期多冒出来一处隐藏依赖：`ConfigManager` 除了 `Config`、`Configuration` 还要
`import net.minecraftforge.common.config.Property` —— 迁一个类不等于迁一个包。

### 4.3 重混淆校验：没有新增未映射项

```
基线（原 jar）        ：未改写为 srg 的原版方法引用 5 条，字段 2 条
切片（迁移后 jar）    ：未改写为 srg 的原版方法引用 5 条，字段 2 条
diff                  ：两份清单完全相同（0 新增）
```

证据：`logs/p2-verify-base.txt`、`logs/p2-verify-work.txt`。
（包名迁移不影响原版方法名的 SRG 映射，符合预期；但按流程仍然要跑。）

护栏对切片产物的判定也很直观 —— **冲突类从 19 降到 17**，并提示白名单可收紧：

```
冲突包 7 个，落进去的类 17 个：
  net.minecraft.util                            Mojang  4 个
  net.minecraftforge.common.config              Forge   5 个
  ...
提示：白名单里有 2 个类已不在产物中，可以把它们从 known-conflicts.txt 删掉（可收紧）：
   net.minecraft.util.EnumHand
   net.minecraftforge.common.config.ConfigManager
```

### 4.4 关键判定：**preInit 仍然过不去**

把切片产物装进**没去签名**的沙箱（JDK 8u251）跑，仍然崩，而且触发点前移：

```
UCE  backportapi{1.0.0}
Caused by: java.lang.SecurityException:
  class "net.minecraftforge.common.config.Config"'s signer information does not match ...
Caused by: java.lang.SecurityException:
  class "net.minecraftforge.event.entity.player.PlayerContainerEvent$Close"'s signer information does not match ...
```

证据：`logs/p2-slice-run.log:300`、`:353`。

读法：

1. `ConfigManager` 搬走了，但它的 `sync()` 里第一件事就是
   `field.isAnnotationPresent(Config.class)` —— **必须加载 `Config` 注解类**，
   而注解类还在原包。搬了外壳，没搬内核。
2. 第二个触发点是 `PlayerContainerEvent$Close`，说明 BackportAPI 自己的
   `preInit`/`init` 还会碰别的原包类。**按类迁移是打地鼠，必须按包整包迁。**

所以任务单里那个"唯一有价值的判定"——"只要 ConfigManager + EnumHand 都过，方案 B 就成立"——
**答案是：不成立。** 这两个类过了没用，下一个立刻接上。

### 4.5 外推 19 个类要多久

12 分钟做了 2 个类，但**不能线性乘 9.5**，理由有三：

- 这 12 分钟里有约 3 分钟是构建/启动/校验的固定开销（与类数无关）；
- `net.minecraftforge.common.config` 是**注解包**：6 个类必须一起走，还要改所有 `@Config.Comment`
  这类写法（注解不能"半搬"，因为它必须和 `ConfigManager` 在同一个包才能被反射读到）；
- `net.minecraft.util` 的 5 个类（`EnumHand`/`SoundEvent`/`SoundCategory`/`EnumActionResult`/`IThreadListener`）
  是高频 API，调用点比 `ConfigManager` 多得多。

| 包 | 类数 | 迁移难度 | 估时 |
|---|---|---|---|
| `net.minecraftforge.common.config` | 6 | 高（注解语义 + 所有配置类都要改 import） | 60–90 min |
| `net.minecraft.util` | 5 | 高（`EnumHand` 牵动 `Hands` 全 API） | 40–60 min |
| `net.minecraft.client.renderer` | 1 | 中（`GlStateManager` 调用点分散在渲染代码） | 20–30 min |
| `net.minecraft.init` | 2 | 低（纯常量转发） | 10–15 min |
| `net.minecraftforge.event` | 1 | 低 | 10 min |
| `net.minecraftforge.event.entity.player` | 3 | 低 | 10–15 min |
| `net.minecraft.entity.player` | 1 | 低 | 10 min |
| **合计** | **19** | | **2.5–4 h 编码 + 半天回归** |

再叠加 §4.4 的教训：**每个包都必须整包迁**，中途任何一个类留在原包，就等于没迁。

另外代价不在工时，在**产品定位**：`@Config` / `EnumHand` / `GlStateManager` 正是"1.8 代码零改编译"
的招牌三件套。迁完之后，这三样对移植者的吸引力直接消失（§4.3 里那 17 个类也一样会消失）。

---

## 5. 护栏（本分支真正的长期产出）

### 5.1 用法

```bash
./check-packages.sh                          # 用 build/ 下最新的 backportapi-*.jar
./check-packages.sh path/to/x.jar            # 指定产物（验收/CI 用）

powershell -ExecutionPolicy Bypass -File check-packages.ps1 [-Jar path]
```

已挂在 `CarryOn-Unofficial/verify.sh` 末尾（非阻断：`if ! bash ... then` 打警告，不中断构建），
`backportapi/README.md` 的构建章节也加了一行说明。

### 5.2 判定逻辑（两处容易写错的地方）

1. **必须比"包"，不能比"类名"**。BackportAPI 的类在 1.7.10 里都不存在，类名交集恒为 0；
   冲突发生在"包已存在且已签名"这个层面。
2. **包匹配要精确**，不能用前缀。`net.minecraft.util.text` 在 1.7.10 里**不存在**，
   用 `net.minecraft.util.` 前缀会把 13 个无辜的类算成冲突（第一次实现就这么错了，19 变成 32）。
3. 对照必须用 `deps/forgebin.jar` + `deps/mcp.jar`（反混淆、包名可读）；
   生产版 jar 的包名是混淆的，比出来没有意义。

### 5.3 验收

| 场景 | 命令 | 退出码 | 输出 |
|---|---|---|---|
| 正常产物 | `./check-packages.sh` | **0** | `冲突包 7 个，落进去的类 19 个` + `结果：PASS` |
| 注入 `net.minecraft.util.ProbeClass` | `./check-packages.sh build/probe-jar.jar` | **1** | `!! 新增冲突类（不在白名单里）： Mojang  net.minecraft.util.ProbeClass （包 net.minecraft.util）` + `结果：FAIL` |
| 移除该测试类 | 同上（换成干净 jar） | **0** | `结果：PASS` |
| 白名单里有、产物里没有 | 人为加一条过期条目 | **0** | `提示：白名单里有 1 个类已不在产物中……（可收紧）` |

证据：`logs/guard-pass.txt`、`logs/guard-fail.txt`。
验收用的探针源码留在 `test/fixtures/probe-src/`（不参与真实构建）。

---

## 6. 风险与遗留

### 6.1 启动器会把签名装回去（**最高风险**）

PCL / HMCL 的"修复文件/完整性检查"会重新下载被改过的 jar。表现是：昨天还好，今天又崩。
处置：把 `test/desig-sandbox.ps1` 做成玩家可双击的 `.bat`，每次"被修复"后重跑一次。
报告里给用户的话术要写死这一点。

### 6.2 多人服务器

本次只测了客户端。机制上服务端同样会在 `preInit` 撞同一堵墙（`BackportAPI` 是双端 mod，
`ConfigManager.sync` 在服务端也会走），但**未实测**。
另外：**不能把去签名的 Minecraft/Forge jar 打包进整合包分发** —— 那是 Mojang/Forge 的再分发物，
且违反本分支的硬约束。可行做法是"随包提供脚本，由玩家在自己的安装目录里执行"。

### 6.3 换 JDK 会制造"已经修好"的假象

§2.3 已证：8u492 上不去签名也能进主菜单。如果有人因此收工，第一次右键搬方块就会炸在 `EnumHand`。
**在 README / 用户说明里必须封死这条路。**

### 6.4 FML 防篡改这道闸

去签名之后必须带 `-Dfml.ignoreInvalidMinecraftCertificates=true`，否则连主菜单都到不了。
这条要和"这个参数治不了 checkCerts"一起写，否则又会有人拿它当万能药。

### 6.5 未测到的部分

- **进世界后的搬起/放下、叠生物、朝向**：无法自动操作输入，需人工 5 分钟验证（见 §6.6）。
- **服务端**：见 6.2。
- **加了其他 1.8-API 回填库的整合包**：护栏只覆盖本库自身，别的库往 `net.minecraft.*` 塞类同样会炸，
  本脚本管不到。

### 6.6 人工自测步骤（给委托方）

1. 启动器里把 JVM 参数加上 `-Dfml.ignoreInvalidMinecraftCertificates=true`；
2. 进主菜单 → 新建创造世界；
3. Shift+右键搬一个**箱子**，放下，看朝向是否与放置时一致（对照组：熔炉）；
4. 搬一只**牛**，放下，看是否正常；
5. 退出，看 `logs/latest.log` 有没有 `SecurityException` / `NoClassDefFoundError`。
   只要出现 `net.minecraft.util.EnumHand` 相关错误，说明**又回到了"被 JDK 假象骗过"的老路**。

### 6.7 无害噪声（别被带偏）

- `org.joml-joml-1.10.8.jar` 的 `module-info.class` 读不了（ASM 5 不认 class 53）→ GTNHLib 的依赖下载，
  与本次无关，基线里就有。
- `domain minecraft has 2 locations` → GTNHLib 资源包与 Default 冲突，纯警告。
- `@Inject(@At("INVOKE")) Shift.BY=3 ... exceeds the maximum allowed value` → GTNHLib 的 mixin 警告。

---

## 7. 给主干的建议（只写建议，不改主干）

1. **发布路径走 A，不要动代码。** 附一个 `desig` 脚本 + 一句 JVM 参数，比迁 19 个类划算得多。
2. **README 的两处更正建议单独提 PR 进主干**：
   - "类名放在 `net.minecraft.*` 下有风险吗" 一节现在只讲撞名，漏了签名包冲突（本分支已补，见 `backportapi/README.md`）；
   - 构建章节加护栏那一行。
3. **`check-packages.sh` + `conf/known-conflicts.txt` 建议进主干**，并把 `verify.sh` 末尾的调用一起带上。
   它不解决冲突，但能防"又有人往 `net.minecraft.util` 里塞类"。
4. **`verify.sh` 的清理 trap 建议单独修**（本分支顺手修了）：
   `trap 'rm -rf "$TMP" 2>/dev/null || true' EXIT`。
   不改的话，在部分环境（本机 `rm` 拒绝带盘符路径）下，脚本明明校验通过却返回 1，看起来像失败。
5. **不建议在全量 B 上投入**，除非出现"目标环境绝对不允许改游戏 jar"的硬需求。
   真要做，按**包**为单位排期，别按类；先迁 `net.minecraftforge.event.*` 和 `net.minecraft.init`
   这类低风险的练手，最后动 `config` 和 `util`。
6. **`port-analysis/JDK8-可行性分析.md` 的 §4.1 需要更正**（universal jar 那条），
   否则照着做的人第一步就会做无效功。

---

## 8. 交付物与提交记录

| 路径 | 内容 |
|---|---|
| `test/signature/` | 最小复现工程（`build.sh` / `run.sh` / `Repro` / README） |
| `test/desig-jar.py` | 只改中央目录的删签名工具（跨平台，py3） |
| `test/desig-sandbox.ps1` | 沙箱去签名 / 查看 / 回滚，带安全护栏 |
| `test/launch-sandbox.py` | 不经启动器直接拉起实例，自动判定 READY/CRASH/TIMEOUT |
| `test/fixtures/probe-src/` | 护栏验收用的假冲突类 |
| `test/fixtures/p2-shim-slice.patch` | P2 切片 diff 备份 |
| `check-packages.sh` / `.ps1` | 包冲突护栏 |
| `backportapi/conf/known-conflicts.txt` | 19 个已登记类（按签名方分组） |
| `port-analysis/logs/` | 全部原始日志（21 份） |

```
abd57fa test: P0 离线复现同包签名冲突
a8a5d82 test: P1 沙箱去签名实测通过
8ddc2a5 test: P3 包冲突护栏
72c6d98 test: P2 包迁移切片实测（2 个类，仍不足）
```

分支未合并；`git log --oneline test/signature-conflict` 可复现上述顺序。
`E:\Weapon And Magic`（真实实例）全程只读，未做任何写操作。
