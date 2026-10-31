package net.minecraft.block.properties;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

/**
 * 1.8+ PropertyInteger 的回填（用于需要按 meta 位段表达整数状态的场合）。
 */
public class PropertyInteger implements IProperty<Integer>
{
	private final String name;
	private final int min;
	private final int max;
	private final Collection<Integer> allowed;

	protected PropertyInteger(String name, int min, int max)
	{
		this.name = name;
		this.min = min;
		this.max = max;
		ArrayList<Integer> list = new ArrayList<Integer>();
		for (int i = min; i <= max; i++)
			list.add(i);
		this.allowed = Collections.unmodifiableList(list);
	}

	public static PropertyInteger create(String name, int min, int max)
	{
		return new PropertyInteger(name, min, max);
	}

	@Override
	public String getName()
	{
		return this.name;
	}

	@Override
	public Collection<Integer> getAllowedValues()
	{
		return this.allowed;
	}

	@Override
	public Class<Integer> getValueClass()
	{
		return Integer.class;
	}

	@Override
	public Integer parseValue(String value)
	{
		try
		{
			int i = Integer.parseInt(value);
			return i >= this.min && i <= this.max ? Integer.valueOf(i) : null;
		}
		catch (NumberFormatException e)
		{
			return null;
		}
	}
}
