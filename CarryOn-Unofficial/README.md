# CarryOn-Unofficial — 1.7.10 移植版

上游：Tschipp / CarryOn，1.10 分支 v1.9.1
移植：基于 **BackportAPI**（提供 BlockPos / IBlockState / EnumHand / GlStateManager / Capability / @Config）

- **modid**: `carryonunoffical`
- **版本**: 1.0.1
- **MC**: 1.7.10
- **硬前置**: Forge 1.7.10 + **BackportAPI**
- **软前置**: Backhand（提供副手，可选）

> **modid 与早期版本不同**：以前用的是 `carryon`（与上游同名），现在改为 `carryonunoffical`。
> 连带变化：配置文件 `config/carryonunoffical.cfg`、资源域 `assets/carryonunoffical/`、
> 能力键 `carryonunoffical:position`。
> 首次启动会把旧的 `carryon.cfg` **复制**过去（旧文件保留，方便回退），参数不会丢。
> 命令名仍是 `/carryon`，脚本目录仍是 `config/carryon-scripts/`，这两处没有跟着改。
>
> **升级时务必删掉旧 jar**。FML 是按每个 jar 自己的 `@Mod` 字节码建 mod 容器的，
> 两个 jar 就会有两个容器（`carryon` 与 `carryonunoffical`）；但 1.7.10 所有模组共用
> 同一个 LaunchClassLoader，两边跑的是**同一份类**。于是 `init` 会走两遍，
> 第二次注册直接撞车：
>
> ```
> java.lang.IllegalStateException: Capability already registered:
>     tschipp.carryon.common.capabilities.IPosition
> 	at net.minecraftforge.common.capabilities.CapabilityManager.register(...)
> 	at tschipp.carryon.common.handler.RegistrationHandler.regCaps(...)
> ```
>
> 更麻烦的是类路径里哪个 jar 先被加载并不固定：本次实测是**旧 jar 的类**赢了，
> 所以新 jar 放进 mods 也不会生效。现在 `CarryOn` 的生命周期带一次性守卫
> （后到的容器只打一行 ERROR 就跳过），`regCaps` 也做成幂等，重复装载不再崩溃；
> 但正确做法仍是 `mods/` 里只留一个 jar。

---

## 玩法

空手（主手）**潜行 + 右键** 搬起方块或生物，松开潜行后再右键放下。

- 搬运中无法攻击、挖掘、丢弃、切换物品栏、打开背包、打开容器
- 搬运物越重（NBT 越大 / 体型越大）移动越慢
- 生物可以叠在别的生物背上

---

## 放下时的朝向

放下的方块默认**正面朝着玩家**（与原版手动放置一致）。
这里踩了两个坑，第一个是主因。

### 主因：`onBlockAdded` 会把算好的朝向冲掉

1.7.10 的放置是**两步契约**（`ItemBlock.placeBlockAt` 就是这两步）：

```java
world.setBlock(x, y, z, block, meta, 3);   // ① 放进世界
block.onBlockPlacedBy(world, x, y, z, player, stack);   // ② 让方块自己定朝向
```

关键在于 ① 会经 `Chunk.func_150807_a` 回调 **`Block.onBlockAdded`**，而
`BlockChest.onBlockAdded` 会调 `func_149954_e`，按"背靠哪个实心块"**重新算一遍 meta**：

```java
int i1 = 3;                                          // 默认朝南
if (北实心 && !南实心) i1 = 3;
if (南实心 && !北实心) i1 = 2;
if (西实心 && !东实心) i1 = 5;
if (东实心 && !西实心) i1 = 4;
world.setBlockMetadataWithNotify(x, y, z, i1, 3);    // 无条件写
```

空地上四个方向都没有实心邻居 → 四个 `if` 全不成立 → **恒为 3**。
而 meta 3 就是"正面朝南"。所以不管玩家朝哪，箱子放下后都朝南。

移植版原来只做了 ①，②被省掉了 → 算好的朝向被 `onBlockAdded` 覆盖，这就是
"正面朝向默认为朝南"的真正原因。

修法：① 之后补上 ②。但不是无条件补——**楼梯的 `onBlockPlacedBy` 会整段重写 meta**，
把"倒置"那一位（bit2）丢掉，所以只在下面两种情况下调用：

- `BlockRotation` 不认识这个方块（模组容器，没有别的办法）；
- 属性路径算出的 meta 确实被 `onBlockAdded` 冲掉了（`clobbered`）。

若方块自己也不管朝向（`onBlockPlacedBy` 是空实现），再用
`setBlockMetadataWithNotify` 把 meta 写回去——**不能再用 `setBlock`**，
那会二次触发 `onBlockAdded`，又冲掉一次。

