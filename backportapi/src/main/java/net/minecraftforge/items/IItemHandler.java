package net.minecraftforge.items;

import net.minecraft.item.ItemStack;

/**
 * 1.8+ IItemHandler 的 1.7.10 回填。
 *
 * <p>1.7.10 没有 {@code net.minecraftforge.items}，物品容器统一用
 * {@link net.minecraft.inventory.IInventory}。此接口提供 1.8 的
 * "槽位 + 插入/抽取"语义，{@link InventoryWrapper} 负责适配到 IInventory。
 */
public interface IItemHandler
{
	int getSlots();

	ItemStack getStackInSlot(int slot);

	/** 插入，返回<b>剩余</b>的物品（全部插入则返回 null）。 */
	ItemStack insertItem(int slot, ItemStack stack, boolean simulate);

	/** 抽取，返回<b>抽出的</b>物品（无物可抽则返回 null）。 */
	ItemStack extractItem(int slot, int amount, boolean simulate);

	int getSlotLimit(int slot);
}
