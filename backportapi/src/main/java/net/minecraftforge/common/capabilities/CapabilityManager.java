package net.minecraftforge.common.capabilities;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * 1.8+ CapabilityManager 的 1.7.10 回填。
 *
 * 用法与 1.8 一致：
 * <pre>
 * CapabilityManager.INSTANCE.register(IPosition.class, new PositionStorage(), TEPosition::new);
 * </pre>
 */
public enum CapabilityManager
{
	INSTANCE;

	private final Map<String, Capability<?>> registry = new HashMap<String, Capability<?>>();
	private final Map<Class<?>, Capability<?>> byType = new HashMap<Class<?>, Capability<?>>();

	/**
	 * 注册一个能力。
	 *
	 * @param type    能力接口的 Class
	 * @param storage NBT 读写策略（可为 null，则不做持久化）
	 * @param factory 默认实例工厂
	 */
	public <T> Capability<T> register(Class<T> type, Capability.IStorage<T> storage, Callable<? extends T> factory)
	{
		if (type == null)
			throw new IllegalArgumentException("capability type must not be null");

		String name = type.getName();
		if (this.registry.containsKey(name))
			throw new IllegalStateException("Capability already registered: " + name);

		Capability<T> capability = new Capability<T>(name, type);
		capability.setStorage(storage);

		if (factory != null)
		{
			try
			{
				capability.setDefaultInstance(factory.call());
			}
			catch (Exception e)
			{
				throw new IllegalStateException("Failed to create default instance for " + name, e);
			}
		}

		this.registry.put(name, capability);
		this.byType.put(type, capability);
		return capability;
	}

	@SuppressWarnings("unchecked")
	public <T> Capability<T> get(Class<T> type)
	{
		return (Capability<T>) this.byType.get(type);
	}

	public Capability<?> getByName(String name)
	{
		return this.registry.get(name);
	}

	/** 由 @CapabilityInject 处理器调用，按名称查找。 */
	Capability<?> getOrNull(String name)
	{
		return this.registry.get(name);
	}
}
