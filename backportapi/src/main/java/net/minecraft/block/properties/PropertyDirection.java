package net.minecraft.block.properties;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import net.backportapi.util.Facing;
import net.minecraft.util.EnumFacing;

/**
 * 1.8+ PropertyDirection 的回填。
 *
 * 1.7.10 没有属性系统；此对象存在的主要目的是让
 * "遍历属性、判断是否朝向属性、withProperty 旋转方块"
 * 这段 1.8 风格逻辑可以原样运行。
 */
public class PropertyDirection implements IProperty<EnumFacing>
{
	private final String name;
	private final Set<EnumFacing> allowedValues;

	protected PropertyDirection(String name, Collection<EnumFacing> values)
	{
		this.name = name;
		this.allowedValues = Collections.unmodifiableSet(new LinkedHashSet<EnumFacing>(values));
	}

	/** 六个方向全允许（1.8 的 create(name)）。 */
	public static PropertyDirection create(String name)
	{
		return new PropertyDirection(name, Arrays.asList(Facing.VALUES));
	}

	/** 指定允许方向（1.8 的 create(name, values...)）。 */
	public static PropertyDirection create(String name, EnumFacing... values)
	{
		return new PropertyDirection(name, Arrays.asList(values));
	}

	/** 水平四向，常用于楼梯/箱子/熔炉。 */
	public static PropertyDirection createHorizontal(String name)
	{
		return new PropertyDirection(name, Arrays.asList(Facing.HORIZONTALS));
	}

	@Override
	public String getName()
	{
		return this.name;
	}

	@Override
	public Collection<EnumFacing> getAllowedValues()
	{
		return this.allowedValues;
	}

	@Override
	public Class<EnumFacing> getValueClass()
	{
		return EnumFacing.class;
	}

	@Override
	public EnumFacing parseValue(String value)
	{
		for (EnumFacing facing : this.allowedValues)
		{
			if (facing.name().equalsIgnoreCase(value))
				return facing;
		}
		return null;
	}

	@Override
	public String toString()
	{
		return "PropertyDirection{" + this.name + "}";
	}
}
