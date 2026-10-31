package tschipp.carryon.client;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import tschipp.carryon.client.keybinds.CarryOnKeybinds;
import tschipp.carryon.common.CommonProxy;
import tschipp.carryon.common.handler.RegistrationHandler;

public class ClientProxy extends CommonProxy
{
	@Override
	public void preInit(FMLPreInitializationEvent event)
	{
		super.preInit(event);
		RegistrationHandler.regItemRenders();
		RegistrationHandler.regClientEvents();

		// 配置界面保存后重新同步（位置/缩放等参数可实时调整）。
		// 只在客户端注册：ConfigChangedEvent 是 cpw.mods.fml.client 下的客户端专有类。
		cpw.mods.fml.common.FMLCommonHandler.instance().bus().register(new tschipp.carryon.common.config.ConfigReload());
		net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new tschipp.carryon.common.config.ConfigReload());
	}

	@Override
	public void init(FMLInitializationEvent event)
	{
		CarryOnKeybinds.init();
		super.init(event);
	}

}
