package net.backportapi.util;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;

/**
 * NBT 子集匹配（1.8 的 {@code NBTUtil.areNBTEquals} 的 1.7.10 等价物）。
 *
 * <p>语义：{@code filter} 里出现的每个键，在 {@code candidate} 里都必须存在且相等；
 * {@code candidate} 多出来的键不影响结果。空 filter 恒为匹配。
 *
 * <p>典型用途：用一小段 NBT 描述"什么样的方块实体"，例如
 * {@code {type:"spruce"}} 只匹配 type 为 spruce 的箱子。
 */
public final class NbtFilter
{
	private NbtFilter()
	{
	}

	public static boolean matches(NBTTagCompound candidate, NBTTagCompound filter)
	{
		if (filter == null || filter.hasNoTags())
			return true;

		if (candidate == null)
			return false;

		// 1.7.10 的 getKeySet() 未映射，直接调 srg 名
		for (Object keyObj : filter.func_150296_c())
		{
			String key = (String) keyObj;

			NBTBase expected = filter.getTag(key);
			NBTBase actual = candidate.getTag(key);

			if (actual == null || !actual.equals(expected))
				return false;
		}

		return true;
	}

	/** 反方向：candidate 的键是否全部包含在 filter 里（用于"完全相同"判断）。 */
	public static boolean contains(NBTTagCompound outer, NBTTagCompound inner)
	{
		return matches(outer, inner);
	}
}
