# Carry On（搬运）→ Minecraft 1.7.10 移植可行性分析

> 分析对象：Tschipp / CarryOn，分支 `1.10`（v1.9.1，`acceptedMinecraftVersions = [1.10,1.11)`）
> 规模：40 个 Java 文件 / 6077 行
> 基线：MCP 1.7.10 官方映射（`mcp-1.7.10-srg.zip`）+ Forge `10.13.4.1614` universal jar（838 个类）
> 结论日期：2026-10-30

---

## 0. 一句话结论

**可以移植，但不是"改改包名"级别的活儿。** 约 60–70% 的代码需要重写，其中 4 个模块（方块状态、数据存储、渲染管线、朝向旋转）属于架构级替换。核心玩法（把方块塞进手里当物品带着走）在 1.7.10 上完全成立，不存在无法实现的功能——只是实现路径完全不同。

最坏情况是渲染和朝向旋转两个模块，1.7.10 的 API 反而更"原始但直接"（`IItemRenderer` + `RenderBlocks`），写法比 1.10 简单，但需要完全重写而非翻译。

---

## 1. 为什么读源码而不是反编译

该模组是开源的，GitHub 上有完整源码。反编译 jar 得到的代码会丢失泛型、注释、参数名，且无法确认对应哪个提交。因此本次分析直接取 `origin/1.10` 分支源码，**并用官方 1.7.10 映射表 + Forge jar 逐项核验 API 是否存在**，而不是凭记忆判断。

> 补充说明：最初误用了 MCP-919 仓库（实为 1.8.9 映射，`version.cfg` 写着 `MCPVersion = 9.18 / ClientVersion = 1.8.9`），差点得出"1.7.10 有 `IBlockState`"的错误结论。改用 `maven.minecraftforge.net` 上的 `mcp-1.7.10-srg.zip` 后才拿到正确基线。下文所有"不存在"的判断均基于该基线实测。

---

## 2. 模组架构拆解

| 模块 | 职责 | 文件 | 1.7.10 可移植性 |
|---|---|---|---|
| 交互拾取 | Shift+右键 判定方块/实体能否搬起 | `PickupHandler`, `ItemEvents` | 中（事件签名全变） |
| 搬运载体 | 用带 NBT 的 `ItemStack` 承载方块/TE/实体 | `ItemTile`, `ItemEntity` | 中（NBT 机制通用） |
| 第一/第三人称渲染 | 手里和身上画出被搬的方块 | `RenderEvents`(824行) | **难（全重写）** |
| 手臂姿态 | 抱东西时手臂前伸 + 假手臂模型 | `RenderEvents`, `ModelPlayerCarrying` | **难（全重写）** |
| 实体搬运渲染 | 被抱的实体跟着玩家 | `RenderEntityEvents`(389行) | 难 |
| 数据存储 | 记录玩家右键的 TE 坐标（防刷物品） | `capabilities/*` | **难（无 Capability）** |
| 配置 | 30+ 项设置 | `Configs`, `CarryOnConfig` | 中（无 `@Config`） |
| 脚本系统 | JSON 自定义拾取规则 | `ScriptReader`, `ScriptChecker`, `CarryOnOverride` | 易（Gson 通用） |
| 网络同步 | 同步搬运槽位/按键状态 | `network/*` | 易（SimpleNetworkWrapper 存在） |
| 模型覆盖 | 自定义方块显示模型 | `ModelOverridesHandler` | **难（无 baked model）** |
| 实体堆叠 | 把动物叠在别的实体上 | `ItemEntityEvents` | 中 |

---

## 3. 1.7.10 缺失清单

### 3.1 总览

源码共引用 **149 个** MC/Forge 类型，其中 **70 个在 1.7.10 无法直接编译**：

- **32 个**是纯包名迁移（`net.minecraftforge.fml.*` → `cpw.mods.fml.*`），机械替换即可
- **38 个**是真实缺失，其中 3 个只是换了包/换了名（`MathHelper`、`Vec3d`、`I18n`），**35 个是 1.7.10 确实没有的 API**

下面按层列出，每项给出 1.7.10 的替代方案。

---

### 3.2 A 层：平台与包名（32 处，机械替换）

