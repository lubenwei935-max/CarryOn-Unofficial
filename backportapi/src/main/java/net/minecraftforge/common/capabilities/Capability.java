package net.minecraftforge.common.capabilities;

import net.minecraft.nbt.NBTBase;
import net.minecraft.util.EnumFacing;

/**
 * 1.8+ Capability 的 1.7.10 回填。
 *
 * <h3>背景</h3>
 * Forge 1.7.10 <b>完全没有</b> Capability 系统（实测 10.13.4.1614 的 universal jar
 * 中不存在任何 {@code capabilities} 包）。1.7.10 的等价机制是
 * {@link net.minecraftforge.common.IExtendedEntityProperties}（仅限实体）。
 *
 * <h3>本实现的策略</h3>
 * 这里重建了 Capability 的<b>对象模型</b>（注册、查找、类型转换、NBT 序列化），
 * 存储则落到 {@link net.backportapi.capability.CapabilityStorage}
 * （基于 IExtendedEntityProperties）。因此 1.8 风格的
 * {@code player.getCapability(X, null)} 代码可以改写成
 * {@code Capabilities.get(player, X)} 后保持逻辑不变。
 *
 * @param <T> 能力接口类型
 */
public class Capability<T>
{
	private final String name;
	private final Class<T> type;

	private IStorage<T> storage;
	private T defaultInstance;

	Capability(String name, Class<T> type)
	{
		this.name = name;
		this.type = type;
	}

	public String getName()
	{
		return this.name;
	}

	public Class<T> getType()
	{
		return this.type;
	}

	public T getDefaultInstance()
	{
		return this.defaultInstance;
	}

	public IStorage<T> getStorage()
	{
		return this.storage;
	}

	/** 1.8 的 Capability.cast。 */
	@SuppressWarnings("unchecked")
	public T cast(Object obj)
	{
		return (T) obj;
	}

	// 由 CapabilityManager 调用
	void setStorage(IStorage<T> storage)
	{
		this.storage = storage;
	}

	void setDefaultInstance(T instance)
	{
		this.defaultInstance = instance;
	}

	@Override
	public String toString()
	{
		return "Capability{" + this.name + "}";
	}

	/**
	 * 能力的 NBT 读写策略（对应 1.8 的 Capability.IStorage）。
	 */
	public interface IStorage<T>
	{
		NBTBase writeNBT(Capability<T> capability, T instance, EnumFacing side);

		void readNBT(Capability<T> capability, T instance, EnumFacing side, NBTBase nbt);
	}
}
