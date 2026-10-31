package tschipp.carryon.common.handler;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import tschipp.carryon.common.config.Configs;

public class ListHandler
{
	public static List<String> FORBIDDEN_TILES;
	public static List<String> FORBIDDEN_ENTITIES;
	public static List<String> ALLOWED_ENTITIES;
	public static List<String> ALLOWED_TILES;

	/** 1.7.10 没有 getRegistryName()，用注册表反查。 */
	private static String blockName(Block block)
	{
		String name = Block.blockRegistry.getNameForObject(block);
		return name == null ? "" : name;
	}

	private static boolean matches(List<String> list, String name)
	{
		if (list == null || name == null)
			return false;

		if (list.contains(name))
			return true;

		for (String s : list)
		{
			if (s.contains("*") && name.contains(s.replace("*", "")))
				return true;
		}
		return false;
	}

	public static boolean isForbidden(Block block)
	{
		return matches(FORBIDDEN_TILES, blockName(block));
	}

	public static boolean isForbidden(Entity entity)
	{
		String name = EntityList.getEntityString(entity);
		return name != null && FORBIDDEN_ENTITIES != null && FORBIDDEN_ENTITIES.contains(name);
	}

	public static boolean isAllowed(Entity entity)
	{
		String name = EntityList.getEntityString(entity);
		return name != null && ALLOWED_ENTITIES != null && ALLOWED_ENTITIES.contains(name);
	}

	public static boolean isAllowed(Block block)
	{
		return matches(ALLOWED_TILES, blockName(block));
	}

	@SuppressWarnings("unchecked")
	private static List<String> expandEntities(String[] patterns)
	{
		List<String> out = new ArrayList<String>();
		if (patterns == null)
			return out;

		// 1.7.10 的实体名表是 stringToClassMapping（公开 Map）
		java.util.Set<String> known = EntityList.stringToClassMapping.keySet();

		for (String pattern : patterns)
		{
			if (pattern == null)
				continue;

			if (pattern.contains("*"))
			{
				String prefix = pattern.replace("*", "");
				for (String name : known)
				{
					if (name.contains(prefix))
						out.add(name);
				}
			}
			out.add(pattern);
		}
		return out;
	}

	public static void initForbiddenTiles()
	{
		FORBIDDEN_TILES = new ArrayList<String>();
		for (String s : Configs.Blacklist.forbiddenTiles)
			FORBIDDEN_TILES.add(s);

		FORBIDDEN_ENTITIES = expandEntities(Configs.Blacklist.forbiddenEntities);
		ALLOWED_ENTITIES = expandEntities(Configs.WhiteList.allowedEntities);

		ALLOWED_TILES = new ArrayList<String>();
		for (String s : Configs.WhiteList.allowedBlocks)
			ALLOWED_TILES.add(s);
	}
}
