package tschipp.carryon.common.event;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent.Phase;
import cpw.mods.fml.common.gameevent.TickEvent.PlayerTickEvent;
import cpw.mods.fml.relauncher.Side;
import net.minecraft.entity.player.EntityPlayer;
import tschipp.carryon.common.config.Configs;
import tschipp.carryon.common.handler.CarryLock;

/**
 * 服务端侧的快捷栏兜底。
 *
 * <p>客户端的 {@code HotbarLockEvents} 已经把滚轮、数字键和
 * {@code currentItem} 本身都管住了，正常情况下服务端收到的
 * {@code C09PacketHeldItemChange} 只可能是搬运物那一格。
 * 这一层是给「改过的客户端 / 别的 mod 直接改字段 / 网络包乱序」准备的：
 * 1.7.10 里 {@code currentItem} 是客户端权威，服务端只从 C09 得知选中的槽位，
 * 而放下、减速、渲染同步全都依赖「主手就是搬运物」这个前提，
 * 所以服务端也必须把它钉住，否则会出现"别人看你抱着、你自己却放不下"的分歧。
 *
 * <p>{@code PlayerTickEvent.side} 的判定依据是
 * {@code player instanceof EntityPlayerMP}（见 {@code TickEvent$PlayerTickEvent}
 * 构造器），因此单人游戏的内置服务端也会走这里；客户端玩家
 * （{@code EntityClientPlayerMP}）由 {@code HotbarLockEvents} 处理，不在这里碰。
 *
 * <p>只做一件事、不做任何发包：原版 1.7.10 没有"把客户端选中槽位改回去"的包，
 * 硬发 {@code S2FPacketSetSlot} 反而会和客户端已锁定的状态打架。
 */
public class HotbarLockServer
{
	@SubscribeEvent
	public void onPlayerTick(PlayerTickEvent event)
	{
		if (event.phase != Phase.END || event.side != Side.SERVER)
			return;

		if (!Configs.Settings.lockHotbarWhileCarrying)
			return;

		EntityPlayer player = event.player;
		int slot = CarryLock.findSlot(player);
		if (slot < 0)
			return;

		if (player.inventory.currentItem != slot)
			player.inventory.currentItem = slot;
	}
}
