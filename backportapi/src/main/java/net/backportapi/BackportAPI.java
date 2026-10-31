package net.backportapi;

import org.apache.logging.log4j.Logger;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import net.backportapi.capability.CapabilityHooks;
import net.backportapi.event.ContainerEventHooks;
import net.backportapi.hand.Hands;
import net.backportapi.util.Scheduling;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.items.CapabilityItemHandler;

/**
 * BackportAPI Lib —— 把 1.8+ 的一批 API 回填到 Minecraft 1.7.10 的兼容库。
 *
 * <h3>它解决什么</h3>
 * 把 1.8/1.9 时代的模组移植回 1.7.10 时，最大的阻力不是逻辑，而是
 * "这些类/方法根本不存在"。本库按原始包名重建了这些缺失的 API，
 * 使移植工作从"重写"退化为"改写少量调用点"。
 *
 * <h3>覆盖范围</h3>
 * <ul>
 *   <li>{@code net.minecraft.util.math} —— BlockPos / Vec3d / MathHelper</li>
 *   <li>{@code net.minecraft.block.state} + {@code properties} —— IBlockState / PropertyDirection</li>
 *   <li>{@code net.minecraft.util} —— EnumHand / EnumActionResult / SoundEvent / SoundCategory</li>
 *   <li>{@code net.minecraft.util.text} —— ITextComponent / TextComponentString / TextFormatting</li>
 *   <li>{@code net.minecraft.init} —— MobEffects / SoundEvents</li>
 *   <li>{@code net.minecraft.client.renderer} —— GlStateManager</li>
 *   <li>{@code net.minecraftforge.common.capabilities} —— 完整的 Capability 对象模型</li>
 *   <li>{@code net.minecraftforge.items} —— IItemHandler / ItemStackHandler</li>
 *   <li>{@code net.minecraftforge.common.config} —— @Config + ConfigManager</li>
 *   <li>{@code net.minecraftforge.event} —— AttachCapabilitiesEvent / PlayerContainerEvent</li>
 * </ul>
 *
 * <h3>副手</h3>
 * 1.7.10 没有副手。装了 <b>Backhand</b>（modid {@code backhand}）时，
 * {@link Hands} 会通过反射自动接上；没装则副手恒为空，代码无需分支。
 *
 * <h3>硬前置</h3>
 * 仅需 Forge 1.7.10。Backhand 为软前置（可选）。
 */
@Mod(modid = BackportAPI.MODID, name = BackportAPI.NAME, version = BackportAPI.VERSION, acceptedMinecraftVersions = BackportAPI.ACCEPTED_VERSIONS)
public class BackportAPI
{
	public static final String MODID = "backportapi";
	public static final String NAME = "BackportAPI Lib";
	public static final String VERSION = "1.0.3";
	public static final String ACCEPTED_VERSIONS = "[1.7.10]";

	@Mod.Instance(BackportAPI.MODID)
	public static BackportAPI instance;

	public static Logger LOGGER;

	@Mod.EventHandler
	public void preInit(FMLPreInitializationEvent event)
	{
		LOGGER = event.getModLog();
		ConfigManager.setConfigDirectory(event.getModConfigurationDirectory());

		// 注册 1.8 的 ITEM_HANDLER 能力
		CapabilityItemHandler.register();

		// 实体能力挂载 + 重生复制（Forge 总线）
		MinecraftForge.EVENT_BUS.register(new CapabilityHooks());

		// 合成的 PlayerContainerEvent.Open/Close（FML 总线：TickEvent 在这里）
		FMLCommonHandler.instance().bus().register(new ContainerEventHooks());

		// 主线程任务队列
		Scheduling.init();

		LOGGER.info("BackportAPI Lib " + VERSION + " initialized");
	}

	/**
	 * 副手探测放到 init：preInit 阶段其他模组可能还没准备好，
	 * 此时用反射加载 Backhand 的类存在风险。
	 */
	@Mod.EventHandler
	public void init(FMLInitializationEvent event)
	{
		LOGGER.info("Offhand provider (Backhand) " + (Hands.hasOffhand() ? "detected" : "not installed - OFF_HAND will always be empty"));
	}
}