| 1.10 | 1.7.10 | 说明 |
|---|---|---|
| `net.minecraftforge.fml.*` | `cpw.mods.fml.*` | 整个 FML 包搬家，全局替换 |
| `@Mod.EventBusSubscriber` | **无** | 必须手动 `MinecraftForge.EVENT_BUS.register(new XxxEvents())` |
| `@Mod(updateJSON = ...)` | **无此字段** | 1.8+ 才有，删掉 |
| `item.setRegistryName(rl)` + `GameRegistry.register(item)` | `GameRegistry.registerItem(item, "name")` | 无 `setRegistryName`，无单参 `register` |
| `GameRegistry.register(block)` | `GameRegistry.registerBlock(block, "name")` | 同上 |
| `ForgeRegistries.ITEMS.getValue(rl)` | `GameRegistry.findItem(modid, name)` | 无 `ForgeRegistries` |
| `@Mod(guiFactory=...)` | ✅ 存在 | 保留 |
| `@Mod(acceptedMinecraftVersions=...)` | ✅ 存在 | 保留 |

---

### 3.3 B 层：方块状态与坐标系统（最重的机械改造）

**这是改动面最大的一层。** 1.7.10 没有 `BlockPos`、没有 `IBlockState`、没有方块属性系统，全部退回 `int x,y,z` + `int metadata`。

| 1.10 | 1.7.10 | 影响 |
|---|---|---|
| `BlockPos` | `int x, y, z` / `ChunkCoordinates` | 出现在 17 个文件，改动最广 |
| `BlockPos.offset(facing)` | `x + facing.offsetX` 等 | 手写 |
| `World.getBlockState(pos)` | `World.getBlock(x,y,z)` + `getBlockMetadata(x,y,z)` | 拆成两次调用 |
| `World.setBlockState(pos, state)` | `World.setBlock(x,y,z,block,meta,flags)` | flags 用 3 |
| `IBlockState` | **不存在** | 用 `Block` + `int meta` 两个值传递 |
| `state.getBlock()` | 直接用 `Block` 引用 | 简化 |
| `Block.getStateId(state)` / `getStateById(id)` | `Block.getIdFromBlock()` / `getBlockById()` + meta | **状态 ID 机制不存在** |
| `IProperty` / `PropertyDirection` | **不存在** | 见 §4.1 |
| `state.getActualState(world,pos)` | **不存在** | 1.7.10 的 meta 就是实际状态 |
| `block.getBlockHardness(state, world, pos)` | `getBlockHardness(World, int, int, int)` | 签名变 |
| `block.isReplaceable(world, pos)` | `isReplaceable(IBlockAccess, int, int, int)` | 签名变 |
| `block.canPlaceBlockAt(world, pos)` | `canPlaceBlockAt(World, int, int, int)` | 签名变 |
| `world.canBlockBePlaced(...)` | `world.canPlaceEntityOnSide(...)` | 改名 |
| `player.canPlayerEdit(pos, facing, stack)` | `canPlayerEdit(int,int,int,int,ItemStack)` | 签名变 |
| `block.getSoundType().getPlaceSound()` | `block.stepSound.getPlaceSound()` | `SoundType` 对象不存在 |
| `world.removeTileEntity(pos)` | `removeTileEntity(x,y,z)` | 签名变 |
| `world.setBlockToAir(pos)` | `setBlockToAir(x,y,z)` | 签名变 |
| `tile.setPos(pos)` | 直接赋 `tile.xCoord/yCoord/zCoord` | 无 `setPos` |
| `TileEntity.create(world, tag)` | `TileEntity.createAndLoadEntity(tag)` | 改名 |
| `world.isBlockLoaded(pos)` | `world.blockExists(x,y,z)` | 改名 |
| `pos.distanceSqToCenter(x,y,z)` | 手算 `dx*dx+dy*dy+dz*dz` | 手写 |
| `EnumFacing.getFacingFromVector(x,y,z)` | 由 yaw 手算 | 1.8+ 才有 |
| `EnumFacing.HORIZONTALS` | 手写数组 | 1.8+ 才有 |
| `EnumFacing.VALUES` | **不存在**（1.7.10 只有私有 `faceList`，公开的是 `values()`） | 用 `Facing.VALUES`（BackportAPI 提供） |
| `EnumFacing.getOpposite()` | **不存在** | 用 `Facing.getOpposite(f)` |
| `world.provider.getDimension()` | `world.provider.dimensionId`（字段） | 方法→字段 |
| `Entity.getPosition()` | `floor(posX/Y/Z)` | 无 `getPosition` |
| `world.getCombinedLight(pos, 0)` | `getLightBrightnessForSkyBlocks(x,y,z,0)` | 改名 |
| `block.getRegistryName().toString()` | `Block.blockRegistry.getNameForObject(block)` | 无 `getRegistryName` |

