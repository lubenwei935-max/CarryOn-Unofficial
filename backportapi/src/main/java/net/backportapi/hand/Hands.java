package net.backportapi.hand;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;

/**
 * 双手访问的统一入口。
 *
 * <p>1.7.10 只有主手；副手由 Backhand 模组提供。所有涉及
 * {@code getHeldItem(hand)} / {@code setHeldItem(hand, stack)} 的移植代码
 * 都改走这里，即可在有/无 Backhand 两种环境下都正确运行。
 *
 * <pre>
 * // 1.8 写法
 * ItemStack main = player.getHeldItemMainhand();
 * ItemStack off  = player.getHeldItemOffhand();
 *
 * // 1.7.10 + BackportAPI 写法
 * ItemStack main = Hands.getHeldItem(player, EnumHand.MAIN_HAND);
 * ItemStack off  = Hands.getHeldItem(player, EnumHand.OFF_HAND);
 * </pre>
 */
public final class Hands
{
	private Hands()
	{
	}

	/** 当前环境是否真的存在副手（即 Backhand 已安装并可用）。 */
	public static boolean hasOffhand()
	{
		return BackhandBridge.isAvailable();
	}

	public static ItemStack getHeldItem(EntityPlayer player, EnumHand hand)
	{
		if (player == null)
			return null;

		if (hand == EnumHand.OFF_HAND)
			return BackhandBridge.get(player);

		return player.getHeldItem();
	}

	public static void setHeldItem(EntityPlayer player, EnumHand hand, ItemStack stack)
	{
		if (player == null)
			return;

		if (hand == EnumHand.OFF_HAND)
		{
			BackhandBridge.set(player, stack);
			return;
		}

		player.inventory.setInventorySlotContents(player.inventory.currentItem, stack);
	}

	public static ItemStack getMainhand(EntityPlayer player)
	{
		return getHeldItem(player, EnumHand.MAIN_HAND);
	}

	public static ItemStack getOffhand(EntityPlayer player)
	{
		return getHeldItem(player, EnumHand.OFF_HAND);
	}

	/**
	 * 1.8 的"双手皆空"判定。
	 * 没有 Backhand 时退化为"主手为空"，这是 1.7.10 环境下唯一可行的语义。
	 */
	public static boolean isBothHandsEmpty(EntityPlayer player)
	{
		return getMainhand(player) == null && getOffhand(player) == null;
	}

	/** 把物品放回主手当前槽（1.7.10 的 setHeldItem 等价物）。 */
	public static void setMainhand(EntityPlayer player, ItemStack stack)
	{
		setHeldItem(player, EnumHand.MAIN_HAND, stack);
	}
}