### 次因：meta 位宽不够（掩码 3 → 7）

1.8+ 用 `IProperty<EnumFacing>` 表达朝向，与 meta 无关；1.7.10 只能往 4 bit meta
的某几个位段里写。箱子/熔炉的编码是 **2/3/4/5 = 北/南/西/东**，需要 **3 位**。

`BlockRotation.CHEST` 原来写的是 `FacingMeta(3)`（只保留 2 位），
`rotateTo()` 里的 `value & mask` 把西/东截掉了：

| 玩家朝向 | 目标正面 | 写入 meta | 渲染器角度 | 实际正面 |
|---|---|---|---|---|
| 南 | 北 | 2 | 180° | 北 ✅ |
| 北 | 南 | 3 | 0° | 南 ✅ |
| 西 | 东 | **1**（5 被截） | **default 0°** | **南 ❌** |
| 东 | 西 | **0**（4 被截） | **default 0°** | **南 ❌** |

`TileEntityChestRenderer` 的 switch 只有 2/3/4/5 四个分支，0/1 落进 default（旋转 0°），
而 0° 对应的锁扣在 **+Z = 南**。即使修好主因，这个坑也会让东西方向继续朝南。

修法：掩码改成 **7**；同时让 `FacingMeta.put()` 按登记值**自动加宽掩码**，
以后再有"常量写小了"的情况也不会静默生效。

### 模组容器

`BlockRotation` 只认识原版那几类方块。模组容器（例如 IronChest 的 `BlockIronChest`，
它继承的是 `BlockContainer` 而不是 `BlockChest`）拿不到朝向编解码器，
于是走 ②：`onBlockPlacedBy(..., placer, ...)`，让方块按放置者朝向决定 meta——
原版末影箱、铁砧、发射器都是这么写的。（漏斗、信标的同名方法只处理自定义名称，调用无副作用。）

模组如果压根没实现 `onBlockPlacedBy`（IronChest 就没实现，它只提供 Forge 的
`rotateBlock` 扳手接口），放下后仍会保持搬起时的朝向。

兜底的触发条件是"放下后那个位置有 TileEntity"，所以铁砧/门/床这类没有 TE 的方块不会被误伤
（铁砧的 `onBlockPlacedBy` 会把 meta 整段重写成 0~3，顺手清掉 bit2 的"损坏"标记）。

### 诊断

每次放下方块会打一行日志，`meta` 就是最终写进世界的值：

```
[CarryOn] place-block minecraft:chest pos=12,64,-3 meta=4 look=EAST yaw=270.0 prop=true clobbered=true
```

- `look`：玩家视线方向；`yaw`：玩家朝向角
- `prop`：是否命中 `BlockRotation` 的属性路径（有朝向规则）
- `clobbered`：算好的 meta 是否被 `onBlockAdded` 冲掉（true 表示走了 ② 补救）

箱子朝东（yaw 270）放下时应当是 `meta=4`（正面朝西 = 朝玩家）。

---

## 移植清单

| 模块 | 状态 | 说明 |
|---|---|---|
| 方块搬运（含 TileEntity NBT） | ✅ 完整 | 状态用 `Block + meta` 存，TE 走 `writeToNBT` |
| 生物搬运 | ✅ 完整 | `EntityList.createEntityByName` 重建 |
| 朝向自动旋转 | ✅ 可用 | 走 `BackportAPI` 的 `BlockRotation`（meta 位段表） |
| 潜行触发搬起 | ✅ 完整 | 服务端 `player.isSneaking()`，无自定义按键 |
| 配置（21 项 + 黑白名单） | ✅ 完整 | `@Config` + `Configuration` |
| 配置 GUI | ✅ 完整 | `GuiConfig` + 三个分类 |
| 命令 `/carryon clear|debug`、`/reloadscripts` | ✅ 完整 | 1.7.10 的 `processCommand` |
| 脚本系统（JSON 拾取规则） | ✅ 完整 | Gson + NBT，逻辑基本原样 |
| 生物堆叠 | ✅ 完整 | `riddenByEntity` / `mountEntity` |
| 第一/第三人称渲染 | ⚠️ 重写 | 见下 |
| 模型覆盖（ModelOverrides） | ✅ 已实现 | 用 BackportAPI 的 `RenderTarget` 替代 baked model |

---

## 渲染：为什么是重写而不是翻译

1.10 的渲染依赖 `GlStateManager` + `IBakedModel` + `ModelPlayer` + 手臂假模型，
这套在 1.7.10 完全不存在。移植后改成两条路径：

