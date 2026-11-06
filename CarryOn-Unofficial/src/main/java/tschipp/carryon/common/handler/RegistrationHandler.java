package tschipp.carryon.common.handler;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.item.Item;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.CapabilityManager;
import tschipp.carryon.CarryOn;
import tschipp.carryon.client.event.InventoryLockEvents;
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
	 * 里，和物品渲染是两件事）。槽位里的图标也不用画——
	 * {@code ItemTile.getIconFromDamage} 返回的是全透明贴图。
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

		// 抱起东西时不许打开背包：GuiOpenEvent 挂在 Forge 总线
		MinecraftForge.EVENT_BUS.register(new InventoryLockEvents());

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
		// 能力注册是"一次性"的：同一个 Class 只能注册一次，第二次会抛
		// IllegalStateException("Capability already registered")，直接把游戏崩在加载阶段。
		// 唯一能触发它的场景是 mods 目录里留了新旧两个 jar——两个 mod 容器共享同一份类，
		// init 会被跑两遍。这里先问一句，注册过就跳过。
		if (CapabilityManager.INSTANCE.get(IPosition.class) != null)
		{
			CarryOn.LOGGER.warn("[CarryOn] capability " + IPosition.class.getName() + " 已经注册过，跳过重复注册");
			return;
		}

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
