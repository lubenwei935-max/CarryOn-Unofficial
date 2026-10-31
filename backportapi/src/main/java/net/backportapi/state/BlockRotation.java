package net.backportapi.state;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.block.BlockChest;
import net.minecraft.block.BlockFurnace;
import net.minecraft.block.BlockLog;
import net.minecraft.block.BlockPistonBase;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.BlockTorch;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;

/**
 * 方块朝向 ↔ metadata 的换算表。
 *
 * <h3>为什么需要这个</h3>
 * 1.8+ 用 {@code IProperty<EnumFacing>} 表达朝向，可以用
 * {@code state.withProperty(FACING, dir)} 通用地旋转任意方块。
 * 1.7.10 没有属性系统，朝向被硬编码在 4 bit metadata 的某几个位段里，
 * 而且每个方块家族的编码规则都不一样（楼梯、箱子、熔炉、南瓜、原木、活塞、火把各不相同）。
 *
 * <h3>用法</h3>
 * 内置常见原版方块的规则；模组方块可以用 {@link #register(Block, Rotator)} 追加。
 * 未注册的方块 {@link #getRotator(Block)} 返回 null，调用方应退回"按原 meta 放置"。
 */
public final class BlockRotation
{
	/** 单个方块家族的朝向编解码器。 */
	public interface Rotator
	{
		/** 从 meta 解出朝向；无法判断时返回 null。 */
		EnumFacing getFacing(Block block, int meta);

		/** 把 meta 改写为朝向 facing，保留其余位。 */
		int rotateTo(Block block, int meta, EnumFacing facing);

		/** 该方块允许的朝向集合，用于构造 PropertyDirection。 */
		Collection<EnumFacing> getAllowedFacings(Block block);
	}

	private static final Map<Block, Rotator> REGISTRY = new HashMap<Block, Rotator>();
	private static final Map<Block, Rotator> CACHE = new HashMap<Block, Rotator>();

	/**
	 * 按"朝向 -> meta 值"显式映射的通用实现。
	 * mask 指 meta 中用于朝向的位段。
	 */
	public static class FacingMeta implements Rotator
	{
		private final Map<EnumFacing, Integer> metaByFacing = new LinkedHashMap<EnumFacing, Integer>();
		private int mask;

		public FacingMeta(int mask)
		{
			this.mask = mask;
		}

		/**
		 * 登记一个"朝向 -> meta"映射。
		 *
		 * <p>掩码必须覆盖所有写入值，否则 {@link #rotateTo} 里的 {@code value & mask}
		 * 会把高位截掉，写进一个方块根本不认识的 meta。
		 * 1.7.10 的箱子/熔炉 meta 是 2~5（需要 3 位），当初按 0~3 写了掩码 3，
		 * 西/东就被截成 0/1，客户端渲染时落进 default 分支（0°），
		 * 表现就是"放下后永远朝南"。
		 *
		 * <p>这里按登记值自动加宽掩码，避免同一类错误再次静默生效。
		 */
		public FacingMeta put(EnumFacing facing, int metaValue)
		{
			if ((metaValue & ~this.mask) != 0)
				this.mask |= metaValue;
			this.metaByFacing.put(facing, Integer.valueOf(metaValue));
			return this;
		}

		/** 当前使用的 meta 位段掩码（自动加宽后的结果）。 */
		public int getMask()
		{
			return this.mask;
		}

		@Override
		public EnumFacing getFacing(Block block, int meta)
		{
			int value = meta & this.mask;
			for (Map.Entry<EnumFacing, Integer> e : this.metaByFacing.entrySet())
			{
				if (e.getValue().intValue() == value)
					return e.getKey();
			}
			return null;
		}

		@Override
		public int rotateTo(Block block, int meta, EnumFacing facing)
		{
			Integer value = this.metaByFacing.get(facing);
			if (value == null)
				return meta;
			return (meta & ~this.mask) | (value.intValue() & this.mask);
		}

		@Override
		public Collection<EnumFacing> getAllowedFacings(Block block)
		{
			return Collections.unmodifiableSet(this.metaByFacing.keySet());
		}
	}

	/** 楼梯：meta 低 2 位是朝向，bit2 是倒置。 */
	public static final Rotator STAIRS = new FacingMeta(3).put(EnumFacing.EAST, 0).put(EnumFacing.WEST, 1).put(EnumFacing.SOUTH, 2).put(EnumFacing.NORTH, 3);

	/**
	 * 箱子：meta 2/3/4/5 = 北/南/西/东（正面朝向）。
	 *
	 * <p><b>注意 1</b>：1.7.10 里箱子/熔炉的 meta 是 2~5，<b>不是</b> 0~3。
	 * 所以掩码必须是 <b>7</b>（3 位），不能是 3：写 3 时 {@code 4 & 3 = 0}、
	 * {@code 5 & 3 = 1}，会得到 0/1 这两个非法值。
	 *
	 * <p><b>注意 2</b>：{@code TileEntityChestRenderer} 的朝向表只有 2/3/4/5 四个分支，
	 * 0/1 会落进 default（旋转 0°）——而 0° 对应的正面是 <b>+Z = 南</b>。
	 * 这就是"放下后正面永远朝南"的真正原因。
	 *
	 * <p>meta 与正面朝向的对应关系来自 {@code BlockChest.onBlockPlacedBy}：
	 * yaw 0（玩家朝南）写 meta 2 → 正面朝北（对着玩家），依次类推。
	 */
	public static final Rotator CHEST = new FacingMeta(7).put(EnumFacing.NORTH, 2).put(EnumFacing.SOUTH, 3).put(EnumFacing.WEST, 4).put(EnumFacing.EAST, 5);

