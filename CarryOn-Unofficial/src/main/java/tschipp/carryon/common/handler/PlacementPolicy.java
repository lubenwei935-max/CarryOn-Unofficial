package tschipp.carryon.common.handler;

/**
 * 「放置语义委托」的纯决策层。
 *
 * <p>本类<b>不引用任何 Minecraft / Forge 类型</b>，因此可以在没有游戏 jar 的
 * 环境里独立编译与穷举测试（见 {@code tests/PlacementPolicyTest.java}）。
 * 生产侧的 {@link ItemPlacementSemantics} 只负责采集事实，所有判断都委托给这里，
 * 保证「被测的那份逻辑」就是「跑在游戏里的那份逻辑」。
 *
 * <h3>为什么需要它</h3>
 * 1.7.10 没有 IBlockState 属性系统，朝向由每个方块家族各自编码进 4 bit metadata，
 * 且很多模组把编码逻辑写在独立 Item 的 {@code onItemUse} 里，而不是 Block 里。
 * 与其为每个模组登记一张 meta 表（无法穷举、且随模组版本失效），不如让模组自己的
 * 放置逻辑在受控探针里跑一次，再把结果作为事实采纳。这里的所有规则都只依赖
 * 「行为特征」：是否自定义放置、探针是否真的在目标格写出了目标方块、
 * 写出的 meta 是否合法且发生了变化。整个过程不出现任何 modid、类名清单或
 * yaw→meta 数值表。
 */
public final class PlacementPolicy
{
	/** 1.7.10 的六个面编号 0..5。 */
	public static final int SIDE_COUNT = 6;

	/** 1.7.10 的 metadata 是 4 bit。 */
	public static final int META_BITS = 4;
	public static final int META_MASK = (1 << META_BITS) - 1;

	/** 探针没有给出可用结果时的哨兵值。 */
	public static final int NO_META = -1;

	private PlacementPolicy()
	{
	}

	/** 把任意输入折叠成合法面编号；非法值统一退到 0，保证调用方永远不会越界。 */
	public static int normalizeSide(int side)
	{
		return side >= 0 && side < SIDE_COUNT ? side : 0;
	}

	/**
	 * 探针尝试面的确定性顺序：玩家真实点击面优先，其余按编号补齐。
	 *
	 * <p>优先真实面是为了保留那些「朝向由点击面决定」的模组语义；
	 * 仍遍历全部六个面，是为了覆盖「点击面所在格不满足该模组的放置前置条件」
	 * 的情况。不假设任何一面更特殊。
	 */
	public static int[] sideOrder(int clickedSide)
	{
		int first = normalizeSide(clickedSide);
		int[] out = new int[SIDE_COUNT];
		out[0] = first;
		int n = 1;
		for (int s = 0; s < SIDE_COUNT; s++)
		{
			if (s != first)
				out[n++] = s;
		}
		return out;
	}

	public static boolean isMetaValid(int meta)
	{
		return meta >= 0 && meta <= META_MASK;
	}

	/** 写回世界前把任意整数收敛到 4 bit，避免非法 meta 破坏区块数据。 */
	public static int normalizeMeta(int meta)
	{
		return meta & META_MASK;
	}

	/**
	 * 行为准入：只有当 {@code onItemUse} 被解析到、且声明者既不是原版
	 * {@code Item} 也不是标准 {@code ItemBlock} 时，才认为该物品携带了
	 * 模组自定义的放置语义。
	 *
	 * <p>标准 ItemBlock 的语义（{@code placeBlockAt}，meta 来自 ItemStack damage）
	 * 已被 CarryOn 原有的属性路径 / onBlockPlacedBy 路径完整覆盖，交给它反而是回归。
	 * 这里的两个布尔由调用方通过反射签名得出，判定本身与具体类无关。
	 */
	public static boolean hasCustomPlacement(boolean resolved, boolean declaredByItem, boolean declaredByItemBlock)
	{
		return resolved && !declaredByItem && !declaredByItemBlock;
	}

	/**
	 * 是否采纳探针写出的 meta。四个条件全部是行为事实，不含任何类型/模组假设：
	 * 探针执行完成、目标格确实是被搬运的方块、两个 meta 都在合法域内、
	 * 且推断值与原值不同（相同意味着委托没有贡献任何新信息，回原路径等价）。
	 */
	public static boolean accepts(int originalMeta, int inferredMeta, boolean blockMatches, boolean probeSucceeded)
	{
		return probeSucceeded && blockMatches
				&& isMetaValid(originalMeta) && isMetaValid(inferredMeta)
				&& inferredMeta != originalMeta;
	}

	/** 是否进入委托流程。开关关闭、客户端侧、无候选物品时一律保持原路径。 */
	public static boolean shouldAttempt(boolean enabled, boolean serverSide, boolean hasCandidates)
	{
		return enabled && serverSide && hasCandidates;
	}

	/**
	 * 候选去重，保持首次出现顺序。{@code -1} 表示空槽，直接跳过。
	 * 用于把「NBT 物品形态 / 掉落物 / 默认 ItemBlock」三路候选收敛成不重复序列。
	 */
	public static int[] dedupe(int... ids)
	{
		if (ids == null || ids.length == 0)
			return new int[0];

		int[] tmp = new int[ids.length];
		int n = 0;
		for (int i = 0; i < ids.length; i++)
		{
			int id = ids[i];
			if (id < 0)
				continue;
			boolean seen = false;
			for (int j = 0; j < n; j++)
			{
				if (tmp[j] == id)
				{
					seen = true;
					break;
				}
			}
			if (!seen)
				tmp[n++] = id;
		}

		int[] out = new int[n];
		System.arraycopy(tmp, 0, out, 0, n);
		return out;
	}

	/**
	 * 可复现性：多次探针写出的 meta 必须完全一致且合法，才认为该物品表达的是
	 * 「放置语义」而非随机变体 / 外部状态。
	 *
	 * <p>有些物品在 {@code onItemUse} 里用随机数挑变体 meta；单次探针无法区分它
	 * 与「按玩家朝向算 meta」。要求重复采样一致，就能在不认识任何 mod 的前提下
	 * 把这类不确定性排除掉。
	 */
	public static boolean isStable(int... samples)
	{
		if (samples == null || samples.length == 0)
			return false;
		int first = samples[0];
		if (!isMetaValid(first))
			return false;
		for (int i = 1; i < samples.length; i++)
		{
			if (samples[i] != first)
				return false;
		}
		return true;
	}

	/**
	 * 由目标格反推 {@code onItemUse} 的点击格：1.7.10 的放置物品普遍把
	 * 「点击坐标 + 面偏移」当作目标格，因此点击格 = 目标格 − 面偏移。
	 */
	public static int clickCoord(int target, int facingOffset)
	{
		return target - facingOffset;
	}
}
