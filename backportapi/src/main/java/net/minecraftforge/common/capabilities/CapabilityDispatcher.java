package net.minecraftforge.common.capabilities;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;

/**
 * 能力的聚合容器：一个实体/TileEntity 持有多个能力时用它统一代理。
 *
 * <p>对应 1.8 中由 Forge 自动生成的那个 dispatcher，这里是显式实现。
 * 它实现了 {@link ICapabilitySerializable}，因此可以直接把整组能力写进 NBT。
 */
public class CapabilityDispatcher implements ICapabilityProvider, ICapabilitySerializable<NBTTagCompound>
{
	private final Map<String, ICapabilityProvider> providers = new LinkedHashMap<String, ICapabilityProvider>();
	private final Map<Capability<?>, ICapabilityProvider> cache = new LinkedHashMap<Capability<?>, ICapabilityProvider>();

	public CapabilityDispatcher()
	{
	}

	public CapabilityDispatcher(Map<ResourceLocation, ICapabilityProvider> caps)
	{
		if (caps != null)
		{
			for (Map.Entry<ResourceLocation, ICapabilityProvider> e : caps.entrySet())
			{
				String key = e.getKey() == null ? "unnamed" : e.getKey().toString();
				this.providers.put(key, e.getValue());
			}
		}
	}

	@Override
	public boolean hasCapability(Capability<?> capability, EnumFacing facing)
	{
		return findProvider(capability) != null;
	}

	@Override
	public <T> T getCapability(Capability<T> capability, EnumFacing facing)
	{
		ICapabilityProvider provider = findProvider(capability);
		return provider == null ? null : provider.getCapability(capability, facing);
	}

	private ICapabilityProvider findProvider(Capability<?> capability)
	{
		if (capability == null)
			return null;

		if (this.cache.containsKey(capability))
			return this.cache.get(capability);

		for (ICapabilityProvider provider : this.providers.values())
		{
			if (provider != null && provider.hasCapability(capability, null))
			{
				this.cache.put(capability, provider);
				return provider;
			}
		}
		return null;
	}

	@Override
	public NBTTagCompound serializeNBT()
	{
		NBTTagCompound root = new NBTTagCompound();
		for (Map.Entry<String, ICapabilityProvider> e : this.providers.entrySet())
		{
			ICapabilityProvider provider = e.getValue();
			if (provider instanceof ICapabilitySerializable)
			{
				Object nbt = ((ICapabilitySerializable<?>) provider).serializeNBT();
				if (nbt instanceof net.minecraft.nbt.NBTBase)
					root.setTag(e.getKey(), (net.minecraft.nbt.NBTBase) nbt);
			}
		}
		return root;
	}

	@Override
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public void deserializeNBT(NBTTagCompound root)
	{
		if (root == null)
			return;

		for (Map.Entry<String, ICapabilityProvider> e : this.providers.entrySet())
		{
			ICapabilityProvider provider = e.getValue();
			if (provider instanceof ICapabilitySerializable && root.hasKey(e.getKey()))
			{
				((ICapabilitySerializable) provider).deserializeNBT(root.getTag(e.getKey()));
			}
		}
		this.cache.clear();
	}

	/**
	 * 玩家死亡重生时把旧实体的能力数据搬过来。
	 * 1.8 由 Forge 自动处理；这里必须显式调用（见 CapabilityHooks）。
	 */
	public void copyFrom(CapabilityDispatcher other)
	{
		if (other == null)
			return;
		deserializeNBT(other.serializeNBT());
	}

	public Map<String, ICapabilityProvider> getProviders()
	{
		return this.providers;
	}
}
