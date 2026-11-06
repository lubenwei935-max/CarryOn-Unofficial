package tschipp.carryon.common.handler;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import tschipp.carryon.CarryOn;

/**
 * 「放置后朝向修正」的分派表。
 *
 * <p>解决的问题：一批方块的朝向不在 metadata 里，而在 TileEntity 的字段里。
 * CarryOn 走的是 {@code setBlockState → onBlockPlacedBy → readFromNBT}，
 * 这些方块的朝向要么根本没人设（林业），要么设好之后被 NBT 冲掉（神秘时代）。
 * 详见 {@link IPostPlaceRotator}。
 *
 * <p><b>分派顺序是固定的</b>（改动会改变行为，别动）：
 * <ol>
 *   <li>先按注册名：{@code modid:name;meta} → {@code modid:name}；</li>
 *   <li>再按 TE 类型：沿 {@code te.getClass()} 的父类链比类名字符串，
 *       命中 {@link #BY_TE_TYPE} 里第一条返回 true 的适配器；</li>
 *   <li>都没命中 → 返回 false，调用方什么都不做（这是"零回归"的保证）。</li>
 * </ol>
 *
 * <p>全程 {@code try/catch (Throwable)}：反射失败只记一次 WARN，
 * 行为退回修复前，<b>绝不把异常抛给玩家侧</b>。
 *
 * <p>所有跨 mod 访问都用类名字符串 + 反射，本类不 import 任何
 * {@code forestry.*} / {@code thaumcraft.*}，目标 mod 缺席时不影响加载。
 */
public final class PostPlaceRotators
{
	/** key = "modid:name" 或 "modid:name;meta"。 */
	private static final Map<String, IPostPlaceRotator> BY_NAME = new HashMap<String, IPostPlaceRotator>();

	/** 按注册顺序匹配，先于 BY_NAME 兜底；只放内置适配器。 */
	private static final List<IPostPlaceRotator> BY_TE_TYPE = new ArrayList<IPostPlaceRotator>();

	/** 已经 WARN 过的 key，保证每类失败只刷一行日志。 */
	private static final Set<String> WARNED = new HashSet<String>();

	static
	{
		BY_TE_TYPE.add(new ForestryRotator());
		BY_TE_TYPE.add(new AlembicRotator());
	}

	private PostPlaceRotators()
	{
	}

	/** 供内置与后续扩展使用。key 形如 {@code forestry:something} 或 {@code modid:name;3}。 */
	public static void register(String key, IPostPlaceRotator rotator)
	{
		if (key == null || rotator == null)
			return;

		BY_NAME.put(key, rotator);
	}

	/**
	 * 尝试修正放置后的朝向。
	 *
	 * <p>只应在服务端、且 {@code tile.readFromNBT(...)} 与坐标修正都完成之后调用。
	 *
	 * @return true 表示有适配器接管了该方块（调用方需要补一次 {@code markBlockForUpdate}）
	 */
	public static boolean apply(World world, int x, int y, int z, Block block, int meta, EntityPlayer player)
	{
		if (world == null || block == null || player == null)
			return false;

		String name = null;
		try
		{
			name = Block.blockRegistry.getNameForObject(block);

			if (name != null)
			{
				IPostPlaceRotator rotator = BY_NAME.get(name + ";" + meta);
				if (rotator == null)
					rotator = BY_NAME.get(name);

				// 返回 false 表示"不是我的方块"，继续往下交给 TE 类型匹配。
				if (rotator != null && rotator.rotate(world, x, y, z, block, meta, player))
					return true;
			}

			TileEntity tile = world.getTileEntity(x, y, z);
			if (tile != null)
			{
				for (int i = 0; i < BY_TE_TYPE.size(); i++)
				{
					if (BY_TE_TYPE.get(i).rotate(world, x, y, z, block, meta, player))
						return true;
				}
			}
		}
		catch (Throwable t)
		{
			warnOnce("apply:" + name, "post-place rotation failed for " + name + " (" + t + ")");
		}

		return false;
	}

	/** 每类失败只留一行 WARN；第二次起静默。 */
	private static void warnOnce(String key, String message)
	{
		if (WARNED.add(key))
			CarryOn.LOGGER.warn("[CarryOn] " + message);
	}

	/** {@code cls} 的父类链（含自身）里是否出现 {@code target} 这个类名。 */
	private static boolean extendsByName(Class<?> cls, String target)
	{
		for (Class<?> c = cls; c != null; c = c.getSuperclass())
		{
			if (target.equals(c.getName()))
				return true;
		}
		return false;
	}

	/** 取 public 方法；不存在时返回 null（不是异常，是"这条路径不适用"）。 */
	private static Method findMethod(Class<?> cls, String name, Class<?>... params)
	{
		try
		{
			Method m = cls.getMethod(name, params);
			m.setAccessible(true);
			return m;
		}
		catch (Throwable t)
		{
			return null;
		}
	}

	/** 按玩家朝向取 0~3 的象限。公式与林业 / 神秘时代原生实现逐字节一致。 */
	private static int yawQuadrant(EntityPlayer player)
	{
		return MathHelper.floor_double(player.rotationYaw * 4.0F / 360.0F + 0.5D) & 3;
	}

	// ------------------------------------------------------------------
	// 适配器 A：林业
	// ------------------------------------------------------------------

	/**
	 * 林业：{@code TileForestry} 及其全部子类（Apiarist's / Arborist's /
	 * Lepidopterist's Chest，以及各种林业机器）。
	 *
	 * <p>原生路径是 {@code ItemBlockForestry.placeBlockAt} →
	 * {@code TileForestry.rotateAfterPlacement(player, meta)}；CarryOn 直接写世界，
	 * 永远不经过 ItemBlock，所以这里补调同一个方法。
	 *
	 * <p>用 {@code setOrientation} 而不是直写字段：它内部会
	 * {@code setNeedsNetworkUpdate()} + 标脏，客户端同步是林业自带的。
	 */
	private static final class ForestryRotator implements IPostPlaceRotator
	{
		private static final String TILE_FORESTRY = "forestry.core.tiles.TileForestry";