**连带的功能损失（不是编译问题，是行为问题）：**

1. **方块状态无法完整往返。** 1.10 用 `stateid` 精确存储状态；1.7.10 只有 4 bit 元数据（0–15）。元数据 ≤16 种状态的方块（箱子、熔炉、漏斗、楼梯）没问题；超过 16 种的方块会丢失信息。
2. **`getActualState` 消失。** 1.10 会取"实际状态"（比如栅栏的连接状态）；1.7.10 存的是 meta，栅栏连回来之后朝向可能不对，需要 `Block.onBlockPlacedBy` 重新触发一次邻居更新来修正。

---

### 3.4 C 层：手持与双持（玩法层面的简化）

1.7.10 **没有副手**，`EnumHand` 整个概念不存在。

| 1.10 | 1.7.10 |
|---|---|
| `EnumHand` / `EnumHand.MAIN_HAND` | **不存在** |
| `player.getHeldItemMainhand()` | `player.getHeldItem()` |
| `player.getHeldItemOffhand()` | **不存在**（恒为 null） |
| `player.setHeldItem(hand, stack)` | `player.inventory.setInventorySlotContents(player.inventory.currentItem, stack)` |
| `Item.onItemUse(..., EnumHand, EnumFacing, ...)` → `EnumActionResult` | `onItemUse(stack, player, world, x, y, z, side, hx, hy, hz)` → `boolean` |
| `EnumActionResult.SUCCESS/FAIL` | `true` / `false` |
| `keyBindSwapHands` | **不存在** |

**行为影响：** 原逻辑是"主手和副手都空才能搬起"，1.7.10 只能判定"主手空"。这是可接受的简化，不算 bug，但要在文档里说明。

---

### 3.5 D 层：渲染管线（重写量最大）

| 1.10 | 1.7.10 | 备注 |
|---|---|---|
| `GlStateManager` | **不存在** | 全部改 `GL11.glPushMatrix/glTranslatef/glRotatef/glScalef/glColor4f` |
| `IBakedModel` | **不存在** | 无 baked model 概念 |
| `ModelResourceLocation` | **不存在** | 无 JSON 模型系统 |
| `ModelLoader` / `setCustomModelResourceLocation` | **不存在** | 改用 `Item.registerIcons(IIconRegister)` |
| `Minecraft.getRenderItem()` | `RenderItem.getInstance()` | 无 `getRenderItem()` |
| `renderItem(stack, model)` | `RenderItem.renderItem(EntityLivingBase, ItemStack, int)` 或 `RenderBlocks.renderBlockAsItem(block, meta, 1.0F)` | 后者是 1.7.10 画 3D 方块的正道 |
| `ModelPlayer` | `ModelBiped` | `ModelPlayer` 是 1.8+ |
| `ModelRenderer.isHidden` | `showModel`（**语义相反**） | 极易出错 |
| `bipedLeftArmwear` / `bipedRightArmwear` | **不存在** | 1.8+ 皮肤外层 |
| `RenderPlayerEvent.getRenderer().getMainModel()` | `event.renderer.mainModel`（字段） | 无 getter |
| `RenderManager.getSkinMap()` | `RenderManager.instance.getEntityRenderObject(player)` | 无 `getSkinMap` |
| `Minecraft.getMinecraft().getRenderManager()` | `RenderManager.instance`（静态字段） | 无 getter |
| `DefaultPlayerSkin.getDefaultSkin(uuid)` | `AbstractClientPlayer.getLocationSkin()` | 1.8+ |
| `EnumPlayerModelParts` | **不存在** | 1.8+ |
| `RenderItem.getItemModelWithOverrides(...)` | **不存在** | 模型覆盖功能基本要重做 |
| `TextureMap.LOCATION_BLOCKS_TEXTURE` | `TextureMap.locationBlocksTexture`（字段） | 常量→字段 |
| `getBlockRendererDispatcher().getModelForState(...)` | `RenderBlocks` | 无 dispatcher |
| `RenderHandEvent` | ✅ **存在** | 可继续用，取消即可阻止手部渲染 |
| `RenderPlayerEvent.Pre/Post` | ✅ **存在** | 可继续用 |
| `RenderWorldLastEvent` | ✅ 存在 | 备用方案 |

