package tschipp.carryon.common.scripting;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import com.google.gson.JsonElement;
import com.google.gson.JsonIOException;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTException;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import tschipp.carryon.CarryOn;
import tschipp.carryon.common.config.Configs;

public class ScriptReader
{
	private static ArrayList<File> scripts = new ArrayList<File>();
	public static HashMap<Integer, CarryOnOverride> OVERRIDES = new HashMap<Integer, CarryOnOverride>();
	
	//public static HashSet<CarryOnOverride> OVERRIDES = new HashSet<CarryOnOverride>();

	public static void preInit(FMLPreInitializationEvent event)
	{
		CarryOn.CONFIGURATION_FILE = new File(event.getModConfigurationDirectory(), "carryon-scripts/");
		if (!CarryOn.CONFIGURATION_FILE.exists())
			CarryOn.CONFIGURATION_FILE.mkdir();

		for (File file : CarryOn.CONFIGURATION_FILE.listFiles())
		{
			if (file.getName().endsWith(".json"))
				scripts.add(file);
		}

	}

	public static void parseScripts() throws JsonIOException, JsonSyntaxException, FileNotFoundException, NBTException
	{
		if(!Configs.Settings.useScripts)
			return;
			
		for (File file : scripts)
		{
			boolean errored = false;
			int hash = file.getAbsolutePath().hashCode();

			JsonParser parser = new JsonParser();
			JsonObject json = (JsonObject) parser.parse(new FileReader(file.getAbsolutePath()));

			JsonObject object = (JsonObject) json.get("object");
			JsonObject conditions = (JsonObject) json.get("conditions");

			// 必须是 object + conditions。上游还接受"只有 object + render"的脚本，
			// 但移植版没有 render 定制（见 CarryOnOverride），
			// 那种脚本在这里等于"没有任何匹配条件"，会把所有方块都变成可搬运。
			if (object != null && conditions != null)
			{
				JsonObject block = (JsonObject) object.get("block");
				JsonObject entity = (JsonObject) object.get("entity");

				if ((block == null && entity == null) || (block != null && entity != null))
					errored = true;
				
				if (!errored)
				{
					CarryOnOverride override = new CarryOnOverride(file.getAbsolutePath());
					
					if (block != null)
					{
						override.setBlock(true);
						JsonElement name = block.get("name");
						JsonElement meta = block.get("meta");
						JsonElement material = block.get("material");
						JsonElement hardness = block.get("hardness");
						JsonElement resistance = block.get("resistance");
						JsonObject nbt = (JsonObject) block.get("nbt");

						if (name != null)
							override.setTypeNameBlock(name.getAsString());
						if (meta != null)
							override.setTypeMeta(meta.getAsString());
						if (material != null)
							override.setTypeMaterial(material.getAsString());
						if (hardness != null)
							override.setTypeHardness(hardness.getAsString());
						if (resistance != null)
							override.setTypeResistance(resistance.getAsString());
						if (nbt != null)
							override.setTypeBlockTag((NBTTagCompound) JsonToNBT.func_150315_a(nbt.toString()));
					}
					else
					{
						override.setEntity(true);
						JsonElement name = entity.get("name");
						JsonElement health = entity.get("health");
						JsonElement height = entity.get("height");
						JsonElement width = entity.get("width");
						JsonObject nbt = (JsonObject) entity.get("nbt");

						if (name != null)
							override.setTypeNameEntity(name.getAsString());
						if (health != null)
							override.setTypeHealth(health.getAsString());
						if (height != null)
							override.setTypeHeight(height.getAsString());
						if (width != null)
							override.setTypeWidth(width.getAsString());
						if (nbt != null)
							override.setTypeEntityTag((NBTTagCompound) JsonToNBT.func_150315_a(nbt.toString()));
					}

					if (conditions != null)
					{
						JsonElement achievement = conditions.get("achievement");
						JsonElement xp = conditions.get("xp");
						JsonElement gamemode = conditions.get("gamemode");
						JsonElement scoreboard = conditions.get("scoreboard");
						JsonElement position = conditions.get("position");
						JsonElement effects = conditions.get("effects");

						
						if(achievement != null)
							override.setConditionAchievement(achievement.getAsString());
						if(xp != null)
							override.setConditionXp(xp.getAsString());
						if(gamemode != null)
							override.setConditionGamemode(gamemode.getAsString());
						if(scoreboard != null)
							override.setConditionScoreboard(scoreboard.getAsString());
						if(position != null)
							override.setConditionPosition(position.getAsString());
						if(effects != null)
							override.setConditionEffects(effects.getAsString());
					}

					OVERRIDES.put(override.hashCode(), override);

				}
			}
		}
		
		CarryOn.LOGGER.info("Loaded " + OVERRIDES.size() + " carry-on script override(s)");
	}

	
	public static void reloadScripts()
	{
		scripts.clear();
		OVERRIDES.clear();
		
		for (File file : CarryOn.CONFIGURATION_FILE.listFiles())
		{
			if (file.getName().endsWith(".json"))
				scripts.add(file);
		}
		
		try
		{
			parseScripts();
		}
		catch (JsonIOException | JsonSyntaxException | FileNotFoundException | NBTException e)
		{
			e.printStackTrace();
		}
	}

}
