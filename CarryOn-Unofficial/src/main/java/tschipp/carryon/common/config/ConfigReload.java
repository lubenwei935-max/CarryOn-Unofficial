package tschipp.carryon.common.config;

import cpw.mods.fml.client.event.ConfigChangedEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.backportapi.forge.config.ConfigManager;
import tschipp.carryon.CarryOn;
import tschipp.carryon.common.handler.ListHandler;
import tschipp.carryon.common.handler.ModelOverridesHandler;

/**
 * 配置界面保存后重新同步，使改动<b>无需重启</b>即可生效。
 *
 * <p>搬运物的位置/缩放参数都做成了配置项，这样观感微调可以直接在游戏内完成。
 */
public class ConfigReload
{
	@SubscribeEvent
	public void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event)
	{
		if (!CarryOn.MODID.equals(event.modID))
			return;

		ConfigManager.sync(CarryOn.MODID, Configs.class);
		ListHandler.initForbiddenTiles();
		ModelOverridesHandler.initOverrides();

		CarryOn.LOGGER.info("Carry On config reloaded");
	}
}
