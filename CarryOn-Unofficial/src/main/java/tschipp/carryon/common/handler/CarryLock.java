package tschipp.carryon.common.handler;

import net.backportapi.hand.Hands;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import tschipp.carryon.common.item.ItemEntity;
import tschipp.carryon.common.item.ItemTile;

/**
 * 「是否正在搬运」以及「搬运物在哪一格」的唯一判定入口。
 *
 * <h3>为什么不能只看主手</h3>
 * 搬运物本身是一个普通 {@link ItemStack}（{@link ItemTile} / {@link ItemEntity}），
 * 拾取时由 {@code Hands.setMainhand()} 写进
 * {@code player.inventory.currentItem} 指向的那一格。
 *
 * <p>而 {@code getMainhand()} 最终走的是
 * {@code inventory.getStackInSlot(currentItem)}——也就是说，
 * <b>只要 {@code currentItem} 一变，主手就不再是搬运物</b>。
 * 原来的 {@code InventoryLockEvents} 用「主手里有没有搬运物」判断是否在搬运，
 * 于是玩家一滚轮，锁定条件自己就变成 false：背包能打开、物品能重排，
 * 与此同时渲染、放下、减速等所有走 {@code getMainhand()} 的逻辑也一起失效。
 *
 * <p>所以这里改成扫整条快捷栏（0..8）：只要搬运物还在快捷栏里，
 * 无论当前选中的是哪一格，都算「搬运中」。这也让锁定机制能从
 * 「被切走一格」的坏状态里自动恢复到搬运物所在的槽位。
 */
public final class CarryLock
{
	private CarryLock()
	{
	}

	/**
	 * 这一个 ItemStack 是不是「装着东西」的搬运物。
	 *
	 * <p>必须带 NBT 数据：{@code ItemTile} / {@code ItemEntity} 也是可刷出的普通物品，
	 * 空壳（例如命令给出来的、或放下后数据已清空但物品还没移除的）不算在搬运。
	 */
	public static boolean isCarried(ItemStack stack)
	{
		if (stack == null || RegistrationHandler.itemTile == null || RegistrationHandler.itemEntity == null)
			return false;

		Item item = stack.getItem();
		if (item == RegistrationHandler.itemTile)
			return ItemTile.hasTileData(stack);
		if (item == RegistrationHandler.itemEntity)
			return ItemEntity.hasEntityData(stack);

		return false;
	}

	/**
	 * 搬运物在快捷栏里的槽位；没有搬运物（或已被挪出快捷栏）返回 -1。
	 *
	 * <p>只扫 9 格而不是 {@code mainInventory.length}：背包主格 9..35 里的物品
	 * 不可能是「手上抱着的」东西，把它们算进来会误锁。
	 */
	public static int findSlot(EntityPlayer player)
	{
		if (player == null || player.inventory == null)
			return -1;

		for (int i = 0; i < 9; i++)
		{
			if (isCarried(player.inventory.getStackInSlot(i)))
				return i;
		}

		return -1;
	}

	/**
	 * 是否处于搬运中。比 {@link #findSlot(EntityPlayer)} 多覆盖一种情况：
	 * 装了 Backhand 时，交换键可以把搬运物挪到副手（不在 9 格快捷栏里）。
	 *
	 * <p>这时没有「该钉住的槽位」可言（{@code findSlot} 返回 -1，
	 * 状态兜底不动），但输入层的滚轮 / 数字键仍应被拦住，
	 * 免得搬运物在副手时还能顺手把快捷栏翻走。
	 */
	public static boolean isCarrying(EntityPlayer player)
	{
		if (player == null)
			return false;

		if (findSlot(player) >= 0)
			return true;

		return isCarried(Hands.getOffhand(player));
	}
}