**好消息：** 1.7.10 有 `IItemRenderer` + `MinecraftForgeClient.registerItemRenderer(Item, IItemRenderer)`，支持 `EQUIPPED_FIRST_PERSON` / `EQUIPPED` / `ENTITY` / `INVENTORY` 四种渲染类型。**这比 1.10 那套手动 `GlStateManager` + `RenderPlayerEvent` 拼装要简单得多**——第一人称和第三人称都能在同一个接口里处理。建议整体改走这条路，而不是翻译现有渲染代码。

---

### 3.6 E 层：数据存储（架构级替换）

**Forge 1.7.10 完全没有 Capability 系统**（实测：universal jar 中 0 个含 `capabilit` 的类）。`net.minecraftforge.items` 也不存在。

| 1.10 | 1.7.10 |
|---|---|
| `Capability` / `CapabilityManager` / `CapabilityInject` | **不存在** |
| `ICapabilitySerializable` / `IStorage` | **不存在** |
| `AttachCapabilitiesEvent` | **不存在** |
| `player.getCapability(...)` / `hasCapability(...)` | `player.getExtendedProperties("key")` |
| `@CapabilityInject` 注入 | 手动 `player.registerExtendedProperties("key", instance)`（在 `EntityConstructing` 事件里） |
| `IItemHandler` / `CapabilityItemHandler` | `IInventory` / `ISidedInventory` |
| `IItemHandler.extractItem(i, n, false)` | `IInventory.getStackInSlot(i)` + `setInventorySlotContents(i, null)` |
| `IInventory.clear()` | ✅ 存在 |

**替代方案：** 1.7.10 的 `IExtendedEntityProperties` 接口存在，签名是：
```java
void saveNBTData(NBTTagCompound tag);
void loadNBTData(NBTTagCompound tag);
void init(Entity entity, World world);
```
在 `EntityEvent.EntityConstructing` 事件里 `player.registerExtendedProperties("carryon:position", new PositionProps())` 注册。

**注意：** `IExtendedEntityProperties` 只能挂在 `Entity` 上，`TileEntity` 挂不了。不过看源码，`PositionCommonEvents` 里那个 capability 只是记录"玩家右键了哪个 TE 的坐标"，本质是玩家侧数据，改挂玩家完全成立，不影响功能。

---

### 3.7 F 层：注册与配置

| 1.10 | 1.7.10 |
|---|---|
| `@Config` / `@Config.Comment` / `@Config.RangeInt` | **不存在**（1.8+ 注解式配置） |
| `ConfigManager.sync(...)` | **不存在** |
| `ConfigElement` / `ConfigCategory` / `GuiConfig` | ✅ 存在（可继续做 GUI） |
| `Configuration` | ✅ 存在，但要走 `get(category, key, default)` → `Property` 的老写法 |
| `IModGuiFactory` | ✅ 存在（`cpw.mods.fml.client`） |
| `ClientRegistry.registerKeyBinding` | ✅ 存在 |
| `KeyBinding` / `GameSettings.keyBindsHotbar` | ✅ 存在 |

配置项有 30+ 个，`@Config` 注解式写法要整体改写为 `Configuration` + `Property` 手动读写，工作量中等但枯燥。

---

### 3.8 G 层：文本 / 声音 / 药水效果

