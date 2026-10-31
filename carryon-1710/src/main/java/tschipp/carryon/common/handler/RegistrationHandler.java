package tschipp.carryon.common.handler;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.item.Item;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.CapabilityManager;
import tschipp.carryon.client.event.RenderEntityEvents;
import tschipp.carryon.client.event.RenderEvents;
import tschipp.carryon.common.capabilities.IPosition;
import tschipp.carryon.common.capabilities.PositionProvider;
import tschipp.carryon.common.capabilities.PositionStorage;
import tschipp.carryon.common.capabilities.TEPosition;
import tschipp.carryon.common.capabilities.event.PositionClientEvents;
import tschipp.carryon.common.capabilities.event.PositionCommonEvents;
import tschipp.carryon.common.event.ItemEntityEvents;
import tschipp.carryon.common.event.ItemEvents;
import tschipp.carryon.common.item.ItemEntity;
import tschipp.carryon.common.item.ItemTile;

public class RegistrationHandler
{
	public static Item itemTile;
	public static Item itemEntity;

	public static void regItems()
	{
		itemTile = new ItemTile();
		itemEntity = new ItemEntity();
	}

	/**
	 * 1.7.10 没有 ModelLoader / ModelResourceLocation。
	 * 物品图标在 Item 内部通过 {@code registerIcons} 注册。
	 *
	 * <p>这里注册"空渲染器"：搬运中的物品在第一/第三人称下都不画，
	 * 但原版的手臂照常渲染（1.7.10 的手臂绘制在 {@code renderItemInFirstPerson}
	 * 里，和物品渲染是两件事）。
	 */
	@SideOnly(Side.CLIENT)
	public static void regItemRenders()
	{
		net.backportapi.client.HideHeldItem.register(itemTile);
		net.backportapi.client.HideHeldItem.register(itemEntity);
	}

	public static void regCommonEvents()
	{
		MinecraftForge.EVENT_BUS.register(new ItemEvents());
		MinecraftForge.EVENT_BUS.register(new ItemEntityEvents());
		MinecraftForge.EVENT_BUS.register(new PositionCommonEvents());
	}

	@SideOnly(Side.CLIENT)
	public static void regClientEvents()
	{
		MinecraftForge.EVENT_BUS.register(new RenderEvents());
		MinecraftForge.EVENT_BUS.register(new RenderEntityEvents());
		MinecraftForge.EVENT_BUS.register(new PositionClientEvents());

		// TickEvent 挂在 FML 总线，而渲染事件挂在 Forge 总线。
		// 同一个类需要在两条总线上各注册一个实例（1.7.10 没有 @EventBusSubscriber）。
		cpw.mods.fml.common.FMLCommonHandler.instance().bus().register(new RenderEvents());
		cpw.mods.fml.common.FMLCommonHandler.instance().bus().register(new PositionClientEvents());
	}

	public static void regOverrideList()
	{
		ListHandler.initForbiddenTiles();
		ModelOverridesHandler.initOverrides();
	}

	public static void regCaps()
	{
		CapabilityManager.INSTANCE.register(IPosition.class, new PositionStorage(), new java.util.concurrent.Callable<TEPosition>()
		{
			@Override
			public TEPosition call()
			{
				return new TEPosition();
			}
		});

		// 1.7.10 没有 FML 的自动字段注入，必须手动填 @CapabilityInject
		net.backportapi.capability.Capabilities.inject(PositionProvider.class);
	}
}