| 场景 | 实现 |
|---|---|
| 第一人称 | `RenderHandEvent` 里 `glLoadIdentity()` 后按**相机空间**画；物品本体由 `HideHeldItem` 隐藏，事件末尾 `setCanceled(true)` 把原版手臂一并跳过 |
| 第三人称 | `RenderPlayerEvent.Post` 里，用 `玩家插值坐标 - RenderManager.renderPos` 平移（再减 `yOffset` 落到脚底）——和原版 `renderLivingAt` 同一组数 |
| 方块本体 | `RenderBlocks.renderBlockAsItem(block, meta, 1.0F)` |
| 生物本体 | `RenderManager.getEntityRenderObject(entity).doRender(...)` |
| 手臂 | 仅第三人称：`RenderPlayerEvent.Pre/Post` 里切换 `ModelRenderer.showModel` |

### 三次踩坑的教训：不要自己猜相机

1. **`RenderWorldLastEvent` 的矩阵不可信**：在 Angelica / SDL-GPU 下实测几乎等于
   单位矩阵（没有相机旋转、也没有平移），按"世界坐标"画出来的东西会被当成相机空间
   坐标，永远固定在屏幕中央上方。
2. **矩阵原点是眼睛，不是脚底**：`EntityPlayer.yOffset = 1.62`，
   `RendererLivingEntity` 渲染模型时先 `translate(0, -yOffset, 0)`，
   所以"玩家位置 - 相机位置"这组数在矩阵里落到的是**眼睛高度**，少减 1.62 就整体偏高。
3. **第三人称相机还被后拉了 4 格**（`orientCamera` 的 `glTranslatef(0,0,-d7)`），
   自己重建视图矩阵必须同时猜对这三件事，错一个就"变大/偏移"。

**最终做法**：不再自己算。`RenderPlayerEvent.Post` 就在原版画完玩家模型之后触发，
矩阵已经 `pop` 回原状，而 `RenderManager.renderPos` 就是那一帧相机用的基准，
所以"玩家坐标 - renderPos - yOffset"必然和玩家模型对齐，与相机在哪、朝哪无关。

### 第一人称：先平移再缩放

OpenGL 右乘，`glScalef(s); glTranslatef(t)` 的实际偏移是 `s*t`。
把"世界单位"的平移量写在 scale 之后会被放大 `s` 倍（1.0.8 那版箱子又远又小就是这个）。
现在统一 `glTranslatef → glScalef`。

### 第一人称不画手臂

搬运方块时会看到右下角露出一条手臂——抱的是大箱子时被模型挡住，抱水晶箱、节点稳定器这类
体积小的东西就露出来了。这份手臂不是模组自己画的（1.0.1 里画过一份，已经删掉），而是
**原版照常绘制的右手**。

1.7.10 的调用链是这样：

```java
// EntityRenderer.renderWorld
if (!ForgeHooksClient.renderFirstPersonHand(renderGlobal, partialTicks, pass)
        && this.cameraZoom == 1.0D)
{
    glClear(GL_DEPTH_BUFFER_BIT);
    renderHand(partialTicks, pass);          // → ItemRenderer.renderItemInFirstPerson
}
```

```java
// ItemRenderer.renderItemInFirstPerson（简化）
... bindTexture(player.getLocationSkin());
this.renderPlayerArm(player);                // ← 无条件，就是这条手臂
... 再按 itemToRender 分派给物品渲染器     // ← HideHeldItem 只把这一段变成空操作
```

也就是说 **手臂和手持物是两段独立的绘制**，`HideHeldItem`（注册一个空的 `IItemRenderer`）
只能让手持物不画，管不到手臂。1.7.10 也没有 `RenderHandEvent` 之外的开关，`showModel`
只在第三人称的 `RenderPlayerEvent` 路径上有效。

所以做法是：在 `RenderHandEvent` 里画完搬运物后 `event.setCanceled(true)`。
`renderFirstPersonHand()` 只是 `EVENT_BUS.post(new RenderHandEvent(...))` 的返回值，
取消后上面那个 `if` 直接跳过整段 `renderHand()`——手臂、手持物（本来已被隐藏）、
FOV 切换、视角晃动全部不再执行，第一人称完全由本模组接管。

两个必须自己补齐的副作用：

- **深度清理**：原版那次 `glClear(GL_DEPTH_BUFFER_BIT)` 就在被跳过的分支里，
  事件里必须自己清一次，否则贴墙站时手里的东西会被墙挡住。
- **投影**：原来由 `renderHand()` 换成固定 70°，现在得自己 `glLoadIdentity()` +
  手算透视矩阵（`applyPerspective`），否则玩家把 FOV 拉到 110° 时搬运物会小一半。

### 数值对齐上游 1.12.2