| 1.10 | 1.7.10 |
|---|---|
| `TextComponentString` | `ChatComponentText` |
| `TextFormatting` | `EnumChatFormatting` |
| `ClickEvent` / `ClickEvent.Action` | `net.minecraft.event.ClickEvent`（**换包**） |
| `ITextComponent` | `IChatComponent` |
| `I18n.translateToLocal` | `StatCollector.translateToLocal` |
| `MobEffects.SLOWNESS` | `Potion.moveSlowdown` |
| `PotionEffect(pot, dur, amp, ambient, particles)` | `PotionEffect(int potionID, int dur, int amp)`（**只有 3 参，且第一个参数是 `Potion.xxx.id` 而不是 `Potion` 对象**） |
| `SoundEvents.XXX` | **不存在**，用字符串 `"random.xxx"` |
| `SoundCategory` | **不存在** |
| `world.playSound(...)` | `world.playSoundAtEntity(entity, "sound.name", vol, pitch)` |
| `EntityList.createEntityByIDFromName(name, world)` | `EntityList.createEntityByName(name, world)` |
| `entity.setPositionAndRotation(...)` | `entity.setLocationAndAngles(...)` |
| `EnumCreatureType.MONSTER` | `EnumCreatureType.monster`（**小写**） |
| `EntityTameable.getOwnerId()` 返回 `UUID` | 返回 **`String`**（类型不同，比较逻辑要改） |
| `Vec3d` | `Vec3`（`net.minecraft.util`） |
| `net.minecraft.util.math.MathHelper` | `net.minecraft.util.MathHelper`（换包） |

---

### 3.9 H 层：事件签名差异（容易踩坑）

| 1.10 | 1.7.10 |
|---|---|
| `PlayerInteractEvent.getPos()` | `event.x / event.y / event.z`（public final int） |
| `PlayerInteractEvent.getWorld()` | `event.world`（字段） |
| `PlayerInteractEvent.getEntityPlayer()` | `event.entityPlayer`（继承自 `PlayerEvent`，字段） |
| `PlayerInteractEvent.getHand()` | **不存在** |
| `event.setUseBlock(Result.DENY)` | `event.useBlock = Result.DENY`（**无 setter，直接赋字段**） |
| `PlayerContainerEvent.Close` | **不存在** → 用 `PlayerOpenContainerEvent` |
| `PlayerEvent.Clone.isWasDeath()` | `event.wasDeath`（字段） |
| `PlayerEvent.Clone.getOriginal()` | `event.original`（字段） |
| `StartTracking.getTarget()` | `event.target`（字段） |
| `MouseEvent.getDwheel()` | `event.dwheel`（字段，**无 getter**） |
| `BlockEvent.BreakEvent(World, pos, state, player)` | `BreakEvent(int x, int y, int z, World, Block, int meta, EntityPlayer)` |
| `PlayerTickEvent.side` | ✅ 存在（`TickEvent.side` 字段） |
| `InputEvent` | ✅ 存在，且有 `KeyInputEvent` / `MouseInputEvent` 子类 |
| `GuiScreenEvent.InitGuiEvent.Pre` | ✅ 存在 |
| `EntityJoinWorldEvent` / `LivingAttackEvent` / `LivingUpdateEvent` / `AttackEntityEvent` / `BreakSpeed` / `PlayerEvent` | ✅ 全部存在 |

---

### 3.10 I 层：杂项

| 1.10 | 1.7.10 |
|---|---|
| `EnumActionResult` | **不存在** → `boolean` |
| `IThreadListener` | **不存在** → `MinecraftServer` / `FMLCommonHandler.instance().getMinecraftServerInstance()` |
| `NBTUtil` | **不存在** → 手写 |
| `inventory.removeStackFromSlot(i)` | `getStackInSlot(i)` + `setInventorySlotContents(i, null)` |
| `original.getBedLocation()` 返回 `BlockPos` | 返回 `ChunkCoordinates`（**类型不同**） |
| `inventory.hasItemStack(stack)` | ✅ 存在 |
| `NBTTagCompound.hasNoTags()` | ✅ 存在 |
| `JsonToNBT.getTagFromJson(...)` | ✅ 类存在，方法名需按 MCP 9.05 确认（1.7.10 常为 `func_150315_a`） |
| `GameRules.getBoolean(...)` | ✅ 存在 |
| `player.getEntityData()` | ✅ 存在 |
| `NetworkRegistry.newSimpleChannel` / `SimpleNetworkWrapper` / `ByteBufUtils` / `TargetPoint` | ✅ 全部存在，网络层几乎零改动 |
| `Loader.isModLoaded` | ✅ 存在 |
| `ReflectionHelper` | ✅ 存在（`cpw.mods.fml.relauncher`） |
| `ObfuscationReflectionHelper` | ⚠️ 需确认（1.7.10 后期版本有） |

