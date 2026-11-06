# BackportAPI

把 Minecraft **1.8+ 的一批 API 回填到 1.7.10** 的兼容库，用于把 1.8/1.9 时代的模组移植回 1.7.10。

- **modid**: `backportapi`
- **版本**: 1.0.0
- **MC**: 1.7.10
- **硬前置**: 仅 Forge（10.13.4.1614 验证通过）
- **软前置**: [Backhand-GTNH](https://github.com/GTNewHorizons/Backhand)（提供副手，可选）

---

## 为什么需要它

把 1.8 模组移植回 1.7.10 时，绝大部分工作量不是"改逻辑"，而是"这些类/方法根本不存在"。典型阻断点：

| 缺失项 | 影响 |
|---|---|
| `BlockPos` | 几乎所有方块相关代码都要改签名 |
| `IBlockState` / `IProperty` | 方块状态读写全部重写 |
| `EnumHand` | 双持相关逻辑无处落脚 |
| `GlStateManager` | 渲染代码全废 |
| `Capability` | 数据存储方案要换掉 |
| `IItemHandler` | 容器交互要退回 `IInventory` |
| `@Config` | 配置代码要手写几十个 getter |
| `ITextComponent` | 提示文本要换 API |

本库按**原始包名**重建这些类型，使移植从"重写"退化为"改写少量调用点"。

---

## 覆盖清单

### `net.minecraft.util.math`

| 类 | 说明 |
|---|---|
| `BlockPos` | 完整实现：offset/up/down/north/south/east/west、distanceSq、toLong/fromLong、`MutableBlockPos` |
| `Vec3d` | 与 1.7.10 `Vec3` 互转（`fromVec3`/`toVec3`） |
| `MathHelper` | 静态委托到 `net.minecraft.util.MathHelper`，并补 `floor`/`ceil`/`clamp` |

### `net.minecraft.block.state` + `properties`

| 类 | 说明 |
|---|---|
| `IBlockState` | `getBlock`/`getMeta`/`getStateId`/`getPropertyNames`/`getValue`/`withProperty` |
| `BlockState` | 不可变 `(Block, meta)` 二元组，**按方块+meta 驻留**，可用 `==` 比较 |
| `IProperty` / `PropertyDirection` / `PropertyInteger` / `PropertyBool` | 属性系统 |

> **关键设计**：`getPropertyNames()` 会为"本库认识朝向的方块"**合成**一个 `PropertyDirection`。
> 这意味着 1.8 里那段"遍历属性、发现 `PropertyDirection` 就旋转方块"的代码可以原样运行。
> 朝向的实际换算由 `net.backportapi.state.BlockRotation` 负责（见下）。

### `net.minecraft.util`

`EnumHand`、`EnumActionResult`、`SoundEvent`、`SoundCategory`、`IThreadListener`

### `net.minecraft.util.text`

`ITextComponent`、`TextComponentString`、`TextComponentTranslation`、`TextFormatting`、`Style`、`event.ClickEvent`、`event.HoverEvent`

> **设计**：不另建渲染管线，而是**适配**到底层 `IChatComponent`。
> 每个实现都持有 vanilla 组件，用 `toChatComponent()` 交回原版 API。

### `net.minecraft.init`

`MobEffects`（指向 `Potion.xxx`）、`SoundEvents`（指向 1.7.10 的字符串音效名）

### `net.minecraft.client.renderer`

`GlStateManager` —— 直接转发到 `GL11`/`GL13`。不做状态缓存（性能略差，语义完全一致）。

### `net.backportapi.client` —— 1.7.10 版的 baked model

| 类 | 说明 |
|---|---|
| `RenderTarget` | "怎么画一个东西"的可替换抽象，对应 1.8 的 `IBakedModel` |
| `RenderTargets` | 工厂：`ofBlock` / `ofState` / `ofItem` |
| `HideHeldItem` | 让物品在手持时"什么都不画"，但**保留原版手臂** |

1.8 靠 `IBakedModel` 实现"把 A 渲染成 B"（模型覆盖、自定义渲染）。
1.7.10 没有这层抽象——方块只能走 `RenderBlocks.renderBlockAsItem`，
物品只能走 `ItemRenderer`，两者都硬编码在调用点。

`RenderTarget` 把三种形态统一成一个对象：

| 输入 | 绘制方式 |
|---|---|
| 方块 / 方块状态 | `RenderBlocks.renderBlockAsItem(block, meta, brightness)` |
| 物品且是 `ItemBlock` | 同上（方块物品的模型就是方块本身） |
| 扁平物品 | `ItemRenderer.renderItemIn2D` 画带厚度的精灵 |

```java
RenderTarget target = RenderTargets.ofState(state);
target.render(1.0F);          // 调用方负责矩阵、贴图、光照
IIcon icon = target.getIcon(); // 2D 图标，用于快捷栏
```

> `render()` **不**负责 push/pop 矩阵、绑贴图和设置 lightmap，
> 与 1.8 中 `RenderItem.renderItem(stack, model)` 的分工一致。

### 为什么 `HideHeldItem` 不能用 `RenderHandEvent` 代替

1.7.10 的 `ItemRenderer.renderItemInFirstPerson` 在**同一个方法**里依次做两件事：

1. 绑定玩家皮肤，画出手臂（无条件）；
2. 调用物品渲染器画手持物。

`RenderHandEvent` 在这个方法**开头**触发，取消它就是整个方法返回——
手臂和物品一起消失。所以"想保留手臂、只隐藏物品"必须注册一个
`IItemRenderer`，只在 `EQUIPPED*` 下返回 true 且 `renderItem` 留空。

```java
HideHeldItem.register(MyItems.carried);
```

### `net.backportapi.util` 补充

`NbtFilter.matches(candidate, filter)` —— NBT 子集匹配，
等价于 1.8 的 `NBTUtil.areNBTEquals`。filter 里的键必须全部存在且相等，
candidate 多余的键不影响结果。

### `net.minecraftforge.common.capabilities`

完整的能力对象模型：`Capability`、`Capability.IStorage`、`CapabilityManager`、`ICapabilityProvider`、`ICapabilitySerializable`、`CapabilityInject`、`CapabilityDispatcher`、`CapabilityProvider`

> **底层存储**：用 1.7.10 的 `IExtendedEntityProperties`（随实体 NBT 自动存读）。
> 挂载由 `CapabilityHooks` 在 `EntityConstructing` 时完成，重生复制由 `PlayerEvent.Clone` 处理。

### `net.minecraftforge.items`

`IItemHandler`、`ItemStackHandler`、`CapabilityItemHandler`、`InventoryWrapper`（把 `IInventory` 包成 `IItemHandler`）

### `net.minecraftforge.common.config`

`@Config` + `@Config.Comment` / `@Config.RangeInt` / `@Config.RangeDouble` / `@Config.LangKey`，配合 `ConfigManager.sync(modid, Configs.class)`

### `net.minecraftforge.event`

`AttachCapabilitiesEvent`、`PlayerContainerEvent`（Open/Close 由 tick 对比 `openContainer` 合成）

---

## 副手：Backhand 集成

1.7.10 没有副手。装了 Backhand 时，`net.backportapi.hand.Hands` 会**通过反射**自动接上：

```java
ItemStack main = Hands.getHeldItem(player, EnumHand.MAIN_HAND);
ItemStack off  = Hands.getHeldItem(player, EnumHand.OFF_HAND);   // Backhand 缺席时恒为 null
Hands.setHeldItem(player, EnumHand.OFF_HAND, stack);

boolean both = Hands.isBothHandsEmpty(player);  // 无 Backhand 时退化为"主手为空"
Hands.hasOffhand();                              // 当前环境是否真的有副手
```

**用反射而不是编译依赖**，所以 Backhand 不是硬前置，也不会因为它的版本变动而链接失败。

对接的 Backhand API（1.8.17 实测）：

```
xonin.backhand.api.core.BackhandUtils.getOffhandItem(EntityPlayer)
xonin.backhand.api.core.BackhandUtils.setPlayerOffhandItem(EntityPlayer, ItemStack)
```

---

## 移植对照速查

| 1.8 写法 | 1.7.10 + 本库写法 |
|---|---|
| `world.getBlockState(pos)` | `Worlds.getBlockState(world, pos)` |
| `world.setBlockState(pos, state)` | `Worlds.setBlockState(world, pos, state)` |
| `world.setBlockToAir(pos)` | `Worlds.setBlockToAir(world, pos)` |
| `world.removeTileEntity(pos)` | `Worlds.removeTileEntity(world, pos)` |
| `block.isReplaceable(world, pos)` | `Worlds.isReplaceable(world, pos)` |
| `block.canPlaceBlockAt(world, pos)` | `Worlds.canPlaceBlockAt(block, world, pos)` |
| `world.canBlockBePlaced(...)` | `Worlds.canBlockBePlaced(...)` |
| `world.isBlockLoaded(pos)` | `Worlds.isBlockLoaded(world, pos)` |
| `world.getCombinedLight(pos, 0)` | `Worlds.getCombinedLight(world, pos, 0)` |
| `EnumFacing.VALUES` | `Facing.VALUES` |
| `EnumFacing.HORIZONTALS` | `Facing.HORIZONTALS` |
| `facing.getOpposite()` | `Facing.getOpposite(facing)` |
| `EnumFacing.getFacingFromVector(x,y,z)` | `Facing.getFacingFromVector(x,y,z)` |
| `player.getHeldItemMainhand()` | `Hands.getMainhand(player)` |
| `player.setHeldItem(hand, stack)` | `Hands.setHeldItem(player, hand, stack)` |
| `player.addChatMessage(component)` | `Chat.send(player, component)` |
| `player.getCapability(CAP, null)` | `Capabilities.get(player, CAP)` |
| `te.hasCapability(CAP, side)` | `Capabilities.tileDispatcher(te).hasCapability(CAP, side)` |
| `@CapabilityInject` 自动注入 | preInit 里 `Capabilities.inject(YourClass.class)` |
| `TileEntity.getUpdateTag()` | `BackportAPI` 不提供（见"已知限制"） |

---

## 与 1.8 的刻意差异

移植时这几处**不能照抄 1.8**：

1. **`IProperty<T>` 没有 `Comparable` 约束。** 1.7.10 的 `EnumFacing` 继承的是**裸** `Enum`，并未实现 `Comparable<EnumFacing>`，所以无法沿用 1.8 的 `<T extends Comparable<T>>`。
2. **`PotionEffect` 第一个参数是 `int`。** 写 `new PotionEffect(MobEffects.SLOWNESS.id, 20, 1)`，不是传 `Potion` 对象。
3. **`Block.isReplaceable` 不存在。** 1.7.10 的可替换判定在材质层（`Material.isReplaceable()`）或 `Block.canReplace(...)`。
4. **`ModelRenderer` 用 `showModel`（true=显示）**，1.8 是 `isHidden`（true=隐藏），**语义相反**。
5. **没有 `IBakedModel` / `ModelPlayer`。** 方块渲染走 `RenderBlocks.renderBlockAsItem`，
   需要"可替换模型"时用本库的 `RenderTarget`；玩家模型用 `ModelBiped`。
6. **朝向在 meta 里的位宽各家不同，掩码必须够宽。** `BlockRotation.FacingMeta` 的掩码决定
   写 meta 时保留几位。箱子和熔炉的 meta 是 **2~5**（不是 0~3），掩码必须是 `7`；
   写成 `3` 时 `4 & 3 = 0`、`5 & 3 = 1`，写进去的是**非法 meta**，
   `TileEntityChestRenderer` 会落进 default 分支（旋转 0°），表现就是"放下后永远朝南"。
   现在 `put()` 会按登记值**自动加宽掩码**，这类错误不会再静默生效。

---

## 已知限制

| 限制 | 原因 | 影响 |
|---|---|---|
| 方块状态只有 4 bit | 1.7.10 的 meta 上限 | 状态数 >16 的方块无法完整往返 |
| `getActualState` 返回自身 | 1.7.10 的 meta 即实际状态 | 栅栏连接等"计算状态"需要放置后触发邻居更新 |
| `IBlockState.getPropertyNames()` 只对认识朝向的方块非空 | 没有真正的属性存储 | 通用属性遍历逻辑只能覆盖注册过规则的方块 |
| `TileEntity` 能力不自动存读 | 1.7.10 无 TE 创建事件 | 需手动 `Capabilities.saveTileCaps/loadTileCaps` |
| `@CapabilityInject` 需手动 `inject()` | 无 ASM 注入 | 一行样板代码 |
| `TextFormatting.CHANGE_PAGE` / `HoverEvent.SHOW_ENTITY` 无效 | 1.7.10 没有对应 action | 映射为 `null`，需容错 |
| `GlStateManager` 不缓存状态 | 简化实现 | 渲染略慢，语义一致 |

---

## 运行时陷阱（1.7.10 特有）

### `@SubscribeEvent` 所在类**必须**是 public 顶层类

FML 的 `EventBus` 会为每个 `@SubscribeEvent` 方法动态生成一个
`ASMEventHandler_*` 类，并放进独立的 `ASMClassLoader`。
这个生成类回调目标方法时，与目标类**不在同一个运行时包**，
因此 JVM 会做访问检查——目标类不是 `public` 就直接抛：

```
java.lang.IllegalAccessError: failed to access class net.backportapi.util.Scheduling$1
  from class cpw.mods.fml.common.eventhandler.ASMEventHandler_551__onClientTick_ClientTickEvent
```

**踩过的坑**：1.0.0 版把 tick 处理器写成了匿名内部类

```java
// ✗ 崩溃：匿名类默认包私有
FMLCommonHandler.instance().bus().register(new Object() {
    @SubscribeEvent public void onClientTick(TickEvent.ClientTickEvent e) { ... }
});
```

**正确写法**：独立的 public 顶层类

```java
// ✓
public class SchedulingTicker {
    @SubscribeEvent public void onClientTick(TickEvent.ClientTickEvent e) { ... }
}
FMLCommonHandler.instance().bus().register(new SchedulingTicker());
```

> 这条规则对**所有**事件总线（`MinecraftForge.EVENT_BUS` 和
> `FMLCommonHandler.instance().bus()`）都成立。
> 私有静态内部类、匿名类、lambda 全部不行。

### 其他容易踩的点

| 现象 | 原因 |
|---|---|
| 两个同 modid 的 jar → 启动即报重复模组 | 升级时务必删掉旧 jar，不能并存 |
| 事件处理器注册了但从不触发 | 挂错总线：`TickEvent` / `InputEvent` / `PlayerLoggedInEvent` 在 **FML 总线**；`EntityConstructing` / `PlayerInteractEvent` / `BlockEvent` 在 **Forge 总线** |

---

## 构建

工程已内置一条**不依赖 ForgeGradle** 的构建链（javac → SpecialSource 重混淆 → 打包），
适用于老 ForgeGradle 下载源已失效的环境。

```bash
# Git Bash / WSL
export JAVA_HOME="C:/Program Files/Eclipse Adoptium/jdk-8.0.492.9-hotspot"
./build.sh 1.0.0
# 产物: build/backportapi-1.0.0.jar
```

```powershell
# PowerShell（路径含空格时用 build.sh 更稳）
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-8.0.492.9-hotspot"
.\build.ps1 -Version 1.0.0
```

> **构建末尾请跑一次包冲突护栏**：`../check-packages.sh`（已挂在 `CarryOn-Unofficial/verify.sh` 末尾，非阻断）。
> 它把产物与 `deps/forgebin.jar` + `deps/mcp.jar` 的包名求交集，只要有类又落进 1.7.10 **已签名**的包
> （如 `net.minecraft.util`、`net.minecraftforge.common.config`），就会 `FAIL` 并点名。
> 已知的 19 个类登记在 `conf/known-conflicts.txt`；新增的必须换包名，不能直接加进白名单蒙混过关。

### 目录结构

```
backportapi/
├── deps/                 编译与重混淆依赖
│   ├── forgebin.jar      反混淆的 MC+Forge（唯一保留 InnerClasses 的产物，必须排第一）
│   ├── mcp.jar           MC 类（补充）
│   ├── forge-mcp.jar     Forge 类（补充）
│   ├── lwjgl-2.9.3.jar
│   ├── netty-all-4.0.23.Final.jar
│   ├── guava-17.0.jar
│   ├── authlib-1.5.21.jar
│   └── SpecialSource-1.11.4-shaded.jar
├── conf/
│   ├── mcp2srg.srg       重混淆映射
│   └── inheritance.map   继承关系传播表
├── src/main/java/        53 个源文件
├── src/main/resources/mcmod.info
├── build.sh              已验证的构建脚本
└── build.ps1
```

### 构建时的两个坑（已在脚本里规避）

1. **`forgebin.jar` 必须排在类路径第一位。** 由 SpecialSource 重映射出来的 `mcp.jar` / `forge-mcp.jar` **丢失了 `InnerClasses` 属性**，导致 javac 无法解析 `ClickEvent.Action`、`PlayerInteractEvent.RightClickBlock` 这类嵌套类型。`forgebin.jar` 是 ForgeGradle 的原生产物，保留了该属性。
2. **工程路径含空格时，不能把 `;` 分隔的类路径直接放在命令行上。** Git Bash 的 MSYS 路径转换会把含空格的路径拆断。`build.sh` 因此改为用 javac 参数文件（`@argfile`）+ 相对路径调用。

---

## 类名放在 `net.minecraft.*` 下有风险吗

有，而且不止一种：

- 这些类在 1.7.10 里**不存在**，不会与游戏本体撞类名；
- FML 的 `FMLDeobfuscatingRemapper` 只重映射映射表里有的类，本库的类不在表中，会原样通过；
- 真正的风险是**与其他回填库撞名**。如果你同时装了别的 1.8-API 回填模组（如某些 GTNH 组件），需要确认它们没有定义同名类。
- **更隐蔽的一条：与签名 jar 的「包」冲突。** 1.7.10 的游戏 jar 是签过名的
  （Mojang 的 `META-INF/MOJANGCS.SF`、Forge 的 `META-INF/FORGE.SF`），
  JVM 要求同一个包里所有类的签名信息一致。把 19 个无签名的类塞进 7 个已签名的包，
  就会在 `preInit` 抛 `SecurityException: class "..."'s signer information does not match`。
  这条与 JDK 版本无关，也不是撞名问题，**只能靠去签名或换包名解决**；
  完整取证与实测见 `port-analysis/TEST-REPORT-signature-conflict.md`，
  日常构建用 `../check-packages.sh` 兜底。

---

## 下一步

本库只提供"前提条件"。模组本体的移植在下一阶段进行，
届时按本库的对照表把调用点改写即可。
