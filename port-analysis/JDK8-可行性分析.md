# 「JDK 17+ 运行 → JDK 8 可运行」可行性分析报告（v2 · 结合崩溃日志）

**对象**：`CarryOn-Unofficial` 1.0.3 + 前置 `BackportAPI` 1.0.0
**新增输入**：`C:\Users\36039\Desktop\崩溃报告.txt`（2026-11-06 13:43，实例 `E:\Weapon And Magic`）
**审计方式**：只读，未修改仓库与游戏文件。

---

## 0. 第二轮结论（先看这段）

| 问题 | 第一轮结论 | 第二轮（有日志后）|
|---|---|---|
| 模组能否在 JDK 8 上运行 | 静态审计：可以，产物是 Java 8 字节码 | **已实证**：日志第 5 行 `Java is ... version 1.8.0_251` —— 它本来就在 Java 8 上跑 |
| 「必须 JDK 17+」是否成立 | 代码/产物无证据，疑为环境侧误判 | **证伪**：运行环境就是 `jre1.8.0_251`（第 5、70、363 行）。换 JDK 治不了这次的崩溃 |
| 这次崩溃的原因 | — | **jar 签名 / 包归属冲突**：`SecurityException: class "net.minecraftforge.common.config.ConfigManager"'s signer information does not match...`（第 290–297 行） |
| 修复是否需要改代码 | 预估 0 处 | **仍然是 0 处**，但要动两个游戏 jar 的签名条目（§4 方案 A） |

一句话：**「JDK 17+」是个伪问题（日志直接否掉了），真问题是 BackportAPI 把类塞进了被 Mojang / Forge 签名的包里。**

---

## 1. 崩溃归因（证据链完整）

### 1.1 日志里的三段事实

```
L5    Java is Java HotSpot(TM) 64-Bit Server VM, version 1.8.0_251 ... at D:\...\jre1.8.0_251
L37-38 Found valid fingerprint for Minecraft Forge / Minecraft      ← 签名文件确实存在
L180  Fatal errors were detected during the transition from PREINITIALIZATION to INITIALIZATION
L200  Caught exception from carryonunoffical
        java.lang.NoClassDefFoundError: net/minecraftforge/common/config/ConfigManager
L246  Caught exception from backportapi  （同一个类，同一条链）
L290  Caused by: java.lang.SecurityException:
        class "net.minecraftforge.common.config.ConfigManager"'s signer information
        does not match signer information of other classes in the same package
```

### 1.2 机制：JVM 的"同包签名一致性"约束

`ClassLoader.preDefineClass → checkCerts` 要求**同一个包内的所有类必须有相同的签名信息**；
把一个**无签名**的类定义进一个**已有签名类**的包，会直接抛 `SecurityException`——就是第 290 行那句。
这与 JDK 版本、模组逻辑、混淆都没有关系，**在 Java 8 / 17 / 21 上表现完全一致**。

### 1.3 为什么偏偏是 `ConfigManager`

因为它是**第一个被加载**的冲突类（`BackportAPI.preInit` 第 63 行）。类加载是惰性的，
`CarryOn` 随后也要碰这个类，于是拿到了 LaunchClassLoader 的失败缓存（注意第 242 行 `LaunchClassLoader.java:101`
与第 286 行 `:191` 行号不同——前者是快速失败分支，后者是真正的定义路径），报的是同一个 `NoClassDefFoundError`。

**关键推论：修掉 `ConfigManager` 一个类没有意义，下一个必然是 `EnumHand`。** 见下表。

### 1.4 完整风险集：19 个类落在 7 个"已存在包"

用发布 jar 与游戏类清单做包名交集（`backportapi-1.0.0.jar` 共 21 个包，7 个与 1.7.10 重合）：

| 包（1.7.10 中已存在，且已被签名） | BackportAPI 塞进去的类 | 提供签名的一方 |
|---|---|---|
| `net.minecraftforge.common.config` | `Config`、`Config$Comment`、`Config$LangKey`、`Config$RangeInt`、`Config$RangeDouble`、`ConfigManager`（6） | Forge（`META-INF/FORGE.SF`） |
| `net.minecraft.util` | `EnumHand`、`EnumActionResult`、`SoundEvent`、`SoundCategory`、`IThreadListener`（5） | Mojang（`META-INF/MOJANGCS.SF`） |
| `net.minecraft.client.renderer` | `GlStateManager`（1） | Mojang |
| `net.minecraft.init` | `MobEffects`、`SoundEvents`（2） | Mojang |
| `net.minecraftforge.event` | `AttachCapabilitiesEvent`（1） | Forge |
| `net.minecraftforge.event.entity.player` | `PlayerContainerEvent`、`$Open`、`$Close`（3） | Forge |
| `net.minecraft.entity.player` | `EnumPlayerModelParts`（1） | Mojang |