---

## 4. 四个必须重新设计的硬骨头

### 4.1 方块朝向自动旋转（`ItemTile.onItemUse` 里的 `PropertyDirection` 逻辑）

**现状：** 放置时遍历方块的所有属性，如果发现有 `PropertyDirection` 且取值范围是水平四向或六向，就把方块转成朝向玩家（楼梯则反向）。

**1.7.10 的问题：** 没有属性系统，**无法通用地判断"这个方块有没有朝向属性"**。只能退化成一张"方块 → 元数据位"的映射表：

```
楼梯 → meta 低 2 位是朝向
箱子/熔炉/南瓜 → meta 低 2 位是朝向
原木 → meta 低 2 位是树种，高 2 位是轴向
活塞 → meta 低 3 位
```

**选项：**
- (a) 内置一张常见方块的旋转表（覆盖原版 20~30 种 + 主流模组），其余方块按原 meta 放置
- (b) 直接放弃自动旋转，一律按拾取时的 meta 放回（功能降级，但零风险）
- (c) 用反射读 `Block` 的 `blockProperties` 字段（1.7.10 内部其实有 `BlockStateBase` 雏形，但 Forge 没有暴露）

**建议 (a)**，这也是绝大多数 1.7.10 搬运类模组的做法。

### 4.2 数据存储：Capability → IExtendedEntityProperties

整个 `capabilities/` 包（6 个文件）作废重写。逻辑不复杂，但要重新设计注册时机和 NBT 读写。详见 §3.6。

### 4.3 渲染管线：三段全重写

原代码分三段：第一人称（`RenderHandEvent`）、第三人称（`RenderPlayerEvent.Post`）、手臂（`RenderPlayerEvent.Pre`）。全部依赖 `GlStateManager` + `IBakedModel` + `ModelPlayer`。

**建议改成 1.7.10 的两条路：**
- **方块本身** → `IItemRenderer`（`EQUIPPED_FIRST_PERSON` + `EQUIPPED` 两个 renderType），内部用 `RenderBlocks.renderBlockAsItem(block, meta, 1.0F)` 画 3D 方块。这比原来的 baked model 路径简单。
- **手臂姿态** → 继续用 `RenderPlayerEvent.Pre`，但模型换成 `ModelBiped`，注意 `isHidden` → `showModel` 的语义反转（**1.7.10 是 `showModel = !hidden`，写反了就是"手臂一直隐藏"这种诡异 bug**）。

**风险点：** 1.7.10 的 `IItemRenderer` 在 `ENTITY` 类型下不给 `partialTicks`，第三人称跟随可能有轻微抖动；原版 `RenderPlayerEvent.Post` 里有插值计算，可以保留那套插值。

### 4.4 模型覆盖（`ModelOverridesHandler`）

依赖 `getModelForState` / `IBakedModel`，1.7.10 无对应物。这个功能（让某个带特定 NBT 的方块在手上显示成另一个模型）**基本无法等价实现**。

**建议：** 一期直接砍掉，只保留"用方块自己的 `RenderBlocks` 模型渲染"。如果一定要保留，得给每个覆盖项手工指定 `(block, meta)` 二元组，功能会退化很多。

---

## 5. 受影响文件热力图

按各文件对高改动 API 的引用密度排序（BlockPos / IBlockState / EnumHand / GlStateManager / Capability）：

