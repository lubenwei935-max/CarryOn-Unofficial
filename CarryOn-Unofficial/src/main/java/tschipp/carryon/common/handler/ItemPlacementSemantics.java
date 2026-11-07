package tschipp.carryon.common.handler;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.World;

import tschipp.carryon.CarryOn;
import tschipp.carryon.common.config.Configs;
import tschipp.carryon.common.item.ItemTile;

/**
 * 通用的「放置语义推断」。
 *
 * <h3>解决的问题</h3>
 * 1.7.10 没有属性系统，朝向由每个方块家族自己编码进 metadata；而大量模组把编码
 * 逻辑写在独立 Item 的 {@code onItemUse} 里（放置瞬间按玩家 yaw 算出 meta），
 * Block / TileEntity / 假 IBlockState 里都不含任何朝向信息。CarryOn 直接
 * {@code World.setBlock} 写世界，绕过了物品层，于是只能把搬起时的旧 meta 原样放回。
 *
 * <h3>做法</h3>
 * 不登记任何 modid、类名表或 yaw→meta 数值表，而是让模组自己的放置 Item 在
 * <b>受控探针</b>里跑一次：先对目标格及其六邻居做完整快照（方块 / meta / TE NBT），
 * 用影子 ItemStack 调 {@code onItemUse}，读它写出的 meta，随后无条件回滚快照。
 * 推断结果只作为「原放置路径的 meta 输入」，世界的最终状态仍由 CarryOn 原有流程产生，
 * 因此 TE 数据、通知、网络包、后续回调全部保持原样。
 *
 * <h3>为什么是通用的</h3>
 * 候选物品来自四路公开 API（NBT 记录的物品形态、{@code getItemDropped}、
 * {@code getItemFromBlock}），准入靠「{@code onItemUse} 是否被自定义实现」这一
 * 行为特征，成功与否靠「目标格是否真的变成了被搬运的方块」这一状态事实。
 * 标准 ItemBlock、固定 meta 型物品、探针抛异常、放错位置、非法 meta 都会自动
 * 退回原路径，不需要任何白名单。
 */
public final class ItemPlacementSemantics
{
	private static final Random RANDOM = new Random();

	/** 目标格 + 六个邻居。模组放置双格方块 / 写相邻方块时也能完整回滚。 */
	private static final int[][] OFFSETS = new int[][] {
		{ 0, 0, 0 }, { -1, 0, 0 }, { 1, 0, 0 },
		{ 0, -1, 0 }, { 0, 1, 0 }, { 0, 0, -1 }, { 0, 0, 1 }
	};

	/** 反射分类结果按 Item 类缓存；签名匹配与 SRG 方法名无关，缓存长期有效。 */
	private static final Map<Class<?>, Boolean> CUSTOM_PLACEMENT = new ConcurrentHashMap<Class<?>, Boolean>();

	private static final float HIT = 0.5F;

	/** 可复现性检验的额外采样次数（不含首次探针）。 */
	private static final int CONFIRMATION_PROBES = 2;

	private ItemPlacementSemantics()
	{
	}

	/**
	 * 推断该方块在「当前玩家朝向」下应有的 metadata。
	 *
	 * @return 合法且与 {@code originalMeta} 不同的 meta；无可用结果时返回
	 *         {@link PlacementPolicy#NO_META}，调用方保持原路径不变。
	 */
	public static int inferPlacementMeta(ItemStack carried, Block block, int originalMeta,
			EntityPlayer player, World world, int x, int y, int z, int clickedSide)
	{
		if (carried == null || block == null || player == null || world == null)
			return PlacementPolicy.NO_META;

		boolean enabled = Configs.Settings.itemPlacementSemantics;
		boolean serverSide = !world.isRemote;
		List<Item> candidates = (enabled && serverSide)
				? collectCandidates(carried, block, originalMeta)
				: Collections.<Item>emptyList();

		if (!PlacementPolicy.shouldAttempt(enabled, serverSide, !candidates.isEmpty()))
			return PlacementPolicy.NO_META;

		int[] order = PlacementPolicy.sideOrder(clickedSide);
		Cell[] snapshot = capture(world, x, y, z);

		try
		{
			for (int i = 0; i < candidates.size(); i++)
			{
				Item item = candidates.get(i);
				if (!hasCustomPlacement(item))
					continue;

				for (int s = 0; s < order.length; s++)
				{
					int side = order[s];
					ProbeResult first = probe(item, block, originalMeta, player, world,
							x, y, z, side, snapshot);

					if (!PlacementPolicy.accepts(originalMeta, first.meta,
							first.blockMatches, first.executed))
						continue;

					// 可复现性复核：重复采样，只有每次都写出同一个 meta 才采纳。
					// 用随机数挑变体的物品会在这里被排除，不会把随机值当成朝向。
					int[] samples = new int[1 + CONFIRMATION_PROBES];
					samples[0] = first.meta;
					for (int c = 0; c < CONFIRMATION_PROBES; c++)
					{
						ProbeResult confirm = probe(item, block, originalMeta, player, world,
								x, y, z, side, snapshot);
						samples[1 + c] = confirm.meta;
						if (confirm.meta != first.meta)
							break;
					}
					if (!PlacementPolicy.isStable(samples))
						continue;

					CarryOn.LOGGER.info("[CarryOn] placement semantics "
							+ Block.blockRegistry.getNameForObject(block)
							+ " meta=" + originalMeta + "->" + first.meta
							+ " side=" + side
							+ " item=" + Item.itemRegistry.getNameForObject(item));
					return first.meta;
				}
			}
		}
		finally
		{
			// 任何逃逸路径都回到放置前的世界状态。
			restore(world, x, y, z, snapshot);
		}

		return PlacementPolicy.NO_META;
	}