而另外 14 个包（`net.minecraft.util.math`、`net.minecraft.block.state`、`net.minecraft.util.text`、
`net.minecraftforge.common.capabilities`、`net.minecraftforge.items`、`net.backportapi.*` …）
在 1.7.10 里**根本不存在**，所以 `BlockPos`、`IBlockState`、`ITextComponent`、`Capability` 这些主体功能是安全的。
**问题只集中在"必须沿用原包名的那 19 个类"上。**

### 1.5 已在本机核实（只读）

| 对象 | 路径 | 签名条目 |
|---|---|---|
| 客户端版本 jar（Mojang 签） | `versions\1.7.10-Forge_10.13.4.1614\1.7.10-Forge_10.13.4.1614.jar` | `META-INF/MOJANGCS.SF`、`MOJANGCS.RSA` |
| Forge universal（Forge 签） | `libraries\net\minecraftforge\forge\1.7.10-10.13.4.1614-1.7.10\...-universal.jar` | `META-INF/FORGE.SF`、`FORGE.DSA` |
| 纯原版（备用版本目录） | `versions\1.7.10\1.7.10.jar` | `MOJANGCS.SF`、`MOJANGCS.RSA` |

并且确认生产环境运行时类名就是 `net.minecraft.util.*` 形式
（`conf/mcp2srg.srg` 中 `CL: net/minecraft/util/EnumFacing net/minecraft/util/EnumFacing`，类名映射为恒等），
所以签名归属的"包"确实是 `net.minecraft.util`，冲突成立。

> 也就是说：BackportAPI README 里"类名放在 `net.minecraft.*` 下有风险吗"那一节，
> 只预判了**与其他回填库撞名**，漏掉了**与签名 jar 的包冲突**这一条——这是设计层面的盲点，不是实现 bug。

---

## 2. 第一轮的 JDK 审计结论仍然成立（作为背景）

崩溃与 JDK 无关，但"能不能在 JDK 8 上跑"这个原始问题，第一轮的四项证据继续有效：

| 检查 | 结果 |
|---|---|
| 3 个发布 jar 字节码 | 全部 `major=52`（Java 8） |
| JDK 8（Temurin 1.8.0_492）全量编译当前 HEAD（100 文件 / 8113 行） | exit 0 |
| JDK 8 `jdeps` 解析 `carryon-1.0.3.jar` | 921 行依赖，**0 处 `not found`** |
| Java 9+ 语法/API 扫描 | **0 命中** |
| 反射面 | 只碰自有类 / Forestry / Backhand API，不碰 `java.*` 内部，不需要 `--add-opens` |

现在再加上第五项**运行实证**：日志明确 `1.8.0_251`。**"必须 JDK 17+"这一条可以从问题清单里删掉。**

---

## 3. 为什么"换 JDK"这条路治不好

- 签名检查在 `ClassLoader.preDefineClass` 里，**硬编码**，没有任何 `-D` 开关可以关闭；
- JVM 参数里能关的是 FML 的指纹校验（`-Dfml.ignoreInvalidMinecraftCertificates`），
  那是 FML 自己比对证书指纹用的，**与 `checkCerts` 完全无关**，加上它照样崩；
- Java 17 上只会更糟：老 Forge + LWJGL2 + Mixin 还要额外 `--add-opens`，且同样的签名冲突依旧。

结论：**这是一个与 JVM 版本正交的打包问题，别在 JDK 上找原因。**

---

## 4. 修复方案评估（无需改代码的那条是首选）

| 方案 | 做法 | 改代码？ | 成本 | 风险 | 判定 |
|---|---|---|---|---|---|
| **A. 去签名（推荐）** | 删掉两个游戏 jar 里的 `META-INF/*.SF/*.RSA/*.DSA` | 否 | ~5 分钟/实例 | 该实例失去 jar 完整性签名；FML 指纹日志变成"未找到"；无功能影响 | **立即可行** |
| B. 包迁移 | 把 19 个类挪到 1.7.10 不存在的包（如 `net.backportapi.mc.util`），改调用方 import | 是（约 11 个文件，见 §4.2） | 0.5–1 人日 + 全量回归 | 违背 BackportAPI 的设计初衷（1.8 代码零改编译）；`EnumHand`/`GlStateManager`/`@Config` 恰恰是最常用的三件套 | 可行，但不划算 |
| C. 重新签名 BackportAPI | 用 Mojang / Forge 私钥签 | 否 | — | 拿不到私钥 | **不可行** |
| D. 只修 `ConfigManager` | 单独处理一个类 | 是 | 小 | 下一个 `EnumHand` 继续崩（§1.3） | **无效** |
| E. 把类塞进签名 jar 内 | 解包重打包 | 否 | 中 | 签名文件与内容不匹配 → 更严重的 `SecurityException`（摘要不符） | 不可行，除非同时执行 A |

