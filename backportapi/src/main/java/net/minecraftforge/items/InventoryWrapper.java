package net.minecraftforge.items;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

/**
 * 把 1.7.10 的 {@link IInventory} 适配成 1.8 风格的 {@link IItemHandler}。
 *
 * <p>典型用途：移植代码里"清空 TileEntity 的物品"这类操作，
 * 1.8 走 {@code CapabilityItemHandler.ITEM_HANDLER_CAPABILITY}，
 * 1.7.10 直接包一层即可：
 * <pre>
 * if (tile instanceof IInventory) {
 *     IItemHandler inv = new InventoryWrapper((IInventory) tile);
 *     for (int i = 0; i &lt; inv.getSlots(); i++) inv.extractItem(i, 64, false);
 * }
 * </pre>
 */
public class InventoryWrapper implements IItemHandler
{
	private final IInventory inventory;

	public InventoryWrapper(IInventory inventory)
	{
		this.inventory = inventory;
	}

	@Override
	public int getSlots()
	{
		return this.inventory.getSizeInventory();
	}

	@Override
	public ItemStack getStackInSlot(int slot)
	{
		return this.inventory.getStackInSlot(slot);
	}

	@Override
	public ItemStack insertItem(int slot, ItemStack stack, boolean simulate)
	{
		if (stack == null || !this.inventory.isItemValidForSlot(slot, stack))
			return stack;

		ItemStack existing = this.inventory.getStackInSlot(slot);

		if (existing != null && (!existing.isItemEqual(stack) || !ItemStack.areItemStackTagsEqual(existing, stack)))
			return stack;

		int limit = Math.min(this.inventory.getInventoryStackLimit(), stack.getMaxStackSize());
		int current = existing == null ? 0 : existing.stackSize;
		int accepted = Math.min(limit - current, stack.stackSize);

		if (accepted <= 0)
			return stack;

		if (!simulate)
		{
			if (existing == null)
			{
				ItemStack copy = stack.copy();
				copy.stackSize = accepted;
				this.inventory.setInventorySlotContents(slot, copy);
			}
			else
			{
				existing.stackSize += accepted;
				this.inventory.setInventorySlotContents(slot, existing);
			}
			this.inventory.markDirty();
		}

		if (accepted >= stack.stackSize)
			return null;

		ItemStack remainder = stack.copy();
		remainder.stackSize = stack.stackSize - accepted;
		return remainder;
	}

	@Override
	public ItemStack extractItem(int slot, int amount, boolean simulate)
	{
		ItemStack existing = this.inventory.getStackInSlot(slot);
		if (existing == null || amount <= 0)
			return null;

		int taken = Math.min(amount, existing.stackSize);
		ItemStack result = existing.copy();
		result.stackSize = taken;

		if (!simulate)
		{
			if (taken >= existing.stackSize)
				this.inventory.setInventorySlotContents(slot, null);
			else
			{
				existing.stackSize -= taken;
				this.inventory.setInventorySlotContents(slot, existing);
			}
			this.inventory.markDirty();
		}
		return result;
	}

	@Override
	public int getSlotLimit(int slot)
	{
		return this.inventory.getInventoryStackLimit();
	}
}
