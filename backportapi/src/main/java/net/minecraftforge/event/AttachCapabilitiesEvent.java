package net.minecraftforge.event;

import java.util.LinkedHashMap;
import java.util.Map;

import cpw.mods.fml.common.eventhandler.Event;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.capabilities.ICapabilityProvider;

/**
 * 1.8+ AttachCapabilitiesEvent 的 1.7.10 回填。
 *
 * <p>1.8 中 Forge 会对 Entity/TileEntity/ItemStack 分别触发子类事件。
 * 这里提供泛型版本，由 {@link net.backportapi.capability.CapabilityHooks}
 * 在 {@code EntityEvent.EntityConstructing} 时对实体触发。
 * TileEntity 没有创建事件，需手动调用
 * {@link net.backportapi.capability.Capabilities#attachTo(net.minecraft.tileentity.TileEntity)}。
 *
 * @param <T> 宿主类型
 */
public class AttachCapabilitiesEvent<T> extends Event
{
	private final T object;
	private final Map<ResourceLocation, ICapabilityProvider> capabilities = new LinkedHashMap<ResourceLocation, ICapabilityProvider>();

	public AttachCapabilitiesEvent(T object)
	{
		this.object = object;
	}

	public T getObject()
	{
		return this.object;
	}

	/** 对应 1.8 的 addCapability(ResourceLocation, ICapabilityProvider)。 */
	public void addCapability(ResourceLocation key, ICapabilityProvider provider)
	{
		if (provider != null)
			this.capabilities.put(key == null ? new ResourceLocation("backportapi", "unnamed") : key, provider);
	}

	public Map<ResourceLocation, ICapabilityProvider> getCapabilities()
	{
		return this.capabilities;
	}
}