### 4.1 方案 A 的具体动作（未执行，需你确认后再动）

1. 备份后删条目：
   - `versions\1.7.10-Forge_10.13.4.1614\1.7.10-Forge_10.13.4.1614.jar` → `META-INF/MOJANGCS.SF`、`META-INF/MOJANGCS.RSA`
   - `libraries\...\forge-1.7.10-10.13.4.1614-1.7.10-universal.jar` → `META-INF/FORGE.SF`、`META-INF/FORGE.DSA`
2. 启动后看日志：`signer information does not match` 应消失，`backportapi` / `carryonunoffical` 的状态从 `UCE` 变为 `UCH/UCIA`。
3. 注意：启动器"修复/重新下载"会把签名装回去，需重做；这是唯一需要长期维护的点。

> 副作用评估：签名只用于**来源校验**（单机/联机都不校验内容签名），删掉不影响游戏逻辑、不影响 Mixin、不影响 `verify.sh`。
> 这也是 1.7.10 时代众多整合包（GTNH 系）能塞补丁类的前提。

### 4.2 方案 B 的影响面（供对比，不推荐）

- 需改动的消费方文件：**11 个**（`carryon` 侧 7 个：`Configs`、`ConfigReload`、`CommonProxy`、`PositionCommonEvents`、`PositionClientEvents`、`GuiConfigCarryOn`（4 处 import）、以及 `backportapi` 侧 4 个：`BackportAPI`、`Hands`、`CapabilityHooks`、`ContainerEventHooks`、`SoundEvents`）。
- 真正的代价不在改动量，而在**定位**：迁走之后 `EnumHand`、`GlStateManager`、`@Config` 不再与 1.8 源码同名，
  "把 1.8 模组搬回 1.7.10 只需改少量调用点"这个卖点对最常用的三个 API 就失效了。

---

## 5. 日志里的第二个问题（不是崩溃主因，但要记一笔）

```
L89-111  Unable to read a class file correctly
         ... entry module-info.class in falsepattern\org.joml-joml-1.10.8.jar
L137     Zip file org.joml-joml-1.10.8.jar failed to read properly, it will be ignored
```

- 原因：FML 的 `asm-all 5.0.3` 读不懂 class 版本 53 的 `module-info.class`（Java 9 产物），
  这段代码是 GTNHLib 的 DepLoader 下载的 JOML。
- 影响：只是 FML 的"模组探测"环节跳过了这个 jar，**它仍然在类路径上**（DepLoader 负责挂载）。
  本次崩溃与它无关，但若日后出现 `NoClassDefFoundError: org/joml/...`，就从这里查。
- 处置：可忽略；想彻底消音就换新版 GTNHLib，或把 JOML 放到 FML 不扫描的目录。

---

## 6. 验收清单（修完按这个跑）

1. 启动日志无 `signer information does not match`；
2. `backportapi`、`carryonunoffical` 状态不为 `E`，能进主菜单；
3. 进世界：Shift+右键搬方块 → 放下，观察朝向（对照组：箱子/熔炉/林业容器）；
4. 搬生物、叠生物；
5. 看 `logs/latest.log` 里有没有 `post-rotate`、`warnOnce` 之类的降级告警；
6. 保持 Java 8 运行（`1.8.0_492` 比日志里的 `1.8.0_251` 更新，建议顺手升级，非必需）。

---

## 7. 最终判定

| 问题 | 回答 |
|---|---|
| 改成 JDK 8 可运行，技术可行吗？ | **可行，而且已经是事实**——日志第 5 行就是 Java 8 在跑。 |
| 需要改代码吗？ | **不需要。** 崩溃原因与代码无关。 |
| 那这次为什么崩？ | BackportAPI 的 19 个类落在被 Mojang/Forge 签名的 7 个包里，触发 JVM 的同包签名一致性检查（`SecurityException`）。 |
| 怎么修？ | 删掉两个游戏 jar 的签名条目（方案 A），0 行代码改动；方案 B（包迁移）技术上可行但会牺牲该库的核心价值。 |
| 「必须 17+」这条还成立吗？ | **不成立。** 这是把一次打包冲突误判成了 JDK 版本问题——两者正交，换成 17 只会多出 `--add-opens` 和 LWJGL2 的麻烦。 |
