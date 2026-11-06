package net.backportapi.capability;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.backportapi.forge.event.AttachCapabilitiesEvent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.CapabilityDispatcher;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;

/**
 * 把 Capability 的挂载/持久化接到 1.7.10 的事件总线上。
 *
 * <p>由 {@link net.backportapi.BackportAPI} 在 preInit 注册。
 */
public class CapabilityHooks
{
	/**
	 * 实体构造时：先询问是否有监听者要挂能力，<b>有才挂</b>扩展属性。
	 *
	 * <p>这样绝大多数实体（没有能力需求）零开销——不会为它们创建
	 * dispatcher，也不会在存档时多写 NBT。在实体数量大的整合包里这点很重要。
	 */
	@SubscribeEvent
	public void onEntityConstructing(EntityEvent.EntityConstructing event)
	{
		Entity entity = event.entity;
		if (entity == null)
			return;

		if (entity.getExtendedProperties(CapabilityStorage.ID) != null)
			return;

		AttachCapabilitiesEvent<Entity> attach = new AttachCapabilitiesEvent<Entity>(entity);
		MinecraftForge.EVENT_BUS.post(attach);

		// 没人要挂能力：直接返回，不注册扩展属性
		if (attach.getCapabilities().isEmpty())
			return;

		CapabilityStorage storage = new CapabilityStorage();
		storage.setDispatcher(new CapabilityDispatcher(attach.getCapabilities()));
		entity.registerExtendedProperties(CapabilityStorage.ID, storage);
	}

	/**
	 * 玩家死亡重生：把旧实体的能力数据复制到新实体。
	 * 1.8 由 Forge 自动处理，1.7.10 必须手动。
	 */
	@SubscribeEvent
	public void onPlayerClone(PlayerEvent.Clone event)
	{
		if (!(event.entityPlayer instanceof EntityPlayer))
			return;

		CapabilityDispatcher from = Capabilities.dispatcher(event.original);
		CapabilityDispatcher to = Capabilities.dispatcher(event.entityPlayer);

		if (from != null && to != null)
			to.copyFrom(from);
	}
}