	/** 单次受控探针：跑一次 {@code onItemUse}，读世界状态，无条件回滚快照。 */
	private static ProbeResult probe(Item item, Block block, int originalMeta,
			EntityPlayer player, World world, int x, int y, int z, int side, Cell[] snapshot)
	{
		ProbeResult result = new ProbeResult();
		EnumFacing facing = EnumFacing.getFront(side);
		int cx = PlacementPolicy.clickCoord(x, facing.getFrontOffsetX());
		int cy = PlacementPolicy.clickCoord(y, facing.getFrontOffsetY());
		int cz = PlacementPolicy.clickCoord(z, facing.getFrontOffsetZ());
		ItemStack shadow = new ItemStack(item, 1, PlacementPolicy.normalizeMeta(originalMeta));

		try
		{
			// 恒返回 true 的物品很常见（放置成功与否不看返回值），
			// 因此只以世界状态作为事实来源。
			item.onItemUse(shadow, player, world, cx, cy, cz, side, HIT, HIT, HIT);
			result.executed = true;
			result.blockMatches = world.getBlock(x, y, z) == block;
			if (result.blockMatches)
				result.meta = world.getBlockMetadata(x, y, z);
		}
		catch (Throwable t)
		{
			result.executed = false;
		}
		finally
		{
			restore(world, x, y, z, snapshot);
		}
		return result;
	}

	private static final class ProbeResult
	{
		int meta = PlacementPolicy.NO_META;
		boolean executed;
		boolean blockMatches;
	}

	/** 四路候选，去重后保持优先级顺序；任何一路抛异常都只是少一个候选。 */
	private static List<Item> collectCandidates(ItemStack carried, Block block, int meta)
	{
		int[] ids = new int[3];
		ids[0] = idOf(formItem(carried));
		ids[1] = idOf(droppedItem(block, meta));
		ids[2] = idOf(itemFromBlock(block));

		int[] unique = PlacementPolicy.dedupe(ids);
		List<Item> out = new ArrayList<Item>(unique.length);
		for (int i = 0; i < unique.length; i++)
		{
			Item item = Item.getItemById(unique[i]);
			if (item != null && !out.contains(item))
				out.add(item);
		}
		return out;
	}

	private static int idOf(Item item)
	{
		if (item == null)
			return -1;
		try
		{
			return Item.getIdFromItem(item);
		}
		catch (Throwable t)
		{
			return -1;
		}
	}

	private static Item formItem(ItemStack carried)
	{
		try
		{
			ItemStack form = ItemTile.getItemFormStack(carried);
			return form == null ? null : form.getItem();
		}
		catch (Throwable t)
		{
			return null;
		}
	}

	private static Item droppedItem(Block block, int meta)
	{
		try
		{
			return block.getItemDropped(PlacementPolicy.normalizeMeta(meta), RANDOM, 0);
		}
		catch (Throwable t)
		{
			return null;
		}
	}

	private static Item itemFromBlock(Block block)
	{
		try
		{
			return Item.getItemFromBlock(block);
		}
		catch (Throwable t)
		{
			return null;
		}
	}

