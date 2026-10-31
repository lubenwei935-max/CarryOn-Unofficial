package tschipp.carryon.common;

import java.io.File;
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
		migrateLegacyConfig(event.getModConfigurationDirectory());
		ConfigManager.sync(CarryOn.MODID, Configs.class);

		CarryOn.network = NetworkRegistry.INSTANCE.newSimpleChannel("CarryOn");

		CarryOn.network.registerMessage(SyncKeybindPacketHandler.class, SyncKeybindPacket.class, 0, Side.SERVER);
		CarryOn.network.registerMessage(CarrySlotPacketHandler.class, CarrySlotPacket.class, 1, Side.CLIENT);
		CarryOn.network.registerMessage(ScriptReloadPacketHandler.class, ScriptReloadPacket.class, 2, Side.CLIENT);

		RegistrationHandler.regItems();
		RegistrationHandler.regCommonEvents();
	}

	/**
	 * 把旧版的 {@code carryon.cfg} 搬到 {@code carryonunoffical.cfg}。
	 *
	 * <p>modid 决定配置文件的名字，改名之后老用户调好的参数（搬运距离、渲染偏移、
	 * 黑名单……）都会留在旧文件里读不到。新文件不存在时复制一份即可，
	 * 两个版本的分类名和键名完全一样，复制过来就能直接用。
	 *
	 * <p>只复制、不删除旧文件——万一要回退到旧版本，配置还在。
	 */
	private static void migrateLegacyConfig(File configDir)
	{
		File legacy = new File(configDir, "carryon.cfg");
		File current = new File(configDir, CarryOn.MODID + ".cfg");

		if (!legacy.isFile() || current.exists())
			return;

		try
		{
			java.nio.file.Files.copy(legacy.toPath(), current.toPath());
			CarryOn.LOGGER.info("Migrated legacy config: carryon.cfg -> " + current.getName());
		}
		catch (Exception e)
		{
			CarryOn.LOGGER.warn("Could not migrate legacy config carryon.cfg: " + e.getMessage());
		}
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
