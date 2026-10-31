package tschipp.carryon.common.config;

import net.minecraftforge.common.config.Config;

/**
 * 配置定义。
 *
 * <p>1.8 的 {@code @Config(modid=...)} 是"挂在类上"的，1.7.10 没有这套机制；
 * BackportAPI 的 {@code @Config} 是"挂在字段上"的，配合
 * {@code ConfigManager.sync(MODID, Configs.class)} 使用。
 *
 * <p>因此这里把原来的实例字段全部改成 <b>static</b>，嵌套静态类名即分类名。
 */
public class Configs
{
	public static class Settings
	{
		@Config.Comment("If the front of the Tile Entities should face the player or should face outward")
		public static boolean facePlayer = false;

		@Config.Comment("More complex Tile Entities slow down the player more")
		public static boolean heavyTiles = true;

		@Config.Comment("Allow all blocks to be picked up, not just Tile Entities")
		public static boolean pickupAllBlocks = false;

		@Config.Comment("Whether Blocks and Entities slow the creative player down when carried")
		public static boolean slownessInCreative = true;

		@Config.RangeDouble(min = 0)
		@Config.Comment("Maximum distance from where Blocks and Entities can be picked up")
		public static double maxDistance = 2.5;

		@Config.RangeDouble(min = 0, max = 10)
		@Config.Comment("Max width of entities that can be picked up in survival mode")
		public static float maxEntityWidth = 1.5f;

		@Config.RangeDouble(min = 0, max = 10)
		@Config.Comment("Max height of entities that can be picked up in survival mode")
		public static float maxEntityHeight = 1.5f;

		@Config.Comment("Whether hostile mobs should be able to picked up in survival mode")
		public static boolean pickupHostileMobs = false;

		@Config.Comment("Larger Entities slow down the player more")
		public static boolean heavyEntities = true;

		@Config.Comment("Slowness multiplier for blocks")
		public static double blockSlownessMultiplier = 1.0;

		@Config.Comment("Slowness multiplier for entities")
		public static double entitySlownessMultiplier = 1.0;

		@Config.Comment("Arms should render on sides when carrying")
		public static boolean renderArms = true;

		@Config.Comment("Allow babies to be carried even when adult mob is blacklisted (or not whitelisted)")
		public static boolean allowBabies = false;

		@Config.Comment("Use Whitelist instead of Blacklist for Blocks")
		public static boolean useWhitelistBlocks = false;

		@Config.Comment("Use Whitelist instead of Blacklist for Entities")
		public static boolean useWhitelistEntities = false;

		@Config.Comment("Whether the player can hit blocks and entities while carrying or not")
		public static boolean hitWhileCarrying = false;

		@Config.Comment("Whether the player drops the carried object when hit or not")
		public static boolean dropCarriedWhenHit = false;

		@Config.Comment("Use custom Pickup Scripts. Having this set to false, will not allow you to run scripts, but will save you some performance")
		public static boolean useScripts = false;

		@Config.Comment("Allows entities to be stacked using Carry On")
		public static boolean stackableEntities = true;

		@Config.RangeInt(min = 1)
		@Config.Comment("Maximum stack limit for entities")
		public static int maxEntityStackLimit = 10;

		@Config.Comment("Whether entities' size matters when stacking or not")
		public static boolean entitySizeMattersStacking = true;

		// ---------- 渲染位置（可在游戏内配置界面实时调整） ----------

		@Config.RangeDouble(min = 0, max = 3)
		@Config.Comment("Third person: carried object center height above the player's feet, in blocks")
		public static double carryOffsetY = 0.94;

		@Config.RangeDouble(min = 0, max = 2)
		@Config.Comment("Third person: carried object distance in front of the body, in blocks")
		public static double carryOffsetZ = 0.4;

		@Config.RangeDouble(min = 0.1, max = 2)
		@Config.Comment("Third person: carried object scale")
		public static double carryScale = 0.6;

		@Config.RangeDouble(min = -4, max = 2)
		@Config.Comment("First person: carried object height relative to the eye, in blocks")
		public static double handOffsetY = -1.62;

		@Config.RangeDouble(min = -5, max = -0.1)
		@Config.Comment("First person: carried object distance in front of the eye (negative = forward)")
		public static double handOffsetZ = -2.5;

		@Config.RangeDouble(min = 0.1, max = 6)
		@Config.Comment("First person: carried object scale (vanilla first-person item space is 2.5)")
		public static double handScale = 2.5;
	}

	public static class WhiteList
	{
		@Config.Comment("Entities that CAN be picked up")
		public static String[] allowedEntities = new String[] {};

		@Config.Comment("Blocks that CAN be picked up")
		public static String[] allowedBlocks = new String[] {};
	}

	public static class Blacklist
	{
		@Config.Comment("Tile Entities that cannot be picked up")
		public static String[] forbiddenTiles = new String[] {
				"minecraft:end_portal",
				"minecraft:double_plant",
				"minecraft:bed",
				"minecraft:wooden_door",
				"minecraft:iron_door",
				"minecraft:spruce_door",
				"minecraft:birch_door",
				"minecraft:jungle_door",
				"minecraft:acacia_door",
				"minecraft:dark_oak_door",
				"minecraft:waterlily",
				"minecraft:cake",
				"ic2:*",
				"forestry:*",
				"tconstruct:*",
				"botania:*",
				"storagedrawers:framingtable",
				"opencomputers:*",
				"colossalchests:*"
		};

		@Config.Comment("Entities that cannot be picked up")
		public static String[] forbiddenEntities = new String[] {
				"EnderCrystal",
				"EnderDragon",
				"Ghast",
				"LeashKnot",
				"ItemFrame",
				"Painting"
		};
	}

	public static class ModelOverrides
	{
		@Config.Comment({
				"Model Overrides based on NBT or on Meta. Advanced Users Only!",
				"Format: sourceBlock[;meta][{nbt}] -> [(block)|(item)]target[;meta][{nbt}]",
				"Left side meta 0 or omitted means 'any meta'.",
				"No prefix defaults to (item)."
		})
		public static String[] modelOverrides = new String[] {
				"minecraft:lit_furnace->minecraft:furnace",
				"minecraft:hopper->(block)minecraft:hopper",
				"minecraft:unpowered_comparator->(block)minecraft:unpowered_comparator",
				"minecraft:powered_comparator->(block)minecraft:powered_comparator",
				"minecraft:unpowered_repeater->(block)minecraft:unpowered_repeater",
				"minecraft:powered_repeater->(block)minecraft:powered_repeater",
				"minecraft:cauldron->(block)minecraft:cauldron",
				"minecraft:flower_pot->(block)minecraft:flower_pot",
				"minecraft:reeds->(block)minecraft:reeds",
				"minecraft:brewing_stand->(item)minecraft:brewing_stand",
				"minecraft:tallgrass;1->(item)minecraft:tallgrass;1",
				"minecraft:tallgrass;2->(item)minecraft:tallgrass;2",
				"minecraft:leaves2->(item)minecraft:leaves2",
				"minecraft:daylight_detector_inverted->minecraft:daylight_detector"
		};
	}
}
