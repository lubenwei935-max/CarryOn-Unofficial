package tschipp.carryon.client.event;

import cpw.mods.fml.client.FMLClientHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.GuiOpenEvent;
import tschipp.carryon.common.handler.CarryLock;

/**
 * 抱起东西的时候不许打开背包。
 *
 * <h3>为什么拦 {@code GuiOpenEvent}</h3>
 * 1.7.10 的 {@code Minecraft.displayGuiScreen} 会先 {@code post} 一个
 * {@code GuiOpenEvent}，事件被取消就<b>直接 return</b>（见该方法开头的
 * {@code if (MinecraftForge.EVENT_BUS.post(event)) return;}）。
 * 也就是说在这里取消，GUI 根本不会初始化，屏幕上不会闪一下——
 * 比"打开了再 tick 里 closeScreen"干净。
 *
 * <h3>为什么只拦背包</h3>
 * 玩家自己的背包（以及创造模式那个）可以把自己的物品栏重新排布、
 * 把正抱着的搬运物挪到别的格子，等于绕过"搬运中锁定快捷栏"。
 * 箱子和工作台之类本来就得走近才能开，搬运时右键已经被 {@code ItemEvents}
 * 接管去"放下"了，不用在这里重复拦。
 *
 * <h3>判定改成扫整条快捷栏</h3>
 * 原来这里只看 {@code Hands.getMainhand()}。1.7.10 的主手就是
 * {@code inventory.getStackInSlot(currentItem)}，玩家一滚轮 / 按数字键，
 * 主手就不再是搬运物，于是「不在搬运中」→ 背包能被打开 →
 * 搬运物被拖到别的格子，锁定彻底绕过。现在统一走
 * {@link CarryLock#isCarrying(EntityPlayer)}：只要搬运物还在快捷栏（或副手）里，
 * 无论当前选中哪一格都算搬运中。
 */
@SideOnly(Side.CLIENT)
public class InventoryLockEvents
{
	@SubscribeEvent
	public void onGuiOpen(GuiOpenEvent event)
	{
		GuiScreen gui = event.gui;
		if (gui == null)
			return;

		// 只关心"背包"这一类；其它 GUI（箱子、工作台、聊天…）照常
		if (!(gui instanceof GuiInventory) && !(gui instanceof GuiContainerCreative))
			return;

		Minecraft mc = FMLClientHandler.instance().getClient();
		EntityPlayer player = mc == null ? null : mc.thePlayer;

		if (!CarryLock.isCarrying(player))
			return;

		event.setCanceled(true);
	}
}
