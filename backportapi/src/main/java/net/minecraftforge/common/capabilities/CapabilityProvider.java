package net.minecraftforge.common.capabilities;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;

/**
 * 单能力 provider 的便捷实现，适用于 TileEntity 等无法自动挂载的宿主。
 *
 * <pre>
 * public class MyTile extends TileEntity {
 *     private final CapabilityProvider&lt;IPosition&gt; cap =
 *         new CapabilityProvider&lt;&gt;(MY_CAP, new MyPosition(), MY_CAP.getStorage());
 *     // 在 writeToNBT/readFromNBT 里调用 cap.serializeNBT()/deserializeNBT()
 * }
 * </pre>
 */
public class CapabilityProvider<T> implements ICapabilitySerializable<NBTTagCompound>
{
	private final Capability<T> capability;
	private final T instance;
	private final Capability.IStorage<T> storage;

	public CapabilityProvider(Capability<T> capability, T instance)
	{
		this(capability, instance, capability == null ? null : capability.getStorage());
	}

	public CapabilityProvider(Capability<T> capability, T instance, Capability.IStorage<T> storage)
	{
		this.capability = capability;
		this.instance = instance;
		this.storage = storage;
	}

	public T getInstance()
	{
		return this.instance;
	}

	@Override
	public boolean hasCapability(Capability<?> capability, EnumFacing facing)
	{
		return this.capability != null && capability == this.capability;
	}

	@Override
	@SuppressWarnings("unchecked")
	public <R> R getCapability(Capability<R> capability, EnumFacing facing)
	{
		return this.capability != null && capability == this.capability ? (R) this.instance : null;
	}

	@Override
	public NBTTagCompound serializeNBT()
	{
		NBTTagCompound tag = new NBTTagCompound();
		if (this.storage != null && this.capability != null && this.instance != null)
		{
			NBTBase data = this.storage.writeNBT(this.capability, this.instance, null);
			if (data != null)
				tag.setTag("data", data);
		}
		return tag;
	}

	@Override
	public void deserializeNBT(NBTTagCompound tag)
	{
		if (tag != null && tag.hasKey("data") && this.storage != null && this.capability != null && this.instance != null)
			this.storage.readNBT(this.capability, this.instance, null, tag.getTag("data"));
	}
}