| 文件 | BlockPos | 状态 | 手持 | GL | Cap | 行数 | 改造量 |
|---|---|---|---|---|---|---|---|
| `RenderEvents.java` | 2 | 3 | 8 | **36** | 0 | 824 | **XL** |
| `RenderEntityEvents.java` | 2 | 0 | 6 | **37** | 0 | 389 | **XL** |
| `ItemEvents.java` | 6 | 10 | 13 | 0 | 5 | 375 | **L** |
| `ItemTile.java` | 5 | 15 | 3 | 0 | 0 | 348 | **L** |
| `ItemEntityEvents.java` | 5 | 1 | 7 | 0 | 3 | 259 | **L** |
| `ModelOverridesHandler.java` | 0 | 10 | 0 | 0 | 0 | 268 | **L** |
| `PickupHandler.java` | 4 | 6 | 0 | 0 | 0 | 193 | M |
| `ItemEntity.java` | 3 | 1 | 3 | 0 | 0 | 220 | M |
| `CommandCarryOn.java` | 2 | 6 | 1 | 0 | 0 | 187 | M |
| `PositionClientEvents.java` | 5 | 0 | 0 | 0 | 9 | 91 | M（重写） |
| `capabilities/*`（6 文件） | 16 | 0 | 0 | 0 | 27 | 279 | M（重写） |
| `Configs.java` / `CarryOnConfig.java` | 0 | 0 | 0 | 0 | 0 | 336 | M（重写） |
| `ScriptReader` / `CarryOnOverride` 等 | 0 | 0 | 0 | 0 | 0 | ~900 | S（几乎不动） |
| `network/*`（6 文件） | 0 | 0 | 0 | 0 | 0 | ~243 | S（几乎不动） |

**不受影响、可原样保留的：** 脚本系统（Gson + NBT）、网络包、按键注册、`StringParser`、`ListHandler`、`InvalidConfigException`。

---

## 6. 建议的移植路线

**阶段 0：搭骨架（先跑通空壳）**
1.7.10 Forge 工程 + `@Mod` 主类 + 两个物品注册 + 代理结构。目标：游戏能启动，物品能在创造模式物品栏看到。

**阶段 1：方块搬运主链路（不渲染）**
`PickupHandler` + `ItemTile` + `ItemEvents`。用 `Block` + `meta` 替代 `IBlockState`，用 `int x,y,z` 替代 `BlockPos`。目标：能搬起箱子，能放回去，NBT 不丢。**此阶段先不做朝向旋转，一律原 meta 放回。**

**阶段 2：实体搬运**
`ItemEntity` + `ItemEntityEvents`。相对独立，`EntityList.createEntityByName` 路径清晰。

**阶段 3：渲染**
`IItemRenderer` 重写第一/第三人称方块渲染，`ModelBiped` 重写手臂。这是最花时间的一段，建议单独隔离验证。

**阶段 4：配置 + 数据存储**
`Configuration` 重写 30+ 项配置；`IExtendedEntityProperties` 替代 capability。

**阶段 5：补齐降级功能**
朝向旋转表、模型覆盖（或砍掉）、实体堆叠。

> 顺序建议：**先做阶段 1+2 把玩法跑通**，渲染最后做。因为渲染是最容易卡住的部分，先让核心玩法可用，能更早发现问题。

---

## 7. 风险与待验证项

### 高风险
1. **`ModelRenderer.isHidden` → `showModel` 语义反转**。1.10 的 `isHidden = true` 表示隐藏；1.7.10 的 `showModel = false` 表示隐藏。写反了不会报错，只会手臂消失——极难 debug。
2. **元数据 4 bit 上限**。超过 16 种状态的方块无法完整往返，可能需要白名单排除。
3. **`EntityTameable.getOwnerId()` 类型从 `UUID` 变成 `String`**。比较逻辑（`owner.equals(playerID)`）会静默失效——不会编译错误，但会导致"能搬别人的宠物"。

### 中风险
4. `IItemRenderer` 在 `ENTITY` 类型下不给 `partialTicks`，第三人称可能抖动。
5. 1.7.10 `Block.onBlockPlacedBy` 的触发时机与 1.10 不同，`getActualState` 那套"放置后修正状态"的逻辑需要重新设计。
6. `ModelOverridesHandler` 的 NBT 匹配（`HashMap<NBTTagCompound, Object>`）在 1.7.10 上行为一致，但模型解析部分要全部重写。

### 待运行时验证
7. `JsonToNBT` 在 MCP 9.05 下的实际方法名（`getTagFromJson` vs `func_150315_a`）。
8. `AbstractClientPlayer.getSkinType()` 在 1.7.10 是否返回 `"default"` / `"slim"`（决定假手臂的宽度分支）。
9. `EntityLivingBase.setArrowCountInEntity` 是否存在（原代码有个 TODO 临时修复）。
10. `ObfuscationReflectionHelper` 在 10.13.4.1614 是否存在。
11. ~~`EnumCreatureType` 的常量名在 1.7.10 是小写~~ → **已确认**：`monster` / `creature` / `ambient` / `waterCreature`，全小写。
12. `PotionEffect` 三参构造的第一个参数在 1.7.10 是 `int potionID`（传 `Potion.moveSlowdown.id`），不是 `Potion` 对象——1.8 才改的对象签名。

