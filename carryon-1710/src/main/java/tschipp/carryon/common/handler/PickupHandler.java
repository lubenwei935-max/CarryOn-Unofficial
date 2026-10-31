package tschipp.carryon.common.handler;

import net.backportapi.util.Worlds;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityAgeable;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.entity.passive.EntityTameable;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.world.BlockEvent;
import tschipp.carryon.common.config.Configs;
import tschipp.carryon.common.item.ItemTile;
import tschipp.carryon.common.scripting.CarryOnOverride;
import tschipp.carryon.common.scripting.ScriptChecker;

public class PickupHandler
{
	public static boolean canPlayerPickUpBlock(EntityPlayer player, TileEntity tile, World world, BlockPos pos)
	{
		IBlockState state = Worlds.getBlockState(world, pos);
		Block block = state.getBlock();

		player.closeScreen();

		NBTTagCompound tag = new NBTTagCompound();
		if (tile != null)
			tile.writeToNBT(tag);

		CarryOnOverride override = ScriptChecker.inspectBlock(state, world, pos, tag);
		if (override != null)
		{
			return ScriptChecker.fulfillsConditions(override, player) && handleProtections((EntityPlayerMP) player, world, pos, state);
		}

		if (Configs.Settings.useWhitelistBlocks)
		{
			if (!ListHandler.isAllowed(block))
				return false;
		}
		else
		{
			if (ListHandler.isForbidden(block))
				return false;
		}

		// 1.7.10 的 getBlockHardness 直接收世界坐标
		if (block.getBlockHardness(world, pos.getX(), pos.getY(), pos.getZ()) != -1 || player.capabilities.isCreativeMode)
		{
			double distance = pos.distanceSqToCenter(player.posX, player.posY + 0.5, player.posZ);

			if (distance < Math.pow(Configs.Settings.maxDistance, 2))
			{
				if (!ItemTile.isLocked(pos, world))
				{
					if (Configs.Settings.pickupAllBlocks || tile != null)
						return handleProtections((EntityPlayerMP) player, world, pos, state);
				}
			}
		}

		return false;
	}

	public static boolean canPlayerPickUpEntity(EntityPlayer player, Entity toPickUp)
	{
		BlockPos pos = new BlockPos(toPickUp.posX, toPickUp.posY, toPickUp.posZ);

		if (toPickUp instanceof EntityPlayer)
			return false;

		CarryOnOverride override = ScriptChecker.inspectEntity(toPickUp);
		if (override != null)
			return ScriptChecker.fulfillsConditions(override, player) && handleProtections((EntityPlayerMP) player, toPickUp);

		// 允许搬幼崽（原版逻辑里这段有个写反的条件，这里修正为"满足距离才允许"）
		if (toPickUp instanceof EntityAgeable && Configs.Settings.allowBabies)
		{
			EntityAgeable living = (EntityAgeable) toPickUp;
			if (living.getGrowingAge() < 0 || living.isChild())
			{
				double distance = pos.distanceSqToCenter(player.posX, player.posY + 0.5, player.posZ);
				if (distance < Math.pow(Configs.Settings.maxDistance, 2))
				{
					if (toPickUp instanceof EntityTameable)
					{
						EntityTameable tame = (EntityTameable) toPickUp;
						// 1.7.10 的 getOwner() 直接返回实体，没有 UUID 比较
						EntityLivingBase owner = tame.getOwner();
						if (owner != null && owner != player)
							return false;
					}
					return handleProtections((EntityPlayerMP) player, toPickUp);
				}
			}
		}

		if (Configs.Settings.useWhitelistEntities)
		{
			if (!ListHandler.isAllowed(toPickUp))
				return false;
		}
		else
		{
			if (ListHandler.isForbidden(toPickUp))
				return false;
		}

		boolean hostileOk = Configs.Settings.pickupHostileMobs || !toPickUp.isCreatureType(EnumCreatureType.monster, false) || player.capabilities.isCreativeMode;

		if (!hostileOk)
			return false;

		if (!(toPickUp.height <= Configs.Settings.maxEntityHeight && toPickUp.width <= Configs.Settings.maxEntityWidth || player.capabilities.isCreativeMode))
			return false;

		double distance = pos.distanceSqToCenter(player.posX, player.posY + 0.5, player.posZ);
		if (distance >= Math.pow(Configs.Settings.maxDistance, 2))
			return false;

		if (toPickUp instanceof EntityTameable)
		{
			EntityTameable tame = (EntityTameable) toPickUp;
			EntityLivingBase owner = tame.getOwner();
			if (owner != null && owner != player)
				return false;
		}

		return handleProtections((EntityPlayerMP) player, toPickUp);
	}

	private static boolean handleProtections(EntityPlayerMP player, World world, BlockPos pos, IBlockState state)
	{
		BlockEvent.BreakEvent event = new BlockEvent.BreakEvent(pos.getX(), pos.getY(), pos.getZ(), world, state.getBlock(), state.getMeta(), player);
		MinecraftForge.EVENT_BUS.post(event);
		return !event.isCanceled();
	}

	private static boolean handleProtections(EntityPlayerMP player, Entity entity)
	{
		AttackEntityEvent event = new AttackEntityEvent(player, entity);
		MinecraftForge.EVENT_BUS.post(event);
		return !event.isCanceled();
	}
}
