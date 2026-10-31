package tschipp.carryon.client.gui;

import java.util.ArrayList;
import java.util.List;

import net.minecraftforge.common.config.ConfigElement;
import cpw.mods.fml.client.config.GuiConfig;
import cpw.mods.fml.client.config.IConfigElement;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.ConfigManager;
import tschipp.carryon.CarryOn;

/**
 * 1.7.10 配置界面。
 *
 * <p>1.8 通过反射拿 {@code ConfigManager.CONFIGS}；BackportAPI 的
 * {@link ConfigManager} 直接提供 {@code getConfig(modid)}，不需要反射。
 */
public class GuiConfigCarryOn extends GuiConfig
{
	private static final String LANG_PREFIX = CarryOn.MODID + ".category.";

	public GuiConfigCarryOn(GuiScreen parent)
	{
		super(parent, getConfigElements(), CarryOn.MODID, false, false, "CarryOn-Unofficial Configuration");
	}

	private static List<IConfigElement> getConfigElements()
	{
		Configuration configuration = ConfigManager.getConfig(CarryOn.MODID);
		List<IConfigElement> elements = new ArrayList<IConfigElement>();

		String[] categories = new String[] { "Settings", "WhiteList", "Blacklist", "ModelOverrides" };
		for (String name : categories)
		{
			ConfigCategory category = configuration.getCategory(name);
			category.setLanguageKey(LANG_PREFIX + category.getName());
			elements.add(new ConfigElement(category));
		}
		return elements;
	}
}
