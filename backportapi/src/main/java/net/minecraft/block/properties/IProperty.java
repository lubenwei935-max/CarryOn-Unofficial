package net.minecraft.block.properties;

import java.util.Collection;

/**
 * 1.8+ 方块属性接口的回填。
 *
 * 1.7.10 没有属性系统，方块状态由 4 bit metadata 表示。
 * 这里的属性只是"元数据的语义视图"，由 {@link net.backportapi.state.BlockRotation}
 * 负责与实际 meta 互相换算。
 *
 * <p><b>注意：</b>1.8 的签名是 {@code IProperty<T extends Comparable<T>>}，
 * 但 1.7.10 的 {@code EnumFacing} 继承的是<b>裸</b> {@code Enum}，
 * 并未实现 {@code Comparable<EnumFacing>}，因此这里去掉了 Comparable 约束。
 */
public interface IProperty<T>
{
	String getName();

	Collection<T> getAllowedValues();

	Class<T> getValueClass();

	T parseValue(String value);
}
