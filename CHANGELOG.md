# 更新日志

## 1.0.2（前置 BackportAPI 1.1.0）

**版本发布**：以 1.0.1 为基线，把下面三项通用兼容性修复（fix1 / fix2 / fix3）统一发布为 1.0.2。
源码 `@Mod.version`、`mcmod.info`、`build.sh` 默认版本、`verify.sh` 默认校验目标、
README 与交付 jar 名同步改为 1.0.2；本条只涉及版本号与文档，功能代码、三个默认开关、
前置 `backportapi-1.1.0.jar` 一律未动。

> 与早期回撤的「1.0.2 + BackportAPI 1.2.0」无关：那一版连同它的前置已整体回撤，
> 本版基线仍是 1.0.1 / BackportAPI 1.1.0。

- **fix1 物品形态兜底**：拾取时记录可选 `itemForm`，仅在显示名为未翻译裸键、
  或 `renderBlockAsItem` 零顶点时启用，解决「第一人称不渲染 + 显示 `tile.*` 键」。
- **fix2 3D 模型优先**：`CustomBlockModel` 调 `renderWorldBlock` 复现世界几何（pass 0/1），
  2D 图标降为最后兜底，解决「抱起来显示图标而不是模型」。
- **fix3 通用放置语义**：`PlacementPolicy` + `ItemPlacementSemantics` 用影子物品在快照上跑
  `onItemUse` 推断 meta 后无条件回滚；无 modid、类名、yaw→meta 特判，探针不可复现或失败
  自动退回原路径，解决把朝向写在 `Item.onItemUse` 里的方块放下时不响应朝向。
- 三个开关 `itemFormFallback` / `itemForm3DModel` / `itemPlacementSemantics` 均默认 true，
  可分别退回上一阶段行为。
- 验证：`build.sh 1.0.2` 全链路通过；`verify.sh` 与上一基线同为 7 条允许清单、零新增未映射引用；
  `tests/run_tests.sh` 113983 条断言 0 失败；class major 52（Java 8）。
- 部署：mods 目录只保留一个生效的 CarryOn jar（`CarryOn-Unoffcial-1.0.2.jar`），
  原生效的 1.0.1 构建改为 `.jar.disabled`；BackportAPI 1.1.0 保持不动。游戏内实测仍在本机完成。

## 1.0.2-fix3（通用的放置朝向语义推断）

**反馈**：放下 cfm 容器方块时不会「正面面向自己」；诊断确认四条可改写朝向的路径
（合成 IBlockState 属性、setBlockState、onBlockPlacedBy、PostPlaceRotators）全部落空，
因为 cfm 把朝向编码在独立 `ItemFurniturePlacer.onItemUse` 算出的 meta 0..3 里。
移植分析 §4.1 早在移植前就写明：1.7.10 无属性系统，只能退化成方块白名单，
「其余方块按原 meta 放置」——这是当时接受的功能降级，不是回归。

- 新增 `common/handler/PlacementPolicy`：无 MC 依赖的纯决策层（面顺序、meta 合法域、
  行为准入、采纳判据、候选去重、点击坐标反推），可独立编译穷举测试。
- 新增 `common/handler/ItemPlacementSemantics`：通用「放置语义探针」。候选物品来自
  NBT 物品形态 / `getItemDropped` / `getItemFromBlock` 三路公开 API；按 `onItemUse`
  方法签名（重载混淆后名字会变、签名不变）判定是否携带自定义放置语义；用影子
  ItemStack 在目标格及六邻居的完整快照上跑一次 `onItemUse`，只读它写出的 meta，
  随后无条件回滚。世界最终状态仍由原有 placeFromItem 流程产生。
- 不出现任何 modid、类名清单、yaw→meta 数值表或单面特判：六个面按「真实点击面优先」
  确定性遍历；标准 ItemBlock、固定 meta 型物品、探针抛异常、放错方块、非法 meta、
  推断值等于原值都自动退回原路径。属性路径已有结果时不覆盖。
- 新增开关 `itemPlacementSemantics`（默认 true）；设为 false 回到 fix2 行为。
- 成功时在日志打一行 `[CarryOn] placement semantics <block> meta=<old>-><new> side=... item=...`。
- 可复现性复核：同一探针重复采样，meta 不一致（随机变体型物品，如 cfm 的
  `itemStonePath` 用 `rand.nextInt()` 挑 meta）即放弃采纳，仍是不认识 mod 的行为判据。
- 验证：`tests/run_tests.sh` 共 113983 条断言全通过（面顺序、全 meta 域、准入 / 采纳
  真值表、候选去重、可复现性、六面坐标往返、20000 轮随机压力）；对 4 个不同 mod 的
  真实字节码抽样分类稳定；`verify.sh` 与 fix2 基线同为 7 条，零新增未映射引用。

## 1.0.2-fix2（3D 模型优先，2D 图标仅作兜底）

**反馈**：fix1 搬起 cfm 洗衣机时第一人称显示的是放大的 2D 图标，不是 3D 模型。

