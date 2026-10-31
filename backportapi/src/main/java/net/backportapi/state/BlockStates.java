package net.backportapi.state;

import net.minecraft.block.Block;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.IBlockState;

/**
 * {@link IBlockState} 与 1.7.10 的 (Block, meta) 之间的转换入口。
 *
 * 对应 1.8 的 Block.getStateFromMeta / getMetaFromState / getStateById / getStateId。
 */
public final class BlockStates
{
	private BlockStates()
	{
	}

	public static IBlockState of(Block block, int meta)
	{
		return BlockState.of(block, meta);
	}

	public static IBlockState getStateFromMeta(Block block, int meta)
	{
		return BlockState.of(block, meta);
	}

	public static int getMetaFromState(IBlockState state)
	{
		return state == null ? 0 : state.getMeta();
	}

	public static IBlockState getStateById(int stateId)
	{
		return BlockState.byStateId(stateId);
	}

	public static int getStateId(IBlockState state)
	{
		return state == null ? 0 : state.getStateId();
	}

	public static IBlockState getDefaultState(Block block)
	{
		return BlockState.of(block, 0);
	}
}