| | 上游写法 | 本移植（先缩放后平移，单位=世界格） |
|---|---|---|
| 第一人称 | `scale(2.5)` → `translate(0,-0.6,-1)` | `translate(0,-1.5,-2.5)` → `scale(2.5)` |
| 第三人称 | `scale(0.6)` → `translate(0,1.6,±0.65)` | `translate(0,0.70,±0.40)` → `scale(0.6)` |
| 生物第一人称 | `scale(0.8)` → `translate(0,-h-0.1,w+0.1)` | 同上游（顺序保留） |
| 生物第三人称 | `scale((10-h*w)*0.08)` → `translate(0,1,max(w-0.1,0.7))` | 同上游（顺序保留） |

### 两个 1.7.10 特有的模型差异

1. **`renderBlockAsItem` 的原点不统一**：渲染类型 0 内部有 `translate(-0.5,-0.5,-0.5)`，
   几何中心在原点；渲染类型 22（箱子/末影箱）走 TESR，模型 y 只有 0~0.875，
   **原点在底部中心**，而且它加的平移不会还原。`BackportAPI` 的
   `RenderTargets.BlockTarget` 因此补了 `translate(0,-0.4375,0)` 并包 `push/pop`。
2. **箱子模型的正面朝 +X**（1.8+ 的方块模型朝 -Z）。上游那句 `rotate(180)` 是按 -Z 写的，
   这里要换成 `rotate(-90)`，锁扣才会朝外。

---

## 性能：热路径改了什么

模组每 tick / 每帧都会被调用的地方只有几处，但原来的实现里有三个真实的热点。

### 1. 每 tick 序列化整个 TileEntity NBT（最大的一处）

`ItemTile.onUpdate` 要按"方块有多重"决定减速等级，原来的算法是：

```java
String nbt = getTileData(stack).toString();   // ← 每 tick 把整个 TE NBT 拼成字符串
int i = nbt.length() / 500;
```

一个装满东西的箱子，NBT 序列化出来是上千字符，而且这个字符串**每 tick 都重新生成一次**
（`Item.onUpdate` 由 `ItemStack.updateAnimation` 驱动，每 tick 对每个背包槽都调一次；
只有搬运中的那一格有 tileData，所以实际每 tick 序列化一次 = 每秒 20 次）。
搬运时那种说不清的顿挫感主要来自这里。

**改法**：拾取时算一次，把结果存进物品 NBT（`carryWeight`），`onUpdate` 只读一个 int。
旧存档里搬着的物品没有这个键，退回 0（正好是小容器原本的等级）。

### 2. 每 tick 重建一只实体

`ItemEntity.onUpdate` 的 `potionLevel(stack, world)` 为了拿 `height * width`，调用了
`getEntity(stack, world)` —— 那是 `EntityList.createEntityByName + readFromNBT`，
**每 tick 凭空造一只完整实体**再扔掉。

**改法**：同上，拾取时把 `height*width` 算好存成 `carrySize`。

### 3. 每帧申请一块堆外内存

`RenderEvents.applyPerspective` 每帧 `ByteBuffer.allocateDirect(64)` 新建矩阵缓冲区。
堆外内存的分配要走 native，比堆内贵得多，而且这是 60 次/秒。

**改法**：静态复用同一个 `FloatBuffer`，每帧只改值。

### 顺带修掉的一个渲染 bug

`renderHand` 里为了"只在第一帧打印调试信息"，留下了一段这样的代码：

```java
if (!loggedFirstPerson)
    logProjection();

if (!loggedFirstPerson)          // ← 悬空 if，后面的语句成了它的循环体

GL11.glDepthMask(true);
GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
```

第二个 `if` 没有花括号，于是**深度清理只在第一帧执行**，之后贴墙站时手里的方块会被墙挡住。
调试代码删掉后这个 if 一并消失，深度清理恢复成每帧执行。

### 其他

- `CarryOn.LOGGER` 从 `getFormatterLogger` 换成普通 `getLogger`：
  formatter logger 把消息当 printf 格式串，拼接进去的配置项/异常信息里只要有一个 `%`
  就会抛 `IllegalFormatException`（本来在报错，结果又炸一次）。
- 三个网络包处理器把 `Minecraft.theWorld / thePlayer` 从"构造 Runnable 时读取"
  挪进了 `run()` —— 那个构造发生在 **netty 网络线程**，读客户端状态是未定义行为。

### 顺手删掉的死代码