	/**
	 * 按<b>方法签名</b>判定 {@code onItemUse} 是否被自定义实现。
	 *
	 * <p>不能按方法名查：产物经过 SpecialSource 重混淆，运行时叫 {@code func_77648_a}，
	 * 而字符串常量不会被改写。签名（10 个参数、返回 boolean、类型序列）在混淆前后
	 * 完全一致，因此这里逐个类向上扫 {@code getDeclaredMethods()}。
	 */
	private static boolean hasCustomPlacement(Item item)
	{
		Class<?> type = item.getClass();
		Boolean cached = CUSTOM_PLACEMENT.get(type);
		if (cached != null)
			return cached.booleanValue();

		boolean resolved = false;
		boolean byItem = false;
		boolean byItemBlock = false;

		Class<?> current = type;
		while (current != null && current != Object.class)
		{
			Method[] methods;
			try
			{
				methods = current.getDeclaredMethods();
			}
			catch (Throwable t)
			{
				break;
			}

			boolean found = false;
			for (int i = 0; i < methods.length; i++)
			{
				Method m = methods[i];
				if (Modifier.isStatic(m.getModifiers()) || m.getReturnType() != boolean.class)
					continue;

				Class<?>[] p = m.getParameterTypes();
				if (p.length != 10
						|| p[0] != ItemStack.class || p[1] != EntityPlayer.class || p[2] != World.class
						|| p[7] != float.class || p[8] != float.class || p[9] != float.class)
					continue;

				boolean intsMatch = true;
				for (int k = 3; k <= 6; k++)
				{
					if (p[k] != int.class)
					{
						intsMatch = false;
						break;
					}
				}
				if (!intsMatch)
					continue;

				found = true;
				break;
			}

			if (found)
			{
				resolved = true;
				byItem = current == Item.class;
				byItemBlock = current == ItemBlock.class;
				break;
			}
			if (current == Item.class)
				break;
			current = current.getSuperclass();
		}

		boolean result = PlacementPolicy.hasCustomPlacement(resolved, byItem, byItemBlock);
		CUSTOM_PLACEMENT.put(type, Boolean.valueOf(result));
		return result;
	}

	private static final class Cell
	{
		Block block;
		int meta;
		NBTTagCompound tile;
	}

	private static Cell[] capture(World world, int x, int y, int z)
	{
		Cell[] cells = new Cell[OFFSETS.length];
		for (int i = 0; i < OFFSETS.length; i++)
		{
			int cx = x + OFFSETS[i][0];
			int cy = y + OFFSETS[i][1];
			int cz = z + OFFSETS[i][2];

			Cell cell = new Cell();
			try
			{
				cell.block = world.getBlock(cx, cy, cz);
				cell.meta = world.getBlockMetadata(cx, cy, cz);
				TileEntity tile = world.getTileEntity(cx, cy, cz);
				if (tile != null)
				{
					NBTTagCompound nbt = new NBTTagCompound();
					tile.writeToNBT(nbt);
					cell.tile = nbt;
				}
			}
			catch (Throwable t)
			{
				cell.block = Blocks.air;
				cell.meta = 0;
				cell.tile = null;
			}
			cells[i] = cell;
		}
		return cells;
	}

	/** 完整回滚：只重写确实发生变化的格子，避免误伤邻居的 TileEntity。 */
	private static void restore(World world, int x, int y, int z, Cell[] cells)
	{
		for (int i = 0; i < cells.length; i++)
		{
			Cell cell = cells[i];
			int cx = x + OFFSETS[i][0];
			int cy = y + OFFSETS[i][1];
			int cz = z + OFFSETS[i][2];

			try
			{
				TileEntity currentTile = world.getTileEntity(cx, cy, cz);
				boolean tileMatches = cell.tile == null ? currentTile == null : currentTile != null;
				if (world.getBlock(cx, cy, cz) == cell.block
						&& world.getBlockMetadata(cx, cy, cz) == cell.meta
						&& tileMatches)
					continue;

				if (cell.block == null || cell.block == Blocks.air)
				{
					world.setBlockToAir(cx, cy, cz);
					world.removeTileEntity(cx, cy, cz);
				}
				else
				{
					world.setBlock(cx, cy, cz, cell.block, cell.meta, 3);
					// onBlockAdded 可能又改了一次 meta（原版箱子就是），补写回快照值。
					if (world.getBlockMetadata(cx, cy, cz) != cell.meta)
						world.setBlockMetadataWithNotify(cx, cy, cz, cell.meta, 3);
				}

				if (cell.tile != null)
				{
					TileEntity tile = world.getTileEntity(cx, cy, cz);
					if (tile != null)
					{
						tile.readFromNBT(cell.tile);
						tile.xCoord = cx;
						tile.yCoord = cy;
						tile.zCoord = cz;
					}
				}
				else
				{
					world.removeTileEntity(cx, cy, cz);
				}
			}
			catch (Throwable t)
			{
				// 单格恢复失败不影响其余格子；外层 finally 仍会再试一次。
			}
		}
	}
}
