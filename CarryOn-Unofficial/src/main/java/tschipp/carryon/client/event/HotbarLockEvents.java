package tschipp.carryon.client.event;

import cpw.mods.fml.client.FMLClientHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent.KeyInputEvent;
import cpw.mods.fml.common.gameevent.TickEvent.ClientTickEvent;
import cpw.mods.fml.common.gameevent.TickEvent.Phase;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraftforge.client.event.MouseEvent;
import tschipp.carryon.common.config.Configs;
import tschipp.carryon.common.handler.CarryLock;

/**
 * 抱起方块 / 生物时锁死快捷栏：{@code inventory.currentItem} 必须一直指向搬运物那一格。
 *
 * <h3>1.7.10 到底有哪几条路能改 currentItem</h3>
 * 反汇编运行时 {@code Minecraft.runTick()}（forgebin.jar，MCP 名）可以看到：
 *
 * <pre>
 *   89:  FMLCommonHandler.onPreClientTick()                      → ClientTickEvent START
 *  453:  while (Mouse.next())
 *  459:      if (ForgeHooksClient.postMouseEvent()) continue;    → MouseEvent（可取消）
 *  517:      int d = Mouse.getEventDWheel();
 *  536:      if (d != 0) thePlayer.inventory.changeCurrentItem(d);   ← ① 滚轮
 *  661:  while (Keyboard.next())
 *  685:      KeyBinding.onTick(key);                              ← pressTime++
 * 1298:      FMLCommonHandler.fireKeyInput()                     → KeyInputEvent
 * 1304:  for (i = 0; i &lt; 9; ++i)
 * 1339:      if (gameSettings.keyBindsHotbar[i].isPressed())
 *                 thePlayer.inventory.currentItem = i;              ← ② 数字键 1..9
 * 2130: FMLCommonHandler.onPostClientTick()                      → ClientTickEvent END
 * 2147: return
 * </pre>
 *
 * <p>渲染不在这个方法里：{@code runGameLoop()} 先 {@code runTick()}（偏移 131），
 * 之后才 {@code EntityRenderer.updateCameraAndRender()}（偏移 324）。
 * 所以只要在本 tick 结束前把槽位改回去，画面上连一帧的跳动都没有。
 *
 * <h3>三层拦截</h3>
 * <ol>
 *   <li><b>滚轮</b>：{@link MouseEvent} 是 {@code @Cancelable} 的，取消后
 *       {@code postMouseEvent()} 返回 true，{@code runTick()} 直接
 *       {@code continue} 到下一个 {@code Mouse.next()}，① 整段不会执行。
 *       只在 {@code dwheel != 0} 时取消——左右键必须留给"放下"。</li>
 *   <li><b>数字键</b>：1.7.10 没有可取消的按键事件。但 ② 用的是
 *       {@code KeyBinding.isPressed()}，它每调用一次就把 {@code pressTime} 减 1。
 *       在 {@link KeyInputEvent}（1298，正好在 {@code onTick(685)} 之后、
 *       ② 之前）里把这 9 个 binding 的 {@code pressTime} 抽干，
 *       等 ② 去问的时候就已经是 false 了。
 *       注意不能在 {@code ClientTickEvent START(89)} 里抽：那时按键事件还没处理，
 *       {@code pressTime} 还没加。</li>
 *   <li><b>状态兜底</b>：{@code ClientTickEvent START/END} 把
 *       {@code currentItem} 强行钉回 {@link CarryLock#findSlot} 得到的槽位，
 *       覆盖中键选取方块、其它 mod 直接改字段、以及任何没预料到的路径。
 *       END 在 2130，早于本帧渲染，因此不会有视觉闪烁。</li>
 * </ol>
 *
 * <p>本类无状态，按工程惯例在 Forge 总线（{@code MouseEvent}）和
 * FML 总线（{@code KeyInputEvent} / {@code ClientTickEvent}）各注册一个实例。
 */
@SideOnly(Side.CLIENT)
public class HotbarLockEvents
{
	/** ① 滚轮：取消带滚轮增量的鼠标事件。 */
	@SubscribeEvent
	public void onMouse(MouseEvent event)
	{
		if (event.dwheel == 0)
			return;

		if (!Configs.Settings.lockHotbarWhileCarrying)
			return;

		if (CarryLock.isCarrying(clientPlayer()))
			event.setCanceled(true);
	}

	/** ② 数字键 1..9：在 vanilla 读取之前把 hotbar KeyBinding 的 pressTime 抽干。 */
	@SubscribeEvent
	public void onKeyInput(KeyInputEvent event)
	{
		if (!Configs.Settings.lockHotbarWhileCarrying)
			return;

		if (!CarryLock.isCarrying(clientPlayer()))
			return;

		Minecraft mc = client();
		if (mc == null || mc.gameSettings == null || mc.gameSettings.keyBindsHotbar == null)
			return;

		KeyBinding[] keys = mc.gameSettings.keyBindsHotbar;
		for (int i = 0; i < keys.length; i++)
		{
			KeyBinding key = keys[i];
			if (key == null)
				continue;

			// isPressed() 每次消耗一个 pressTime；循环保证连击 / 长按不会留残留。
			// 上限 64 纯粹是防御：万一有别的 mod 疯狂 onTick，也不至于卡死主线程。
			for (int n = 0; n < 64 && key.isPressed(); n++)
			{
				// 故意空循环体：目的就是消耗掉这次按键
			}
		}
	}

	/** ③ 状态兜底：无论谁在本 tick 里改了 currentItem，都钉回搬运物所在格。 */
	@SubscribeEvent
	public void onClientTick(ClientTickEvent event)
	{
		if (event.phase != Phase.START && event.phase != Phase.END)
			return;

		if (!Configs.Settings.lockHotbarWhileCarrying)
			return;

		EntityPlayer player = clientPlayer();
		int slot = CarryLock.findSlot(player);
		if (slot < 0)
			return;

		InventoryPlayer inv = player.inventory;
		if (inv.currentItem != slot)
			inv.currentItem = slot;
	}

	/**
	 * 用 {@code FMLClientHandler.instance().getClient()} 而不是
	 * {@code Minecraft.getMinecraft()}：它是 Forge 自己的入口，
	 * 在 GTNH / Angelica 这类会改写客户端类静态成员的运行时里更稳。
	 */
	private static Minecraft client()
	{
		return FMLClientHandler.instance().getClient();
	}

	private static EntityPlayer clientPlayer()
	{
		Minecraft mc = client();
		return mc == null ? null : mc.thePlayer;
	}
}
