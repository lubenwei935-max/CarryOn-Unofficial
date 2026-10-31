package tschipp.carryon.common.scripting;

import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import tschipp.carryon.common.config.Configs;
import tschipp.carryon.common.helper.ScriptParseHelper;

public class ScriptChecker
{
	@Nullable
	public static CarryOnOverride inspectBlock(IBlockState state, World world, BlockPos pos, @Nullable NBTTagCompound tag)
	{
		if (!Configs.Settings.useScripts)
			return null;

		Block block = state.getBlock();
		int meta = state.getMeta();
		Material material = block.getMaterial();
		float hardness = block.getBlockHardness(world, pos.getX(), pos.getY(), pos.getZ());
		float resistance = block.getExplosionResistance(null);
		NBTTagCompound nbt = tag;

		for (CarryOnOverride override : ScriptReader.OVERRIDES.values())
		{
			if (override.isBlock())
			{
				if (matchesAll(override, block, meta, material, hardness, resistance, nbt))
					return override;
			}
		}

		return null;
	}

	@Nullable
	public static CarryOnOverride inspectEntity(Entity entity)
	{
		if (!Configs.Settings.useScripts)
			return null;

		String name = EntityList.getEntityString(entity).toString();
		float height = entity.height;
		float width = entity.width;
		float health = entity instanceof EntityLivingBase ? ((EntityLivingBase) entity).getHealth() : 0.0f;
		NBTTagCompound tag = new NBTTagCompound();
		entity.writeToNBT(tag);

		for (CarryOnOverride override : ScriptReader.OVERRIDES.values())
		{
			if (override.isEntity())
			{
				if (matchesAll(override, name, height, width, health, tag))
					return override;
			}
		}

		return null;
	}

	public static boolean matchesAll(CarryOnOverride override, String name, float height, float width, float health, NBTTagCompound tag)
	{
		boolean matchname = override.getTypeNameEntity() == null ? true : name.equals(override.getTypeNameEntity());
		boolean matchheight = ScriptParseHelper.matches(height, override.getTypeHeight());
		boolean matchwidth = ScriptParseHelper.matches(width, override.getTypeWidth());
		boolean matchhealth = ScriptParseHelper.matches(health, override.getTypeHealth());
		boolean matchnbt = ScriptParseHelper.matches(tag, override.getTypeEntityTag());

		return (matchname && matchheight && matchwidth && matchhealth && matchnbt);
	}

	public static boolean matchesAll(CarryOnOverride override, Block block, int meta, Material material, float hardness, float resistance, NBTTagCompound nbt)
	{
		boolean matchnbt = ScriptParseHelper.matches(nbt, override.getTypeBlockTag());
		boolean matchblock = ScriptParseHelper.matches(block, override.getTypeNameBlock());
		boolean matchmeta = ScriptParseHelper.matches(meta, override.getTypeMeta());
		boolean matchmaterial = ScriptParseHelper.matches(material, override.getTypeMaterial());
		boolean matchhardness = ScriptParseHelper.matches(hardness, override.getTypeHardness());
		boolean matchresistance = ScriptParseHelper.matches(resistance, override.getTypeResistance());

		return (matchnbt && matchblock && matchmeta && matchmaterial && matchhardness && matchresistance);
	}

	public static boolean fulfillsConditions(CarryOnOverride override, EntityPlayer player)
	{
		boolean achievement = ScriptParseHelper.getAchievementFromString(override.getConditionAchievement()) == null ? true : ((EntityPlayerMP) player).func_147099_x().hasAchievementUnlocked(ScriptParseHelper.getAchievementFromString(override.getConditionAchievement()));
		boolean gamemode = ScriptParseHelper.matches(((EntityPlayerMP) player).theItemInWorldManager.getGameType().getID(), override.getConditionGamemode());
		boolean position = ScriptParseHelper.matches(new BlockPos(player.posX, player.posY, player.posZ), override.getConditionPosition());
		boolean xp = ScriptParseHelper.matches(player.experienceLevel, override.getConditionXp());
		boolean scoreboard = ScriptParseHelper.matchesScore(player, override.getConditionScoreboard());
		boolean effects = ScriptParseHelper.hasEffects(player, override.getConditionEffects());
		
		return (achievement && gamemode && position && xp && scoreboard && effects);
	}

	/**
	 * 记录服务端判定命中的脚本（{@code CarrySlotPacket} 带过来的 hash）。
	 *
	 * <p>客户端目前只做记录：1.10 上游用它来选渲染参数，移植版没有那套
	 * 按脚本定制渲染的功能（见 README），所以这里只是同步状态，
	 * 放置/丢弃时会被 {@code removeTag("overrideKey")} 清掉。
	 */
	public static void setCarryOnOverride(EntityPlayer player, int i)
	{
		NBTTagCompound tag = player.getEntityData();

		if (tag != null)
			tag.setInteger("overrideKey", i);
	}
	
}
