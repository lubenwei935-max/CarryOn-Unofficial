package net.backportapi.forge.event;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraftforge.event.entity.player.PlayerEvent;

/**
 * 1.8+ PlayerContainerEvent 的 1.7.10 回填。
 *
 * <p>1.7.10 只有 {@code PlayerOpenContainerEvent}，没有"关闭容器"事件。
 * 这里通过 tick 对比 {@code player.openContainer} 的变化来合成 Open/Close，
 * 由 {@link net.backportapi.event.ContainerEventHooks} 负责触发。
 */
public class PlayerContainerEvent extends PlayerEvent
{
	private final Container container;

	public PlayerContainerEvent(EntityPlayer player, Container container)
	{
		super(player);
		this.container = container;
	}

	public Container getContainer()
	{
		return this.container;
	}

	public static class Open extends PlayerContainerEvent
	{
		public Open(EntityPlayer player, Container container)
		{
			super(player, container);
		}
	}

	public static class Close extends PlayerContainerEvent
	{
		public Close(EntityPlayer player, Container container)
		{
			super(player, container);
		}
	}
}