| 位置 | 内容 | 为什么是死的 |
|---|---|---|
| `CarryOnOverride` | `render*` 12 个字段 + 24 个访问器 + 生成的 `equals`（193 行） | 1.10 上游用它按脚本定制渲染，移植版没有这套功能；`equals` 更是从未被调用（`HashMap<Integer, …>` 用 Integer 做键），而且它和只认 `path` 的 `hashCode` 本来就不一致 |
| `ScriptReader` | `render` 块解析（37 行） | 同上 |
| `CarryOnOverride` | `conditionGamestage` | 1.7.10 没有 GameStages，从未被解析也从未被读取 |
| `ScriptParseHelper` | `getXYZArray` / `getScale` / `getScaleValueFromString` | 只有彼此互相调用，没有任何外部入口 |
| `ModelOverridesHandler` | `hasOverride` | 无调用者 |
| `ScriptChecker` | `getOverride` | 无调用者（渲染定制删掉后就没读者了） |
| `CommonProxy` / `ClientProxy` / `CarryOn` | 空的 `postInit` 生命周期 | 三个文件里都是空方法 |
| `GuiFactoryCarryOn` | 注释掉的 `hasConfigGui` / `createConfigGui` | 那是 1.8+ 的接口，1.7.10 用不上 |
| 8 个文件 | 未使用的 import | — |
| 3 处 | `System.out.println` 调试输出（"放下生物"排查遗留） | 已用 LOGGER 取代或直接删掉 |

`ScriptReader` 的 `render` 块删掉后还顺带修了一个隐患：原来
`if ((object != null && conditions != null) || (object != null && render != null))`
允许"只有 object + render"的脚本，而这种脚本没有任何匹配条件，
`matchesAll` 会全部返回 true —— 等于把所有方块都变成可搬运。现在强制要求 `conditions`。

代码量 5244 → 4763 行，jar 90746 → 87157 字节。

---

## 与上游的行为差异

| 项 | 上游 1.10 | 本移植 |
|---|---|---|
| 双手判定 | 主手 + 副手都空 | 有 Backhand 时同上游；没有时只看主手 |
| 手臂姿态 | 隐藏真手臂 + 画假手臂 | 同上游（假手臂挂在 `bipedBody` 上） |
| 模型覆盖 | 按 NBT/meta 换模型 | 支持，机制不同（见下节） |
| 方块状态精度 | `stateid` 精确保存 | 4 bit meta；状态数 >16 的方块可能丢失信息 |
| `getActualState` | 取实际状态（如栅栏连接） | 返回 meta 本身；放置后靠邻居更新修正 |

---

## 模型覆盖：在 1.7.10 上怎么做

上游靠 `IBakedModel` 实现：配置里写 `A -> B`，渲染时把 A 的模型换成 B 的模型。
1.7.10 没有这层抽象，渲染是硬编码的（方块走 `RenderBlocks`，物品走 `ItemRenderer`），
所以不能照搬。

### 解法：把"怎么画"抽象成一个可替换对象

BackportAPI 提供了 `RenderTarget` —— 1.7.10 版的 baked model：

```java
public interface RenderTarget {
    void render(float brightness);   // 只管画，不管矩阵/贴图/光照
    IIcon getIcon();                 // 2D 图标
    String getDisplayName();
    boolean isBlock();
}
```

工厂 `RenderTargets` 按形态分派：

| 输入 | 绘制方式 |
|---|---|
| 方块 / 方块状态 | `RenderBlocks.renderBlockAsItem(block, meta, brightness)` |
| 物品且是 `ItemBlock` | 同上（1.8 里方块物品的模型本来就是方块） |
| 扁平物品 | `ItemRenderer.renderItemIn2D` 画带厚度的精灵 |

于是 `ModelOverridesHandler` 里存的不再是 `IBlockState`/`ItemStack`，
而是 `RenderTarget`——**语义与上游完全一致，只是换了载体**。

### 配置格式（与上游相同）

```
源方块[;meta][{nbt}] -> [(block)|(item)]目标[;meta][{nbt}]
```

```ini
minecraft:lit_furnace->minecraft:furnace
minecraft:tallgrass;1->(item)minecraft:tallgrass;1
quark:custom_chest{type:"spruce"}->quark:custom_chest;0
```

两处沿用上游的约定：
- 左侧 meta 为 0 或不写 → **匹配该方块的所有 meta**（所以没法单独指定 meta 0）；
- 不写 `(block)`/`(item)` → 默认按 `item` 处理。

### 匹配逻辑

按 `blockRegistry 名 + meta + NBT 子集` 匹配。NBT 用
`NbtFilter.matches(candidate, filter)`：filter 里的每个键在 candidate 里
都必须存在且相等，candidate 多余的键不影响结果——等价于 1.8 的
`NBTUtil.areNBTEquals`。

### 快捷栏图标：故意不画

