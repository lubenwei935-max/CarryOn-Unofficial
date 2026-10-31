package net.minecraftforge.items;

import java.util.concurrent.Callable;

import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;

/**
 * 1.8+ CapabilityItemHandler 的 1.7.10 回填。
 *
 * <p>1.7.10 的 TileEntity 没有 {@code hasCapability}，所以
 * {@link #ITEM_HANDLER_CAPABILITY} 主要供显式查询使用；
 * 对原生 IInventory 请直接用 {@link InventoryWrapper}。
 */
public class CapabilityItemHandler
{
	public static Capability<IItemHandler> ITEM_HANDLER_CAPABILITY;

	public static void register()
	{
		if (ITEM_HANDLER_CAPABILITY != null)
			return;

		ITEM_HANDLER_CAPABILITY = CapabilityManager.INSTANCE.register(IItemHandler.class, null, new Callable<ItemStackHandler>()
		{
			@Override
			public ItemStackHandler call()
			{
				return new ItemStackHandler();
			}
		});
	}
}
