package net.minecraft.block.state;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import net.backportapi.state.BlockRotation;
import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

/**
 * {@link IBlockState} 的默认实现：不可变的 (Block, meta) 二元组，按方块实例 + meta 驻留。
 *
 * 驻留保证 {@code of(block, meta) == of(block, meta)}，因此可以安全地用作 Map 键、
 * 也可以用引用相等做比较（与原版 1.8 的 BlockState 行为一致）。
 */
public final class BlockState implements IBlockState
{
	private static final java.util.Map<Long, BlockState> CACHE = new java.util.HashMap<Long, BlockState>();
	private static final java.util.Map<Integer, BlockState> BY_ID = new java.util.HashMap<Integer, BlockState>();

	private final Block block;
	private final int meta;
	private final int stateId;
	private final List<IProperty<?>> properties;

	private BlockState(Block block, int meta, int stateId)
	{
		this.block = block;
		this.meta = meta;
		this.stateId = stateId;

		BlockRotation.Rotator rotator = BlockRotation.getRotator(block);
		if (rotator == null)
		{
			this.properties = Collections.emptyList();
		}
		else
		{
			List<IProperty<?>> list = new ArrayList<IProperty<?>>(1);
			Collection<EnumFacing> allowed = rotator.getAllowedFacings(block);
			list.add(PropertyDirection.create("facing", allowed.toArray(new EnumFacing[allowed.size()])));
			this.properties = Collections.unmodifiableList(list);
		}
	}

	public static BlockState of(Block block, int meta)
	{
		if (block == null)
			block = net.minecraft.init.Blocks.air;

		meta &= 15;
		long key = ((long) Block.getIdFromBlock(block) << 4) | meta;
		BlockState state = CACHE.get(Long.valueOf(key));
		if (state == null)
		{
			state = new BlockState(block, meta, (int) (key & 0x7FFFFFFFL));
			CACHE.put(Long.valueOf(key), state);
			BY_ID.put(Integer.valueOf(state.stateId), state);
		}
		return state;
	}

	/** 按 stateId 反查（对应 1.8 的 Block.getStateById）。 */
	public static BlockState byStateId(int stateId)
	{
		BlockState state = BY_ID.get(Integer.valueOf(stateId));
		if (state != null)
			return state;

		// 未驻留：从 id 反解 (blockId, meta)
		int blockId = (stateId >> 4) & 0xFFF;
		int meta = stateId & 15;
		Block block = Block.getBlockById(blockId);
		return of(block == null ? net.minecraft.init.Blocks.air : block, meta);
	}

	@Override
	public Block getBlock()
	{
		return this.block;
	}

	@Override
	public int getMeta()
	{
		return this.meta;
	}

	@Override
	public int getStateId()
	{
		return this.stateId;
	}

	@Override
	public Collection<IProperty<?>> getPropertyNames()
	{
		return this.properties;
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> T getValue(IProperty<T> property)
	{
		if (property instanceof PropertyDirection)
		{
			EnumFacing facing = BlockRotation.getFacing(this.block, this.meta);
			if (facing != null)
				return (T) facing;
		}
		return null;
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T, V extends T> IBlockState withProperty(IProperty<T> property, V value)
	{
		if (property instanceof PropertyDirection && value instanceof EnumFacing)
		{
			int newMeta = BlockRotation.rotateTo(this.block, this.meta, (EnumFacing) value);
			return newMeta == this.meta ? this : of(this.block, newMeta);
		}
		return this;
	}

	/**
	 * 1.7.10 没有"实际状态"概念，meta 就是实际状态。
	 * 保留此方法只为让 1.8 代码编译；返回值恒为 this。
	 */
	@Override
	public IBlockState getActualState(IBlockAccess world, BlockPos pos)
	{
		return this;
	}

	@Override
	public boolean equals(Object o)
	{
		return this == o;
	}

	@Override
	public int hashCode()
	{
		return this.stateId;
	}

	@Override
	public String toString()
	{
		return "BlockState{" + Block.blockRegistry.getNameForObject(this.block) + "@" + this.meta + "}";
	}
}