搬运物那张物品在快捷栏/背包里**不显示任何图标**，`ItemTile.getIconFromDamage`
返回的是 `assets/carryonunoffical/textures/items/tile.png`——一张 16×16 **全透明**的贴图。

理由有两条：

1. 被搬的东西真正长什么样，`RenderEvents` 已经在第一/第三人称按 3D 模型画出来了。
   槽位里再放一张平面方块贴图（只有某一面），看起来像"手里还多了个东西"，反而干扰。
2. 1.7.10 里想画"被搬方块自己的图标"是做不到的：贴图分在两张图集——方块
   （spriteNumber 0）和物品（1），`IIcon` 只带一组**归一化** UV，不带"我属于哪张图集"，
   调用方按 `ItemStack.getItemSpriteNumber()` 决定绑哪张。本物品的 spriteNumber 是 1，
   方块贴图却在方块图集上，错绑不会报错，只会采样到物品图集里同一块区域——
   玩家看到的就是**"别的物品的图标"**，每个方块落在哪块区域完全随机。
   （曾经试过注册 `IItemRenderer` 接管 `ItemRenderType.INVENTORY`、自己绑方块图集，
   能画对，但按上面第 1 条，最终决定不画。）

### 抱起时锁背包

`InventoryLockEvents` 监听 `GuiOpenEvent`：手上有搬运物时，取消打开的
`GuiInventory` / `GuiContainerCreative`。

1.7.10 的 `Minecraft.displayGuiScreen` 会先 `post` 这个事件，**被取消就整段 return**，
所以背包根本不会初始化，屏幕上不会闪一下——比"打开后再 `closeScreen`"干净。

只拦背包，不拦箱子/工作台：搬运中右键已经被 `ItemEvents` 接管去"放下"了，
而且背包能重排物品栏、把搬运物挪到别的格子，等于绕过"搬运中锁定快捷栏"。

---

## 移植时踩到的坑（1.7.10 特有）

1. **`PlayerInteractEvent` 是单一类 + `Action` 枚举**，没有 `RightClickBlock` /
   `RightClickItem` 子类（那是 1.8 才拆的）。实体交互是**独立的** `EntityInteractEvent`。
2. **`event.useBlock` 是公开字段**，没有 `setUseBlock()`。
3. **`EntityPlayer` 没有 `isCreative()`**，用 `player.capabilities.isCreativeMode`。
4. **`EntityPlayer` 没有 `getStatFileWriter()`**，成就是
   `((EntityPlayerMP) player).func_147099_x().hasAchievementUnlocked(a)`。
5. **`EntityTameable.getOwner()` 直接返回实体**，1.8 的 `getOwnerId()` 返回 UUID。
6. **`Block.SoundType.getPlaceSound()` 在 MCP 9.05 里未映射**，要写 `func_150496_b()`。
7. **`JsonToNBT.getTagFromJson` 未映射**，要写 `func_150315_a()`，且返回 `NBTBase` 需转型。
8. **`NBTTagCompound.getKeySet()` 未映射**，要写 `func_150296_c()`。
9. **`GameRules.getBoolean` 叫 `getGameRuleBooleanValue`**。
10. **`KeyBinding.isKeyDown()` 叫 `getIsKeyPressed()`**。
11. **`Entity` 没有 `getPassengers()` / `getLowestRidingEntity()`**，用
    `riddenByEntity` / `ridingEntity` 链手工遍历。
12. **`InventoryPlayer.clearMatchingItems` 不存在**，用 `clearInventory(Item, -1)`。
13. **`ICommand` 用 `processCommand`**，`execute(MinecraftServer,...)` 是 1.8+。
14. **`TickEvent` 在 FML 总线，渲染事件在 Forge 总线** —— 同一个类要注册两次。

---

## 构建期陷阱：编译 jar 与重混淆映射不一致

这是本项目**最隐蔽**的一类 bug，单独说明。

### 现象

编译通过、构建通过、进游戏也能加载，直到某个操作触发才崩：

```
java.lang.NoSuchMethodError: 'int net.minecraft.item.ItemStack.getItemDamage()'
	at tschipp.carryon.common.item.ItemTile.storeTileData(ItemTile.java:225)
```

### 原因

构建链里有**两份来源不同的映射**：

| 产物 | 来源 | 命名风格 |
|---|---|---|
| `deps/forgebin.jar`（编译用） | Gradle 缓存里的 ForgeGradle 产物 | GTNH 风格：`getItemDamage` |
| `conf/mcp2srg.srg`（重混淆用） | 原版 MCP 9.05 | 原版风格：`getMetadata` |

同一个方法 `func_77960_j`，两边叫法不同。重混淆时 SpecialSource 在 srg 里
找不到 `getItemDamage` 这条映射，就**静默跳过**了——类名保留 MCP 名，
运行时自然找不到。

