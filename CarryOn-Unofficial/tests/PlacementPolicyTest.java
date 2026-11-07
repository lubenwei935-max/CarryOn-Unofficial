package tschipp.carryon.common.handler;

import java.util.Arrays;
import java.util.Random;

/**
 * {@link PlacementPolicy} 的多输入穷举 / 压力验证。
 *
 * <p>刻意不依赖任何 Minecraft / Forge 类：本测试与生产用的纯决策类一起用
 * 裸 JDK 编译运行，因此验证的是「跑在游戏里的那份逻辑」本身，而不是复刻品。
 * 覆盖：非法与极端面编号、全 meta 域、行为准入全真值表、采纳判据全真值表、
 * 候选去重、点击坐标往返、固定种子随机压力。
 */
public final class PlacementPolicyTest
{
	private static int assertions;
	private static int failures;

	private PlacementPolicyTest()
	{
	}

	private static void check(boolean condition, String message)
	{
		assertions++;
		if (!condition)
		{
			failures++;
			if (failures <= 20)
				System.out.println("  FAIL  " + message);
		}
	}

	/** 独立的规范复述，用于和实现逐组对照。 */
	private static boolean specAccepts(int original, int inferred, boolean matches, boolean executed)
	{
		if (!executed)
			return false;
		if (!matches)
			return false;
		if (original < 0 || original > 15)
			return false;
		if (inferred < 0 || inferred > 15)
			return false;
		return original != inferred;
	}

	private static boolean isPermutation(int[] values)
	{
		if (values == null || values.length != PlacementPolicy.SIDE_COUNT)
			return false;
		boolean[] seen = new boolean[PlacementPolicy.SIDE_COUNT];
		for (int i = 0; i < values.length; i++)
		{
			int v = values[i];
			if (v < 0 || v >= PlacementPolicy.SIDE_COUNT || seen[v])
				return false;
			seen[v] = true;
		}
		return true;
	}

	private static int firstIndex(int[] values, int value)
	{
		for (int i = 0; i < values.length; i++)
		{
			if (values[i] == value)
				return i;
		}
		return -1;
	}

	private static boolean dedupeWellFormed(int[] out, int[] in)
	{
		for (int i = 0; i < out.length; i++)
		{
			if (out[i] < 0)
				return false;
			if (firstIndex(in, out[i]) < 0)
				return false;
			for (int j = 0; j < i; j++)
			{
				if (out[j] == out[i])
					return false;
			}
			if (i > 0 && firstIndex(in, out[i - 1]) >= firstIndex(in, out[i]))
				return false;
		}
		return true;
	}

	private static void group(String name, int before)
	{
		System.out.println(String.format("  %-34s %5d assertions, %s",
				name, assertions - before, failures == 0 ? "OK" : "FAILED"));
	}

	private static void testSides()
	{
		int before = assertions;
		int[] inputs = new int[] {
			Integer.MIN_VALUE, -100000, -2, -1, 0, 1, 2, 3, 4, 5, 6, 7, 999, Integer.MAX_VALUE
		};
		for (int i = 0; i < inputs.length; i++)
		{
			int in = inputs[i];
			int[] order = PlacementPolicy.sideOrder(in);
			check(isPermutation(order), "sideOrder 必须是 0..5 的排列, input=" + in
					+ " got=" + Arrays.toString(order));
			check(order[0] == PlacementPolicy.normalizeSide(in),
					"sideOrder 首项必须是归一化后的真实点击面, input=" + in);
			check(Arrays.equals(order, PlacementPolicy.sideOrder(in)),
					"sideOrder 必须确定, input=" + in);
		}
		for (int s = -100; s <= 100; s++)
		{
			int n = PlacementPolicy.normalizeSide(s);
			check(n >= 0 && n < PlacementPolicy.SIDE_COUNT, "normalizeSide 越界, s=" + s);
			if (s >= 0 && s < PlacementPolicy.SIDE_COUNT)
				check(n == s, "合法面编号不得被改写, s=" + s);
		}
		group("sideOrder / normalizeSide", before);
	}

	private static void testMeta()
	{
		int before = assertions;
		for (int m = -1000; m <= 1000; m++)
		{
			check(PlacementPolicy.isMetaValid(m) == (m >= 0 && m <= PlacementPolicy.META_MASK),
					"isMetaValid 与 4-bit 域不一致, m=" + m);
			int normalized = PlacementPolicy.normalizeMeta(m);
			check(normalized >= 0 && normalized <= PlacementPolicy.META_MASK,
					"normalizeMeta 输出越界, m=" + m);
			check(normalized == (m & 15), "normalizeMeta 不等于掩码, m=" + m);
		}
		group("isMetaValid / normalizeMeta", before);
	}

	private static void testAdmission()
	{
		int before = assertions;
		for (int mask = 0; mask < 8; mask++)
		{
			boolean resolved = (mask & 1) != 0;
			boolean byItem = (mask & 2) != 0;
			boolean byItemBlock = (mask & 4) != 0;
			boolean expected = resolved && !byItem && !byItemBlock;
			check(PlacementPolicy.hasCustomPlacement(resolved, byItem, byItemBlock) == expected,
					"行为准入真值表不符, mask=" + mask);
		}
		group("hasCustomPlacement (8 combos)", before);
	}

