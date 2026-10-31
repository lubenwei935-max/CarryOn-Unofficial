package net.backportapi.client;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.IItemRenderer;
import net.minecraftforge.client.MinecraftForgeClient;

/**
 * 让某个物品在第一人称/第三人称"什么都不画"，但<b>保留原版手臂</b>。
 *
 * <h3>为什么不能用 RenderHandEvent 取消</h3>
 * 1.7.10 的 {@code ItemRenderer.renderItemInFirstPerson} 在同一个方法里依次做两件事：
 * <ol>
 *   <li>绑定玩家皮肤，画出手臂（无条件）</li>
 *   <li>调用物品渲染器画手持物</li>
 * </ol>
 * {@code RenderHandEvent} 在方法<b>开头</b>触发，取消它就是整个方法返回——
 * 手臂和物品一起消失。所以"想保留手臂、只隐藏物品"不能用它。
 *
 * <h3>正确做法</h3>
 * 注册一个 {@code IItemRenderer}，只在 {@code EQUIPPED*} 类型下返回 true
 * 且 {@code renderItem} 里什么都不画。原版照常画手臂，物品部分变成空操作。
 *
 * <pre>
 * HideHeldItem.register(MyItems.carried);
 * </pre>
 */
public final class HideHeldItem implements IItemRenderer
{
	private static final HideHeldItem INSTANCE = new HideHeldItem();

	private HideHeldItem()
	{
	}

	public static void register(Item item)
	{
		if (item != null)
			MinecraftForgeClient.registerItemRenderer(item, INSTANCE);
	}

	@Override
	public boolean handleRenderType(ItemStack item, ItemRenderType type)
	{
		return type == ItemRenderType.EQUIPPED_FIRST_PERSON || type == ItemRenderType.EQUIPPED;
	}

	@Override
	public boolean shouldUseRenderHelper(ItemRenderType type, ItemStack item, ItemRendererHelper helper)
	{
		return false;
	}

	@Override
	public void renderItem(ItemRenderType type, ItemStack item, Object... data)
	{
		// 故意留空：手臂由原版绘制，手持物由调用方自行渲染
	}
}
