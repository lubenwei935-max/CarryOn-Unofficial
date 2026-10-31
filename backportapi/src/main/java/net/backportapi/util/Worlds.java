package net.backportapi.util;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/**
 * 1.8 的 World/BlockPos 风格方块读写 API 到 1.7.10 int 三元参数的桥。
 *
 * <p>1.7.10 的 {@code World} 无法被注入新方法（没有 mixin 时），
 * 所以移植时把 {@code world.getBlockState(pos)} 改写成
 * {@code Worlds.getBlockState(world, pos)} 即可，其余语义保持一致。
 */
public final class Worlds
{
	private Worlds()
	{
	}

	// ---------- 读 ----------

	public static IBlockState getBlockState(IBlockAccess world, BlockPos pos)
	{
		return getBlockState(world, pos.getX(), pos.getY(), pos.getZ());
	}

	public static IBlockState getBlockState(IBlockAccess world, int x, int y, int z)
	{
		Block block = world.getBlock(x, y, z);
		return net.minecraft.block.state.BlockState.of(block, world.getBlockMetadata(x, y, z));
	}

	public static Block getBlock(IBlockAccess world, BlockPos pos)
	{
		return world.getBlock(pos.getX(), pos.getY(), pos.getZ());
	}

	public static int getMeta(IBlockAccess world, BlockPos pos)
	{
		return world.getBlockMetadata(pos.getX(), pos.getY(), pos.getZ());
	}

	public static TileEntity getTileEntity(IBlockAccess world, BlockPos pos)
	{
		return world.getTileEntity(pos.getX(), pos.getY(), pos.getZ());
	}

	public static boolean isAirBlock(IBlockAccess world, BlockPos pos)
	{
		return world.isAirBlock(pos.getX(), pos.getY(), pos.getZ());
	}

	public static boolean isBlockLoaded(World world, BlockPos pos)
	{
		return world.blockExists(pos.getX(), pos.getY(), pos.getZ());
	}

	/** 1.8 的 getCombinedLight；1.7.10 叫 getLightBrightnessForSkyBlocks。 */
	public static int getCombinedLight(World world, BlockPos pos, int lightValue)
	{
		return world.getLightBrightnessForSkyBlocks(pos.getX(), pos.getY(), pos.getZ(), lightValue);
	}

	// ---------- 写 ----------

	public static boolean setBlockState(World world, BlockPos pos, IBlockState state)
	{
		return setBlockState(world, pos, state, 3);
	}

	public static boolean setBlockState(World world, BlockPos pos, IBlockState state, int flags)
	{
		if (state == null)
			return false;
		return world.setBlock(pos.getX(), pos.getY(), pos.getZ(), state.getBlock(), state.getMeta(), flags);
	}

	public static boolean setBlockToAir(World world, BlockPos pos)
	{
		return world.setBlockToAir(pos.getX(), pos.getY(), pos.getZ());
	}

	public static void removeTileEntity(World world, BlockPos pos)
	{
		world.removeTileEntity(pos.getX(), pos.getY(), pos.getZ());
	}

	// ---------- 放置判定 ----------

	/**
	 * 1.8 的 {@code block.isReplaceable(world, pos)}。
	 *
	 * 1.7.10 的 Block 没有 isReplaceable；等价判定在材质层：
	 * {@code block.getMaterial().isReplaceable()}（覆盖空气、水、高草等）。
	 */
	public static boolean isReplaceable(World world, BlockPos pos)
	{
		Block block = world.getBlock(pos.getX(), pos.getY(), pos.getZ());
		return block.getMaterial().isReplaceable();
	}

	/**
	 * 更严格的判定：材质可替换，或该方块本身允许被给定物品替换。
	 * 对应 1.7.10 的 {@code block.canReplace(world, x, y, z, side, stack)}。
	 */
	public static boolean isReplaceable(World world, BlockPos pos, int side, net.minecraft.item.ItemStack stack)
	{
		Block block = world.getBlock(pos.getX(), pos.getY(), pos.getZ());
		if (block.getMaterial().isReplaceable())
			return true;
		return block.canReplace(world, pos.getX(), pos.getY(), pos.getZ(), side, stack);
	}

	public static boolean canPlaceBlockAt(Block block, World world, BlockPos pos)
	{
		return block.canPlaceBlockAt(world, pos.getX(), pos.getY(), pos.getZ());
	}

	/** 1.8 的 canBlockBePlaced；1.7.10 叫 canPlaceEntityOnSide。 */
	public static boolean canBlockBePlaced(World world, Block block, BlockPos pos, boolean skipCollision, int side, net.minecraft.entity.Entity placer, net.minecraft.item.ItemStack stack)
	{
		return world.canPlaceEntityOnSide(block, pos.getX(), pos.getY(), pos.getZ(), skipCollision, side, placer, stack);
	}
}