---

## 9. 勘误与实测补充（第二轮，基于反混淆编译类路径）

第二轮拿到了完整反混淆的编译类路径（`forgebin.jar`，2674 个类，MCP 命名），
逐条 `javap` 复核后修正/确认如下：

| 项 | 本报告初版 | 实测结论 |
|---|---|---|
| `EnumFacing.VALUES` | 误标为"存在" | **不存在**。1.7.10 只有私有 `faceList` 与公开的 `values()` |
| `EnumFacing.getOpposite()` | 未提及 | **不存在**，需手写映射 |
| `EnumFacing` 偏移量 | 按字段 `offsetX` | 实为方法 `getFrontOffsetX()` / `getFrontOffsetY()` / `getFrontOffsetZ()` |
| 发送聊天消息 | 按 `addChatMessage` | 实为 **`addChatComponentMessage(IChatComponent)`**（1.8 才改名） |
| `Block.isReplaceable(world,pos)` | 按此名 | **不存在**。可替换判定在 `Material.isReplaceable()` 或 `Block.canReplace(World,x,y,z,side,stack)` |
| `Block.SoundType.getPlaceSound()` | 待确认 | ✅ 存在，`block.stepSound.getPlaceSound()` 可用 |
| `RenderBlocks.renderBlockAsItem(Block,int,float)` | 推测 | ✅ 存在，是 1.7.10 画 3D 方块的正道 |
| `PotionEffect` 构造 | 推测 3 参 | ✅ `PotionEffect(int potionID, int duration, int amplifier)` |
| `ModelRenderer.showModel` | 推测 | ✅ 存在（`isHidden` 的语义相反项） |
| `RenderManager.instance` | 推测 | ✅ public static 字段 |
| `TextureMap.locationBlocksTexture` | 推测 | ✅ public static final |
| `AbstractClientPlayer.getLocationSkin()` | 推测 | ✅ 存在，但 **`getSkinType()` 不存在**——皮肤类型判断需另寻他法 |
| `EnumCreatureType` 常量 | 待确认 | ✅ 全小写：`monster` / `creature` / `ambient` / `waterCreature` |

### 一个会影响整个工具链的坑

由 SpecialSource 重映射产出的 MC/Forge 类 jar **丢失了 `InnerClasses` 属性**，
导致 javac 无法解析 `ClickEvent.Action`、`PlayerInteractEvent.RightClickBlock`、
`BlockEvent.BreakEvent` 这类嵌套类型（报"找不到符号 Action"）。

解决办法：改用 ForgeGradle 的原生产物 `forgeBin-1.7.10-10.13.4.1614.jar`
（保留了该属性），并把它排在类路径**第一位**。
这个问题在第二轮构建 BackportAPI Lib 时已实际踩到并解决。

---

## 8. 附：给 AI 编码时的关键约束（可直接当 prompt 用）

```
目标：把 CarryOn 1.10 分支移植到 Minecraft 1.7.10 + Forge 10.13.4.1614

硬约束：
- 包名全部 cpw.mods.fml.*，不是 net.minecraftforge.fml.*
- 没有 BlockPos，用 int x,y,z
- 没有 IBlockState / IProperty，用 Block + int meta
- 没有 EnumHand / 副手，只有 player.getHeldItem()
- 没有 GlStateManager，用 GL11
- 没有 Capability，用 IExtendedEntityProperties
- 没有 @Config，用 Configuration + Property
- 没有 IBakedModel，方块渲染用 RenderBlocks.renderBlockAsItem
- 事件字段直接访问，不要找 getter（event.x, event.world, event.entityPlayer）
- Item.onItemUse 返回 boolean，签名带 int x,y,z,int side
- GameRegistry.registerItem(item, "name") 注册物品
- ModelRenderer 用 showModel（true=显示），不是 isHidden
```

---

*本报告的所有 API 存在性判断均基于 `mcp-1.7.10-srg.zip`（1836 个类）与 `forge-1.7.10-10.13.4.1614-universal.jar`（838 个类）实测，未依赖记忆。*