	private static void testAccepts()
	{
		int before = assertions;
		int[] metas = new int[] { -1000, -1, 0, 1, 2, 7, 14, 15, 16, 1000 };
		for (int a = 0; a < metas.length; a++)
		{
			for (int b = 0; b < metas.length; b++)
			{
				for (int flags = 0; flags < 4; flags++)
				{
					int original = metas[a];
					int inferred = metas[b];
					boolean matches = (flags & 1) != 0;
					boolean executed = (flags & 2) != 0;
					boolean actual = PlacementPolicy.accepts(original, inferred, matches, executed);
					check(actual == specAccepts(original, inferred, matches, executed),
							"accepts 与规范不符, o=" + original + " i=" + inferred + " f=" + flags);
					if (!matches || !executed)
						check(!actual, "探针失败/方块不符时不得采纳, f=" + flags);
				}
			}
		}
		// 合法 4-bit 域内全穷举：16 x 16 x 4
		for (int original = 0; original <= 15; original++)
		{
			for (int inferred = 0; inferred <= 15; inferred++)
			{
				for (int flags = 0; flags < 4; flags++)
				{
					boolean matches = (flags & 1) != 0;
					boolean executed = (flags & 2) != 0;
					boolean actual = PlacementPolicy.accepts(original, inferred, matches, executed);
					check(actual == (matches && executed && original != inferred),
							"合法域 accepts 不符, o=" + original + " i=" + inferred + " f=" + flags);
					if (original == inferred)
						check(!actual, "推断值等于原值时必须回退, o=" + original);
				}
			}
		}
		group("accepts (meta domain + flags)", before);
	}

	private static void testAttempt()
	{
		int before = assertions;
		for (int mask = 0; mask < 8; mask++)
		{
			boolean enabled = (mask & 1) != 0;
			boolean server = (mask & 2) != 0;
			boolean candidates = (mask & 4) != 0;
			boolean expected = enabled && server && candidates;
			check(PlacementPolicy.shouldAttempt(enabled, server, candidates) == expected,
					"shouldAttempt 真值表不符, mask=" + mask);
		}
		group("shouldAttempt (8 combos)", before);
	}

	private static void testDedupe()
	{
		int before = assertions;
		int[][] cases = new int[][] {
			new int[0],
			new int[] { -1 },
			new int[] { -1, -1, -1 },
			new int[] { 0 },
			new int[] { 5, 5, 5 },
			new int[] { 3, 1, 3, 2, 1 },
			new int[] { 0, -1, 0, 4095, 4095, -2, 7 },
			new int[] { Integer.MAX_VALUE, -1, Integer.MAX_VALUE, 0 },
			new int[] { 256, 257, 258, 256, 257, -5, 259 }
		};
		for (int c = 0; c < cases.length; c++)
		{
			int[] in = cases[c];
			int[] out = PlacementPolicy.dedupe(in);
			check(dedupeWellFormed(out, in), "dedupe 结果不满足去重/保序/无负值, case=" + c
					+ " out=" + Arrays.toString(out));
			check(Arrays.equals(out, PlacementPolicy.dedupe(out)), "dedupe 必须幂等, case=" + c);
			int expectedCount = 0;
			for (int i = 0; i < in.length; i++)
			{
				if (in[i] < 0)
					continue;
				boolean seen = false;
				for (int j = 0; j < i; j++)
				{
					if (in[j] == in[i])
					{
						seen = true;
						break;
					}
				}
				if (!seen)
					expectedCount++;
			}
			check(out.length == expectedCount, "dedupe 数量不符, case=" + c);
		}
		check(PlacementPolicy.dedupe((int[]) null).length == 0, "dedupe(null) 应返回空数组");
		group("dedupe (9 shapes + idempotence)", before);
	}

