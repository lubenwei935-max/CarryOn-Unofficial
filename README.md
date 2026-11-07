# CarryOn-Unofficial

**Carry On（搬运）模组的 Minecraft 1.7.10 非官方移植**，以及它依赖的前置库 **BackportAPI**。

上游是 [Tschipp/CarryOn](https://github.com/Tschipp/CarryOn) 的 1.10 分支 v1.9.1（LGPL-3.0）。
本仓库把整套逻辑搬回 1.7.10，并额外沉淀出一套「把 1.8+ API 回填到 1.7.10」的兼容库——
移植一个 1.8 模组时，绝大部分工作量不是改逻辑，而是这些类根本不存在。

| 模块 | 说明 | 产物 |
|---|---|---|
| `backportapi/` | 1.8+ API 回填库：`BlockPos` / `IBlockState` / `EnumHand` / `GlStateManager` / `Capability` / `@Config` / `RenderTarget` … | `backportapi-1.1.0.jar` |
| `CarryOn-Unofficial/` | Carry On 本体（搬运方块与生物） | `CarryOn-Unoffcial-1.0.2.jar` |
| `port-analysis/` | 移植前的可行性分析报告 | — |

- **MC**：1.7.10
- **前置**：Forge 10.13.4.1614 + BackportAPI（Backhand 提供副手，可选）
- **许可证**：LGPL-3.0（衍生自上游 Carry On）
- **版本变化**：见 [CHANGELOG.md](CHANGELOG.md)

---

## 玩法

空手（主手）**潜行 + 右键** 搬起方块或生物，松开潜行后再右键放下。

- 搬运中无法攻击、挖掘、丢弃、切换物品栏、打开背包、打开容器
- 搬运物越重（NBT 越大 / 体型越大）移动越慢
- 生物可以叠在别的生物背上
- 放下的方块**正面朝着玩家**（与原版手动放置一致）

---

## 目录结构

```
backportapi/
  src/main/java/net/backportapi/     回填库本体
  src/main/java/net/minecraft/…      按原包名重建的 1.8 类型
  build.sh  build.ps1                构建脚本（无 ForgeGradle）
  conf/                              重混淆用的 MCP 映射表
CarryOn-Unofficial/
  src/main/java/tschipp/carryon/     Carry On 本体
  src/main/resources/                语言文件 / 贴图 / mcmod.info
  build.sh                           构建脚本
  verify.sh                          重混淆校验（见下）
  conf/                              重混淆用的 MCP 映射表
```

---

## 构建

需要 **JDK 8**。两个工程都用同一套「不依赖 ForgeGradle」的流水线：

```
javac（MCP 名）→ jar → SpecialSource 重混淆（mcp2srg）→ 打包
```

```bash
export JAVA_HOME="C:/Program Files/Eclipse Adoptium/jdk-8.0.492.9-hotspot"

cd backportapi  && ./build.sh 1.1.0     # 产物 build/backportapi-1.1.0.jar
cd ../CarryOn-Unofficial
cp ../backportapi/build/backportapi-1.1.0.jar deps/   # 模组编译期要引用前置
./build.sh 1.0.2                        # 产物 build/carryon-1.0.2.jar
./verify.sh build/carryon-1.0.2.jar     # 必跑，见下
```

### 准备依赖

`deps/` 不入库（约 32MB，且是 Mojang / Forge 的再分发受限产物），需要自己放进去。
两个工程各有一个 `deps/`，内容相同；`CarryOn-Unofficial/deps/` 额外需要上一节构建出的 `backportapi-1.1.0.jar`。

| 文件 | 来源 |
|---|---|
| `forgebin.jar` | ForgeGradle 缓存 `~/.gradle/caches/minecraft/net/minecraftforge/forge/1.7.10-10.13.4.1614-1.7.10/forgeBin-*.jar` |
| `mcp.jar`、`forge-mcp.jar` | 同一缓存目录下的 MCP 产物 |
| `lwjgl-2.9.3.jar`、`netty-all-4.0.23.Final.jar`、`guava-17.0.jar`、`authlib-1.5.21.jar` | MC 1.7.10 的 `libraries/` |
| `SpecialSource-1.11.4-shaded.jar` | Forge maven（`net.md-5:SpecialSource`） |

`forgebin.jar` 必须排在类路径**第一位**：它是唯一还带 `InnerClasses` 属性的产物，
javac 靠它解析 `ClickEvent.Action`、`PlayerInteractEvent.RightClickBlock` 这类嵌套类型。

### 关于 `conf/`

`conf/mcp2srg.srg`（2.6MB）是 MCP 9.05 的 MCP→SRG 映射表，`conf/inheritance.map` 是配套的继承表，
重混淆时喂给 SpecialSource。它们随仓库提供，是为了让 clone 下来就能构建。

---

## `verify.sh`：这套手工构建链的护栏

编译用的 `forgebin.jar` 和重混淆用的 `mcp2srg.srg` 来自**两套不同的映射**，同一个方法两边可能叫不同名字。
SpecialSource 在 srg 里查不到映射时会**静默跳过**——编译通过、构建通过、进游戏才 `NoSuchMethodError`。

`verify.sh` 在构建后静态扫描整个 jar，列出所有未被改写的原版方法/字段引用。
期望输出只有 3 个 Forge 附加方法（`Entity.getEntityData`、`Entity.isCreatureType`、
`RenderBlocks.getInstance`）和几个 `Object` 方法。**每次改完代码都要跑**，编译器抓不到这类问题。

历史上它抓到过两次：`ItemStack.getItemDamage`（GTNH 命名）和 `Block.hasTileEntity(int)`（srg 只有无参版本）。

---

## 已知差异与限制

- 方块状态只有 4 bit meta，状态数 > 16 的方块无法完整往返
- 模组容器若没实现 `onBlockPlacedBy`（例如 IronChest 只提供 Forge 的 `rotateBlock`），
  放下后保持搬起时的朝向
- 脚本系统（`config/carryon-scripts/*.json`）保留了拾取规则，但没有上游那套按脚本定制渲染的功能

细节都写在两个子工程的 `README.md` 里，包括渲染矩阵、朝向 meta 位宽、`onBlockAdded` 覆盖朝向等踩坑记录。

---

## 致谢

- [Tschipp](https://github.com/Tschipp/CarryOn) —— 上游 Carry On，本项目的全部玩法与大部分逻辑来自它
- [Backhand-GTNH](https://github.com/GTNewHorizons/Backhand) —— 1.7.10 的副手实现，BackportAPI 通过反射软接入
