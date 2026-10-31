package tschipp.carryon;

import java.io.File;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.Mod.Instance;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import tschipp.carryon.common.CommonProxy;
import tschipp.carryon.common.command.CommandCarryOn;
import tschipp.carryon.common.command.CommandCarryOnReload;

/**
 * Carry On —— 把方块和生物搬起来带着走。
 *
 * <p>本文件是 <b>1.7.10 移植版</b>，基于上游 1.10 分支 v1.9.1。
 * 移植依赖 <b>BackportAPI Lib</b>（提供 BlockPos / IBlockState / EnumHand /
 * GlStateManager / Capability / @Config 等 1.8+ API）。
 *
 * <p>与上游的差异：
 * <ul>
 *   <li>去掉 {@code @EventBusSubscriber}（1.7.10 无此机制，改为手动注册）</li>
 *   <li>去掉 {@code updateJSON}（1.8+ 才有的字段）</li>
 *   <li>渲染改走 1.7.10 的 {@code RenderHandEvent} + {@code RenderBlocks}</li>
 *   <li>模型覆盖用 BackportAPI 的 {@code RenderTarget} 等价实现（详见 README）</li>
 * </ul>
 */
@Mod(modid = CarryOn.MODID, name = CarryOn.NAME, version = CarryOn.VERSION, guiFactory = "tschipp.carryon.client.gui.GuiFactoryCarryOn", acceptedMinecraftVersions = CarryOn.ACCEPTED_VERSIONS)
public class CarryOn
{
	@SidedProxy(clientSide = "tschipp.carryon.client.ClientProxy", serverSide = "tschipp.carryon.common.CommonProxy")
	public static CommonProxy proxy;

	@Instance(CarryOn.MODID)
	public static CarryOn instance;

	/**
	 * modid 用 {@code carryonunoffical}（与 mcmod.info 一致）。
	 *
	 * <p>它同时决定：配置文件路径 {@code config/carryonunoffical.cfg}、
	 * 资源域 {@code assets/carryonunoffical/}、能力键 {@code carryonunoffical:position}。
	 * 从旧版（modid = {@code carryon}）升级时，{@code CommonProxy} 会把旧配置文件搬过来。
	 */
	public static final String MODID = "carryonunoffical";

	/** 移植版自己的版本号，与 mcmod.info 保持一致（上游是 1.9.1）。 */
	public static final String VERSION = "1.0.0";

	public static final String NAME = "CarryOn-Unofficial";
	public static final String ACCEPTED_VERSIONS = "[1.7.10]";

	/**
	 * 普通 logger，<b>不是</b> {@code getFormatterLogger}。
	 *
	 * <p>formatter logger 会把消息当 printf 格式串处理：一旦拼接进去的字符串
	 * （配置项、异常信息、方块名）里带一个 {@code %}，{@code String.format}
	 * 就会抛 {@code IllegalFormatException}——本来在报错，结果又炸一次。
	 * 本模组所有日志都是直接拼串，用普通 logger 更安全。
	 */
	public static final Logger LOGGER = LogManager.getLogger("CarryOn");
	public static File CONFIGURATION_FILE;

	public static SimpleNetworkWrapper network;

	@EventHandler
	public void preInit(FMLPreInitializationEvent event)
	{
		CarryOn.proxy.preInit(event);
	}

	@EventHandler
	public void init(FMLInitializationEvent event)
	{
		CarryOn.proxy.init(event);
	}

	@EventHandler
	public void serverLoad(FMLServerStartingEvent event)
	{
		event.registerServerCommand(new CommandCarryOn());
		event.registerServerCommand(new CommandCarryOnReload());
	}
}