	/** 熔炉（含点燃态）：与箱子同规则（meta 2~5，掩码 7）。 */
	public static final Rotator FURNACE = new FacingMeta(7).put(EnumFacing.NORTH, 2).put(EnumFacing.SOUTH, 3).put(EnumFacing.WEST, 4).put(EnumFacing.EAST, 5);

	/** 南瓜/南瓜灯：meta 0/1/2/3 = 南/西/北/东。 */
	public static final Rotator PUMPKIN = new FacingMeta(3).put(EnumFacing.SOUTH, 0).put(EnumFacing.WEST, 1).put(EnumFacing.NORTH, 2).put(EnumFacing.EAST, 3);

	/** 活塞：meta 低 3 位。 */
	public static final Rotator PISTON = new FacingMeta(7).put(EnumFacing.DOWN, 0).put(EnumFacing.UP, 1).put(EnumFacing.NORTH, 2).put(EnumFacing.SOUTH, 3).put(EnumFacing.WEST, 4).put(EnumFacing.EAST, 5);

	/** 火把：meta 1..5 = 东/西/南/北/上（6 为地面）。 */
	public static final Rotator TORCH = new FacingMeta(7).put(EnumFacing.EAST, 1).put(EnumFacing.WEST, 2).put(EnumFacing.SOUTH, 3).put(EnumFacing.NORTH, 4).put(EnumFacing.UP, 5);

	/** 原木：meta 低 2 位是树种，bit2-3 是轴向（0=Y, 4=X, 8=Z）。 */
	public static final Rotator LOG = new Rotator()
	{
		@Override
		public EnumFacing getFacing(Block block, int meta)
		{
			switch (meta & 12)
			{
			case 4:
				return EnumFacing.EAST;
			case 8:
				return EnumFacing.SOUTH;
			default:
				return EnumFacing.UP;
			}
		}

		@Override
		public int rotateTo(Block block, int meta, EnumFacing facing)
		{
			int axis;
			if (facing == EnumFacing.UP || facing == EnumFacing.DOWN)
				axis = 0;
			else if (facing == EnumFacing.EAST || facing == EnumFacing.WEST)
				axis = 4;
			else
				axis = 8;
			return (meta & 3) | axis;
		}

		@Override
		public Collection<EnumFacing> getAllowedFacings(Block block)
		{
			return Collections.unmodifiableList(java.util.Arrays.asList(EnumFacing.UP, EnumFacing.EAST, EnumFacing.SOUTH));
		}
	};

	static
	{
		REGISTRY.put(Blocks.pumpkin, PUMPKIN);
		REGISTRY.put(Blocks.lit_pumpkin, PUMPKIN);
	}

	private BlockRotation()
	{
	}

	/** 注册/覆盖某个方块的旋转规则。 */
	public static void register(Block block, Rotator rotator)
	{
		if (block == null)
			return;
		REGISTRY.put(block, rotator);
		CACHE.remove(block);
	}

	/**
	 * 取得方块的旋转规则。
	 * 先查显式注册表，再按类型兜底（覆盖全部楼梯/原木等子类），最后返回 null。
	 */
	public static Rotator getRotator(Block block)
	{
		if (block == null)
			return null;

		if (CACHE.containsKey(block))
			return CACHE.get(block);

		Rotator rotator = REGISTRY.get(block);

		if (rotator == null)
		{
			if (block instanceof BlockStairs)
				rotator = STAIRS;
			else if (block instanceof BlockLog)
				rotator = LOG;
			else if (block instanceof BlockChest)
				rotator = CHEST;
			else if (block instanceof BlockFurnace)
				rotator = FURNACE;
			else if (block instanceof BlockPistonBase)
				rotator = PISTON;
			else if (block instanceof BlockTorch)
				rotator = TORCH;
		}

		CACHE.put(block, rotator);
		return rotator;
	}

	public static boolean isRotatable(Block block)
	{
		return getRotator(block) != null;
	}

	/** 把 meta 旋转到朝向 facing；无规则时原样返回。 */
	public static int rotateTo(Block block, int meta, EnumFacing facing)
	{
		Rotator rotator = getRotator(block);
		return rotator == null ? meta : rotator.rotateTo(block, meta, facing);
	}

	/** 从 meta 解出朝向；无规则时返回 null。 */
	public static EnumFacing getFacing(Block block, int meta)
	{
		Rotator rotator = getRotator(block);
		return rotator == null ? null : rotator.getFacing(block, meta);
	}
}