### 为什么不能靠"非 func_ 就是 Forge 方法"来判断

我一开始就是这么判断的，结果漏掉了 `getItemDamage`：
它在 `forgebin` 里存在，看起来像 Forge 附加方法，实际是 GTNH 改名的原版方法。

真正安全的判据只有一条：**重混淆后所有原版方法引用都必须是 `func_` 开头**。
剩下的非 `func_` 只允许是：

- `Entity.getEntityData`（Forge 附加）
- `Entity.isCreatureType`（Forge 附加）
- `RenderBlocks.getInstance`（Forge 附加）
- `Object` 的方法（`equals` / `toString` / `getMessage`）

### 防护

`verify.sh` 会在构建后静态扫描整个 jar，列出所有未被改写的原版方法/字段引用：

```bash
./build.sh 1.0.0
./verify.sh build/carryon-1.0.0.jar
```

期望输出只有上面那 7 条（3 个 Forge 方法 + 4 个 Object 方法）。
**每次改完代码都要跑一遍**——这类 bug 编译器抓不到。

### 本例的修法

`drop.getItemDamage()` 那行其实是多余的：`drop` 是用
`new ItemStack(..., block.damageDropped(meta))` 构造的，
读回来的值恒等于 `block.damageDropped(meta)`。直接复用，少一次分配，
也绕开了这个命名不一致的 API。

### 第二个实例：`Block.hasTileEntity(int)`

2026-10-31 又踩了一次同款：

```
未改写为 srg 的原版方法引用：7 条
net/minecraft/block/Block.hasTileEntity   ← 新增的这条
```

`srg` 里只有**无参**版本：

```
MD: net/minecraft/block/Block/hasTileEntity ()Z  net/minecraft/block/Block/func_149716_u ()Z
```

而 `forgebin` 里带 `int` 的那个也叫 `hasTileEntity(int)`（GTNH 改名），
重混淆查不到就跳过 → 运行时 `NoSuchMethodError`。

**修法**：不用它。判断"是不是容器"改成"放下后那个位置有没有 TileEntity"
（`Worlds.getTileEntity(world, pos) != null`），语义更准，也绕开了这个坑。

> 教训重申：`verify.sh` 输出的每一条都必须能解释清楚。
> 看到不认识的类名先当它是 bug，不要想当然认为"这是 Forge 附加方法"。

---

## 构建

```bash
export JAVA_HOME="C:/Program Files/Eclipse Adoptium/jdk-8.0.492.9-hotspot"
./build.sh 1.0.0
# 产物: build/carryon-1.0.0.jar
```

依赖 `deps/backportapi-1.1.0.jar`（编译期）。运行期需要 mods 目录里同时存在 BackportAPI 1.1.0。

---

## 待实测项

以下是静态验证无法覆盖、需要进游戏确认的：

1. 第一/第三人称方块的**位置与大小**是否合适（`RenderEvents` 里的 translate/scale 常量；
   现在默认值：第三人称 `(0, 0.70, ±0.40)` scale 0.6，第一人称 `(0, -1.5, -2.5)` scale 2.5）。
2. 搬运生物时 `doRender` 的朝向是否正确。
3. 配置 GUI 三个分类是否正常显示。
4. 脚本系统（`config/carryon-scripts/*.json`）在 `useScripts=true` 下的表现。
5. **放下朝向**：朝东南西北四个方向各放一次箱子/熔炉，正面都应朝着自己；
   模组容器（铁箱子等）走 `onBlockPlacedBy` 兜底，行为取决于模组自己的实现。
   林业箱子与神秘时代蒸馏器由 `PostPlaceRotators` 接管（日志里有 `[CarryOn] post-rotate` 一行）。
6. **快捷栏槽位**：搬起任何方块/容器，槽位里都应是空的（贴图全透明），
   既不该出现方块图标，也不该出现"别的物品"的图标。
7. **抱起时锁背包**：手上有搬运物时按 E（以及创造模式背包键）应毫无反应；
   放下之后能正常打开。
8. **第一人称没有手臂**：搬起体积小的方块（水晶箱、节点稳定器）时，屏幕右下角不应再出现手臂；
   放下后空手、手持普通物品时手臂都恢复正常（事件只在 `isCarried` 为真时取消）。

---

## 变更记录

> 版本号按发布批次编号，不严格单调。1.0.4 / 1.0.5 是开发期的内部迭代号，已并入下面的 1.0.1。

### 1.0.1