- 原因不是渲染失败：fix1 为规避风险，主动跳过 `renderWorldBlock` 改画物品图标。
  cfm 3.4.7 没有注册任何 `IItemRenderer`，47 个 ISBRH 的几何全部只在
  `renderWorldBlock` 里，`renderInventoryBlock` 为空。要模型就必须走世界渲染器。
- 新增 `client/render/CustomBlockModel`：伪造单格 `IBlockAccess`，调用
  `RenderingRegistry.renderWorldBlock`，自行开闭 Tessellator 批次，
  pass 0/1 各一遍（cfm 的玻璃门只在 pass 1 追加），`glTranslate(-0.5)` 把 0..1
  几何居中；完整保存/还原 `RenderBlocks` 单例状态与 `ClientProxy.renderPass`。
  反编译确认 cfm 的世界渲染器不读 TileEntity，故洗衣机/洗碗机等容器方块同样适用。
- 渲染回退改为三级：model override → 3D 世界几何 → 2D 图标。任一级抛异常自动降级，
  不会回到「手里什么都没有」。每个方块只在日志报一次
  `[CarryOn] world model OK/MISS: <name> renderType=... passes=... vertexData=...`。
- 新增开关 `itemForm3DModel`（默认 true）；设为 false 可退回 fix1 的纯图标行为。
- 环境核对：JDK 25 + lwjgl3ify + Angelica 2.2.44（对 `Tessellator` 有 5 个 Mixin），
  12 GB 堆占用 5.1 GB、无 OOM / GL 错误 / CarryOn 异常；fix1 的 `SpriteFallback`
  已证明 `startDrawingQuads/draw` 在该环境可用。假世界为静态单例，每帧无额外分配。

## 1.0.2-fix1（基于 GitHub 旧版本 286cc535，前置仍为 BackportAPI 1.1.0）

**通用兼容性修复**：搬运「方块 / 物品解耦注册」的模组方块时，第一人称不再隐形、显示名不再是一串未翻译的键。
典型触发者是 MrCrayfish's Furniture 3.4.7（洗衣机、洗碗机等全部 ISBRH 家具），但不针对任何 modid 特判。

- 这类模组只在 `renderWorldBlock` 里提供世界几何，`renderInventoryBlock` 为空且
  `shouldRender3DInInventory()` 返回 false，名字与 2D 图标都挂在一个独立的 `Item` 上；
  CarryOn 原先只从 `Block` 出发，于是 `renderBlockAsItem` 零顶点、
  `Item.getItemFromBlock` 又只拿到 FML 自动生成的默认 ItemBlock（缺 `tile.*` 翻译键）。
- 拾取时额外记录一个**可选**的 `itemForm`（`getItemDropped` 得到的物品注册名）。
  只在原路径失败时启用两条兜底：名字仍是 `tile.*` / `item.*` 裸键时改用物品形态的名字；
  `renderType >= 42` 且 Forge 的 `renderItemAsFull3DBlock` 为 false 时改画物品形态的 2D 图标。
- 不调用 `renderWorldBlock`、不伪造 `IBlockAccess`、不改写 `RenderBlocks` 单例状态、
  不碰 `canRenderInPass`；原版方块与正常模组的路径逐字不变，老物品自动回退到原行为。
  配置 `itemFormFallback`（默认 true）可一键关闭全部新行为。
- 只改 CarryOn 本体，BackportAPI 1.1.0 无需更换。

## 1.0.1（前置 BackportAPI 1.1.0）

**现在 Java 8 也能玩了，不再需要 Java 17 + lwjgl3ify。**

以前只能在 lwjgl3ify 的高版本 Java 环境里启动，不是模组真的需要新 Java，而是 Forge 1.7.10 带的是一份很老的 SHA-1 签名：Java 8 还会去核对它，前置库 BackportAPI 往同一个包里加的几个类因此被判"签名不一致"、直接被系统拒绝加载，游戏在启动阶段就报错退出；Java 17 及以上默认不再承认这种老签名，问题只是被掩盖了。这次把这几个类挪到了 BackportAPI 自己的包名下，不再和 Forge 的签名包混在一起，Java 8 / 17 / 21 / 25 都能正常启动，lwjgl3ify 装不装都行。

另外搬东西的方式改回原版习惯：**蹲下 + 右键**搬起，**松开蹲下键**再右键放下。

**抱起东西时第一人称不再出现手臂。**

之前抱大箱子看不见手，是因为手被箱子的模型挡住了；换成水晶箱、节点稳定器这类体积小的方块，右下角就会露出原版那只右手。这只手是原版 `ItemRenderer.renderItemInFirstPerson` 无条件画的（和手持物是两段独立绘制，隐藏手持物的那层拦不住它）。现在搬运时取消第一人称手部渲染，整只手臂（以及本来就被隐藏的手持物）都不再绘制；深度清理和 70° 投影改由模组自己补上。

同时也删掉了早先额外补画的那只手臂，第一人称只剩搬运物本体。空手、手持普通物品时手臂与以前完全一样。
