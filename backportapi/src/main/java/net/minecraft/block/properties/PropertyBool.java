package net.minecraft.block.properties;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

/**
 * 1.8+ PropertyBool 的回填。
 */
public class PropertyBool implements IProperty<Boolean>
{
	private final String name;

	protected PropertyBool(String name)
	{
		this.name = name;
	}

	public static PropertyBool create(String name)
	{
		return new PropertyBool(name);
	}

	@Override
	public String getName()
	{
		return this.name;
	}

	@Override
	public Collection<Boolean> getAllowedValues()
	{
		return Collections.unmodifiableList(Arrays.asList(Boolean.TRUE, Boolean.FALSE));
	}

	@Override
	public Class<Boolean> getValueClass()
	{
		return Boolean.class;
	}

	@Override
	public Boolean parseValue(String value)
	{
		return "true".equalsIgnoreCase(value) ? Boolean.TRUE : ("false".equalsIgnoreCase(value) ? Boolean.FALSE : null);
	}
}
