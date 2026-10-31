package net.backportapi.event;

import java.util.Map;
import java.util.WeakHashMap;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;

/**
 * 合成 1.8 的 PlayerContainerEvent.Open / Close。
 *
 * <p>做法：每个玩家记录上一 tick 的 {@code openContainer}，发生变化时判断方向：
 * 变回 {@code inventoryContainer} 视为关闭，否则视为打开。
 */
public class ContainerEventHooks
{
	private final Map<EntityPlayer, Container> lastContainer = new WeakHashMap<EntityPlayer, Container>();

	@SubscribeEvent
	public void onPlayerTick(TickEvent.PlayerTickEvent event)
	{
		if (event.phase != TickEvent.Phase.END || event.player == null)
			return;

		EntityPlayer player = event.player;
		Container current = player.openContainer;
		Container previous = this.lastContainer.get(player);

		if (previous == current)
			return;

		this.lastContainer.put(player, current);

		if (previous == null)
			return;

		if (current == player.inventoryContainer)
			MinecraftForge.EVENT_BUS.post(new PlayerContainerEvent.Close(player, previous));
		else
			MinecraftForge.EVENT_BUS.post(new PlayerContainerEvent.Open(player, current));
	}
}
