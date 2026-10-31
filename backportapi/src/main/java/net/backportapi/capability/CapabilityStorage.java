package net.backportapi.capability;

import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.common.IExtendedEntityProperties;
import net.minecraftforge.common.capabilities.CapabilityDispatcher;

/**
 * 用 1.7.10 的 {@link IExtendedEntityProperties} 承载 Capability 数据。
 *
 * <p>1.7.10 没有 Capability，但有"实体扩展属性"：注册后会随实体 NBT
 * 自动存读（存在 {@code ForgeData} 标签下）。这正是 1.8 Capability
 * 持久化所做的事，所以拿它当底层存储最自然。
 *
 * <p>生命周期由 {@link CapabilityHooks} 在 {@code EntityConstructing} 时挂载。
 */
public class CapabilityStorage implements IExtendedEntityProperties
{
	public static final String ID = "backportapi:caps";

	private static final String NBT_KEY = "capabilities";

	private CapabilityDispatcher dispatcher;

	public static CapabilityStorage get(Entity entity)
	{
		if (entity == null)
			return null;
		IExtendedEntityProperties props = entity.getExtendedProperties(ID);
		return props instanceof CapabilityStorage ? (CapabilityStorage) props : null;
	}

	/** 惰性创建：避免在没有任何能力时也分配一个 map。 */
	public CapabilityDispatcher getDispatcher()
	{
		if (this.dispatcher == null)
			this.dispatcher = new CapabilityDispatcher();
		return this.dispatcher;
	}

	public void setDispatcher(CapabilityDispatcher dispatcher)
	{
		this.dispatcher = dispatcher == null ? new CapabilityDispatcher() : dispatcher;
	}

	@Override
	public void init(Entity entity, World world)
	{
	}

	@Override
	public void saveNBTData(NBTTagCompound tag)
	{
		NBTTagCompound data = this.dispatcher.serializeNBT();
		if (data != null && !data.hasNoTags())
			tag.setTag(NBT_KEY, data);
	}

	@Override
	public void loadNBTData(NBTTagCompound tag)
	{
		if (tag != null && tag.hasKey(NBT_KEY))
		{
			net.minecraft.nbt.NBTBase data = tag.getTag(NBT_KEY);
			if (data instanceof NBTTagCompound)
				this.dispatcher.deserializeNBT((NBTTagCompound) data);
		}
	}
}
