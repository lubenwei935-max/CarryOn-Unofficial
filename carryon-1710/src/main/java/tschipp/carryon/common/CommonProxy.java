package tschipp.carryon.common;

import java.io.FileNotFoundException;

import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;

import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.relauncher.Side;
import net.minecraft.nbt.NBTException;
import net.minecraftforge.common.config.ConfigManager;
import tschipp.carryon.CarryOn;
import tschipp.carryon.common.config.Configs;
import tschipp.carryon.common.handler.RegistrationHandler;
import tschipp.carryon.common.scripting.ScriptReader;
import tschipp.carryon.network.client.CarrySlotPacket;
import tschipp.carryon.network.client.CarrySlotPacketHandler;
import tschipp.carryon.network.client.ScriptReloadPacket;
import tschipp.carryon.network.client.ScriptReloadPacketHandler;
import tschipp.carryon.network.server.SyncKeybindPacket;
import tschipp.carryon.network.server.SyncKeybindPacketHandler;

public class CommonProxy
{
	@EventHandler
	public void preInit(FMLPreInitializationEvent event)
	{
		ScriptReader.preInit(event);

		// 1.7.10 的注解式配置：读取并回填到 Configs 的静态字段
		// 先显式设置配置目录，避免依赖 BackportAPI 的 preInit 早于本模组执行
		ConfigManager.setConfigDirectory(event.getModConfigurationDirectory());
		ConfigManager.sync(CarryOn.MODID, Configs.class);

		CarryOn.network = NetworkRegistry.INSTANCE.newSimpleChannel("CarryOn");

		CarryOn.network.registerMessage(SyncKeybindPacketHandler.class, SyncKeybindPacket.class, 0, Side.SERVER);
		CarryOn.network.registerMessage(CarrySlotPacketHandler.class, CarrySlotPacket.class, 1, Side.CLIENT);
		CarryOn.network.registerMessage(ScriptReloadPacketHandler.class, ScriptReloadPacket.class, 2, Side.CLIENT);

		RegistrationHandler.regItems();
		RegistrationHandler.regCommonEvents();
	}

	@EventHandler
	public void init(FMLInitializationEvent event)
	{
		try
		{
			ScriptReader.parseScripts();
		}
		catch (JsonIOException | JsonSyntaxException | FileNotFoundException | NBTException e)
		{
			e.printStackTrace();
		}

		RegistrationHandler.regOverrideList();
		RegistrationHandler.regCaps();
	}
}