	private static void testStability()
	{
		int before = assertions;
		check(!PlacementPolicy.isStable((int[]) null), "isStable(null) 必须为 false");
		check(!PlacementPolicy.isStable(new int[0]), "isStable(空) 必须为 false");

		int[] values = new int[] { -1000, -1, 0, 1, 7, 15, 16, 1000 };
		for (int a = 0; a < values.length; a++)
		{
			check(PlacementPolicy.isStable(values[a]) == PlacementPolicy.isMetaValid(values[a]),
					"单样本 isStable 应等价于合法域, v=" + values[a]);
			for (int b = 0; b < values.length; b++)
			{
				boolean expected = PlacementPolicy.isMetaValid(values[a]) && values[a] == values[b];
				check(PlacementPolicy.isStable(values[a], values[b]) == expected,
						"双样本 isStable 不符, a=" + values[a] + " b=" + values[b]);
			}
		}

		// 三次采样（生产的复核形状）：任一漂移都必须被拒绝。
		for (int base = 0; base <= 15; base++)
		{
			check(PlacementPolicy.isStable(base, base, base), "三次一致应通过, base=" + base);
			check(!PlacementPolicy.isStable(base, base, (base + 1) & 15), "尾次漂移必须拒绝, base=" + base);
			check(!PlacementPolicy.isStable(base, (base + 1) & 15, base), "中途漂移必须拒绝, base=" + base);
		}

		Random random = new Random(7721L);
		for (int n = 0; n < 5000; n++)
		{
			int[] samples = new int[1 + random.nextInt(5)];
			for (int i = 0; i < samples.length; i++)
				samples[i] = random.nextInt(41) - 20;
			boolean expected = PlacementPolicy.isMetaValid(samples[0]);
			for (int i = 1; i < samples.length && expected; i++)
				expected = samples[i] == samples[0];
			check(PlacementPolicy.isStable(samples) == expected,
					"随机 isStable 不符, n=" + n + " " + Arrays.toString(samples));
		}
		group("isStable (reproducibility)", before);
	}

	private static void testClickCoord()
	{
		int before = assertions;
		int[] targets = new int[] { -30000000, -1000, -1, 0, 1, 1000, 30000000 };
		int[] offsets = new int[] { -1, 0, 1 };
		for (int t = 0; t < targets.length; t++)
		{
			for (int o = 0; o < offsets.length; o++)
			{
				int target = targets[t];
				int off = offsets[o];
				int click = PlacementPolicy.clickCoord(target, off);
				check(click + off == target, "点击格 + 面偏移必须还原目标格, t=" + target + " off=" + off);
			}
		}

		// 1.7.10 EnumFacing 六个面的真实偏移向量（协议常量，取自 mcp.jar）。
		// 面编号 0..5 = DOWN, UP, NORTH, SOUTH, WEST, EAST。
		int[][] faceOffsets = new int[][] {
			{ 0, -1, 0 }, { 0, 1, 0 }, { 0, 0, -1 },
			{ 0, 0, 1 }, { -1, 0, 0 }, { 1, 0, 0 }
		};
		for (int side = 0; side < faceOffsets.length; side++)
		{
			int[] order = PlacementPolicy.sideOrder(side);
			for (int k = 0; k < order.length; k++)
			{
				int s = order[k];
				int[] v = faceOffsets[s];
				for (int t = 0; t < targets.length; t++)
				{
					check(PlacementPolicy.clickCoord(targets[t], v[0]) + v[0] == targets[t],
							"x 分量往返失败, side=" + s);
					check(PlacementPolicy.clickCoord(targets[t], v[1]) + v[1] == targets[t],
							"y 分量往返失败, side=" + s);
					check(PlacementPolicy.clickCoord(targets[t], v[2]) + v[2] == targets[t],
							"z 分量往返失败, side=" + s);
				}
			}
		}
		group("clickCoord (6 faces x coords)", before);
	}

	private static void testRandomStress()
	{
		int before = assertions;
		Random random = new Random(20261108L);
		for (int n = 0; n < 20000; n++)
		{
			int side = random.nextInt(41) - 20;
			int[] order = PlacementPolicy.sideOrder(side);
			check(isPermutation(order), "随机 sideOrder 非排列, side=" + side);

			int original = random.nextInt(401) - 200;
			int inferred = random.nextInt(401) - 200;
			boolean matches = random.nextBoolean();
			boolean executed = random.nextBoolean();
			check(PlacementPolicy.accepts(original, inferred, matches, executed)
					== specAccepts(original, inferred, matches, executed),
					"随机 accepts 与规范不符, n=" + n);
			check(PlacementPolicy.normalizeMeta(original) >= 0
					&& PlacementPolicy.normalizeMeta(original) <= 15,
					"随机 normalizeMeta 越界, n=" + n);

			int[] raw = new int[1 + random.nextInt(6)];
			for (int i = 0; i < raw.length; i++)
				raw[i] = random.nextInt(8) - 2;
			int[] dedup = PlacementPolicy.dedupe(raw);
			check(dedupeWellFormed(dedup, raw), "随机 dedupe 结果异常, n=" + n);
			check(Arrays.equals(dedup, PlacementPolicy.dedupe(dedup)), "随机 dedupe 非幂等, n=" + n);
		}
		group("random stress (20000 rounds)", before);
	}

	public static void main(String[] args)
	{
		System.out.println("=== PlacementPolicy 多输入验证（裸 JDK，无 MC 依赖）===");
		int failuresAtStart = failures;
		testSides();
		testMeta();
		testAdmission();
		testAccepts();
		testAttempt();
		testDedupe();
		testStability();
		testClickCoord();
		testRandomStress();
		System.out.println("----------------------------------------------------------");
		System.out.println("断言总数: " + assertions + "   失败: " + failures);
		if (failures != failuresAtStart || failures > 0)
		{
			System.out.println("RESULT: FAILED");
			System.exit(1);
		}
		System.out.println("RESULT: PASS");
	}
}
