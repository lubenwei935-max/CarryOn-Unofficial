package net.minecraft.block.state;

import java.util.Collection;

import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

/**
 * 1.8+ IBlockState 的 1.7.10 回填。
 *
 * <h3>语义</h3>
 * 1.7.10 的"方块状态"就是 {@code (Block, int meta)} 二元组。
 * 本接口把这二元组对象化，使 1.8 风格的代码（getBlock / withProperty /
 * getPropertyNames）可以编译并保持行为。
 *
 * <h3>属性是"合成"的</h3>
 * 1.7.10 没有真正的属性存储。{@link #getPropertyNames()} 只会为
 * {@link net.backportapi.state.BlockRotation} 认识朝向的方块合成一个
 * {@code PropertyDirection}，其余方块返回空集合。
 */
public interface IBlockState
{
	Block getBlock();

	/** 1.7.10 特有的元数据视图（1.8 代码里没有，移植时用它取 meta）。 */
	int getMeta();

	/** 等价于 1.8 的 Block.getStateId(state)，用于 NBT 持久化。 */
	int getStateId();

	Collection<IProperty<?>> getPropertyNames();

	<T> T getValue(IProperty<T> property);

	<T, V extends T> IBlockState withProperty(IProperty<T> property, V value);

	IBlockState getActualState(IBlockAccess world, BlockPos pos);
}