		private static final ForgeDirection[] HORIZONTALS = new ForgeDirection[] {
				ForgeDirection.NORTH, ForgeDirection.EAST, ForgeDirection.SOUTH, ForgeDirection.WEST };

		/** 方法按 Class 缓存，value 为 null 表示"这类没有该方法"（用 containsKey 区分未缓存）。 */
		private static final Map<Class<?>, Method> ROTATE_AFTER_PLACEMENT = new HashMap<Class<?>, Method>();
		private static final Map<Class<?>, Method> SET_ORIENTATION = new HashMap<Class<?>, Method>();

		@Override
		public boolean rotate(World world, int x, int y, int z, Block block, int meta, EntityPlayer player)
		{
			TileEntity tile = world.getTileEntity(x, y, z);
			if (tile == null || !extendsByName(tile.getClass(), TILE_FORESTRY))
				return false;

			Class<?> cls = tile.getClass();
			try
			{
				Method rotate = ROTATE_AFTER_PLACEMENT.get(cls);
				if (rotate == null && !ROTATE_AFTER_PLACEMENT.containsKey(cls))
				{
					rotate = findMethod(cls, "rotateAfterPlacement", EntityPlayer.class, int.class);
					ROTATE_AFTER_PLACEMENT.put(cls, rotate);
				}

				if (rotate != null)
				{
					// 完全复刻 ItemBlockForestry 的行为；meta 林业忽略，但签名要匹配。
					rotate.invoke(tile, player, Integer.valueOf(meta));
					return true;
				}

				// 兜底：老版本林业没有 rotateAfterPlacement 时自己算朝向。
				Method set = SET_ORIENTATION.get(cls);
				if (set == null && !SET_ORIENTATION.containsKey(cls))
				{
					set = findMethod(cls, "setOrientation", ForgeDirection.class);
					SET_ORIENTATION.put(cls, set);
				}

				if (set == null)
					return false;

				set.invoke(tile, HORIZONTALS[yawQuadrant(player)]);
				return true;
			}
			catch (Throwable t)
			{
				warnOnce("forestry:" + cls.getName(), "forestry rotation failed for " + cls.getName() + " (" + t + ")");
				return false;
			}
		}
	}

	// ------------------------------------------------------------------
	// 适配器 B：神秘时代蒸馏器
	// ------------------------------------------------------------------

	/**
	 * 神秘时代：{@code TileAlembic}（Arcane Alembic，{@code blockMetalDevice} meta 1）。
	 *
	 * <p>原生逻辑在 {@code BlockMetalDevice.func_149689_a}（{@code onBlockPlacedBy}），
	 * 它其实已经把 {@code facing} 算对了——但紧接着 {@code TileAlembic.readCustomNBT}
	 * 又把 NBT 里的旧 {@code facing} 读回来，把它冲掉。
	 *
	 * <p>{@code facing} 不只是外观：{@code canOutputTo} / {@code isConnectable} 直接用它
	 * 推导导流方向，所以必须一起改对。<b>公式照抄原生实现</b>（0→2 北，1→5 东，
	 * 2→3 南，3→4 西），不要自创映射。
	 *
	 * <p>用类名精确匹配而不是 {@code instanceof}：{@code TileAlembic} 只会挂在这一个方块上，
	 * 精确匹配已经比 (block, meta) 更严格，且不必再去比注册名（林业那种大写开头的
	 * {@code Forestry:xxx} 说明注册名大小写不可假定）。
	 */
	private static final class AlembicRotator implements IPostPlaceRotator
	{
		private static final String TILE_ALEMBIC = "thaumcraft.common.tiles.TileAlembic";

		private static final Map<Class<?>, Field> FACING = new HashMap<Class<?>, Field>();
		private static final Map<Class<?>, Field> FD = new HashMap<Class<?>, Field>();

		@Override
		public boolean rotate(World world, int x, int y, int z, Block block, int meta, EntityPlayer player)
		{
			TileEntity tile = world.getTileEntity(x, y, z);
			if (tile == null || !TILE_ALEMBIC.equals(tile.getClass().getName()))
				return false;

			Class<?> cls = tile.getClass();
			try
			{
				Field facing = FACING.get(cls);
				if (facing == null)
				{
					facing = cls.getField("facing"); // public int
					facing.setAccessible(true);
					FACING.put(cls, facing);
				}

				int quadrant = yawQuadrant(player);
				int value = (quadrant == 0) ? 2 : (quadrant == 1) ? 5 : (quadrant == 2) ? 3 : 4;
				facing.setInt(tile, value);

				// fd 是 readCustomNBT 由旧 facing 推出来的缓存值。本版 TileAlembic 内部
				// 没有读它（导流判断直接用 facing），但别的代码可能读——顺手同步，
				// 失败也不算错误，所以单独 try 掉、不 WARN。
				try
				{
					Field fd = FD.get(cls);
					if (fd == null)
					{
						fd = cls.getDeclaredField("fd");
						fd.setAccessible(true);
						FD.put(cls, fd);
					}
					fd.set(tile, ForgeDirection.getOrientation(value));
				}
				catch (Throwable ignored)
				{
				}

				return true;
			}
			catch (Throwable t)
			{
				warnOnce("alembic:" + cls.getName(), "alembic rotation failed for " + cls.getName() + " (" + t + ")");
				return false;
			}
		}
	}
}