- **fix: 第一人称不再渲染手臂。**
  原版 `ItemRenderer.renderItemInFirstPerson` 里手臂是无条件绘制的
  （`RenderPlayer.renderFirstPersonArm` 只画 `bipedRightArm`），`HideHeldItem` 只能把
  手持物换成空操作、管不到手臂。搬运物体积大时被模型挡住看不见，体积小（水晶箱、
  节点稳定器等）就在右下角露出一条手臂。现在 `RenderHandEvent` 里画完搬运物后
  `setCanceled(true)`，`EntityRenderer.renderWorld` 整段跳过 `renderHand()`，
  手臂随手持物一起消失。深度清理与 70° 投影改由本模组自己补
  （原版那次 `glClear` 也在被跳过的分支里）。
  同时删掉了早先为"补一只手"而写的 `renderFirstPersonArm`（照抄原版空手分支的
  那份变换），第一人称只剩搬运物本体。

- **change: 搬起改为「潜行 + 右键」，不再占用独立按键。**
  上游本来就是蹲下触发；移植版早先用 `Keyboard.KEY_LSHIFT` 的 `KeyBinding`，再由客户端 tick
  把 `carryOnKeyPressed` 同步到服务端 NBT。现在服务端直接判 `player.isSneaking()`，
  同时删掉 `CarryOnKeybinds`、`SyncKeybindPacket` / `SyncKeybindPacketHandler` 及对应网络注册
  （消息 id 0 空出，1/2 不受影响），`options.txt` 里的旧键位与玩家 NBT 里的旧标签都会自动失效。
- 行为差异：潜行且手持物品时客户端不会发出右键包（原版 `doesSneakBypassUse` 判定），
  因此「放下」要先松开潜行——与上游一致。

- **fix: 与 BackportAPI 1.1.0 同步改名，修掉 Java 8 上的 `SecurityException: signer information does not match`。**
  BackportAPI 把 `@Config` / `ConfigManager`、`AttachCapabilitiesEvent`、`PlayerContainerEvent` 从
  `net.minecraftforge.common.config` / `net.minecraftforge.event[.entity.player]` 迁到了
  `net.backportapi.forge.config` / `net.backportapi.forge.event`。
  原因是 Forge 1.7.10 的 jar 带 DSA + SHA-1 签名，仍校验 SHA-1 的 JDK（8u251、JDK 9）会把
  `net.minecraftforge.common.config` 视为"已签名包"，无签名的回填类一进同包就被
  `ClassLoader.checkCerts` 拒绝（表现为 `NoClassDefFoundError: ConfigManager`）。
  改名后与 JDK 的签名策略无关，Java 8 / 17 / 21 / 25 行为一致。改动仅 6 行 import。
- 编译依赖 `deps/backportapi-1.1.0.jar`；运行期需 BackportAPI **1.1.0**（1.0.0 不同包名，不可混用）。

### 1.0.3

- **fix: TE-based blocks (Forestry chests, Thaumcraft alembic) did not face the player after being placed by Carry On.**
  这类方块的朝向不在 metadata 里，而在 TileEntity 字段里（`TileForestry.orientation` /
  `TileAlembic.facing`）。原来的流程是 `setBlockState → onBlockPlacedBy → readFromNBT`：
  林业的朝向逻辑在 **ItemBlock** 层（`ItemBlockForestry.placeBlockAt`），CarryOn 直接写世界
  永远不经过它；神秘时代的 `onBlockPlacedBy` 其实算对了，却紧接着被 `readFromNBT` 读回的旧值冲掉。
  新增放置后阶段 `PostPlaceRotators`（`IPostPlaceRotator` + 两张内置适配器，全反射、不 import
  林业/神秘时代），在 NBT 恢复之后修正朝向，并补一次 `markBlockForUpdate` 让客户端 TE 数据刷新
  （方块落地时那次描述包发在"设朝向之前"）。未命中适配器时返回 false，行为与修复前一致。
  配置项：`B:postPlaceRotation=true`。

### 1.0.0

- **fix: suppress duplicated block drop when picking up blocks that drop themselves in `breakBlock`**
  林业工作台（`Forestry:factory2:2`）把"掉落本体"写在 `breakBlock` 里（`tryDropAsWorktable` →
  `InventoryUtil.dropItemStackFromInventory` → `spawnEntityInWorld`）。1.7.10 移除方块一定会回调
  `breakBlock`，所以"抱起"这个动作会额外生成一个同种方块的物品实体——抱起 1 个，净赚 1 个。
  新增 `PickupGuard`：只在"移除被拾取方块"的那几行代码里建立作用域，把同一瞬间生成的
  **同世界 + 同坐标 + 同方块** 的 `EntityItem` 取消并 `setDead()`。不改 `breakBlock` 本身，
  正常挖掘、其他玩家/其他来源的掉落都不受影响。
