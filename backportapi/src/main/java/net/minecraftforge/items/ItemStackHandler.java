package net.minecraftforge.items;

import net.minecraft.item.ItemStack;

/**
 * 1.8+ ItemStackHandler 的 1.7.10 回填：定长槽位数组。
 */
public class ItemStackHandler implements IItemHandler
{
	protected ItemStack[] stacks;
	private final int size;

	public ItemStackHandler()
	{
		this(1);
	}

	public ItemStackHandler(int size)
	{
		this.size = size;
		this.stacks = new ItemStack[size];
	}

	@Override
	public int getSlots()
	{
		return this.size;
	}

	@Override
	public ItemStack getStackInSlot(int slot)
	{
		return isValid(slot) ? this.stacks[slot] : null;
	}

	@Override
	public ItemStack insertItem(int slot, ItemStack stack, boolean simulate)
	{
		if (stack == null || stack.stackSize <= 0 || !isValid(slot))
			return stack;

		ItemStack existing = this.stacks[slot];

		if (existing != null && (!existing.isItemEqual(stack) || !ItemStack.areItemStackTagsEqual(existing, stack)))
			return stack;

		int limit = Math.min(getSlotLimit(slot), stack.getMaxStackSize());
		int current = existing == null ? 0 : existing.stackSize;
		int accepted = Math.min(limit - current, stack.stackSize);

		if (accepted <= 0)
			return stack;

		if (!simulate)
		{
			if (existing == null)
			{
				this.stacks[slot] = stack.copy();
				this.stacks[slot].stackSize = accepted;
			}
			else
			{
				existing.stackSize += accepted;
			}
			onContentsChanged(slot);
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
		if (amount <= 0 || !isValid(slot))
			return null;

		ItemStack existing = this.stacks[slot];
		if (existing == null)
			return null;

		int taken = Math.min(amount, existing.stackSize);
		ItemStack result = existing.copy();
		result.stackSize = taken;

		if (!simulate)
		{
			if (taken >= existing.stackSize)
				this.stacks[slot] = null;
			else
				existing.stackSize -= taken;
			onContentsChanged(slot);
		}
		return result;
	}

	@Override
	public int getSlotLimit(int slot)
	{
		return 64;
	}

	protected boolean isValid(int slot)
	{
		return slot >= 0 && slot < this.size;
	}

	/** 内容变化回调，子类可覆写。 */
	protected void onContentsChanged(int slot)
	{
	}
}
