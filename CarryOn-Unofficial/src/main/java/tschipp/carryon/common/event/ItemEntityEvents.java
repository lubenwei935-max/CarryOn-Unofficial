package tschipp.carryon.common.event;

import cpw.mods.fml.common.eventhandler.Event.Result;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.network.NetworkRegistry.TargetPoint;
import net.backportapi.hand.Hands;
import net.backportapi.util.Worlds;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.passive.EntityHorse;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent;
import net.minecraftforge.event.entity.player.EntityInteractEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import tschipp.carryon.CarryOn;
import tschipp.carryon.client.keybinds.CarryOnKeybinds;
import tschipp.carryon.common.config.Configs;
import tschipp.carryon.common.handler.PickupHandler;
import tschipp.carryon.common.handler.RegistrationHandler;
import tschipp.carryon.common.item.ItemEntity;
import tschipp.carryon.common.scripting.CarryOnOverride;
import tschipp.carryon.common.scripting.ScriptChecker;
import tschipp.carryon.network.client.CarrySlotPacket;

public class ItemEntityEvents
{
	@SubscribeEvent(priority = EventPriority.HIGH)
	public void onBlockClick(PlayerInteractEvent event)
	{
		if (event.action != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK)
			return;

		EntityPlayer player = event.entityPlayer;
		ItemStack stack = Hands.getMainhand(player);

		if (stack == null || stack.getItem() != RegistrationHandler.itemEntity || !ItemEntity.hasEntityData(stack))
			return;

		// 与 ItemEvents 同理：接管右键，避免未潜行时被方块交互抢先
		event.useBlock = Result.DENY;
		event.useItem = Result.DENY;

		if (!event.world.isRemote)
			((ItemEntity) stack.getItem()).placeFromItem(stack, player, event.world, event.x, event.y, event.z, event.face);
	}

	@SubscribeEvent(priority = EventPriority.HIGH)
	public void onItemDropped(EntityJoinWorldEvent event)
	{
		Entity e = event.entity;
		World world = event.world;

		if (!(e instanceof EntityItem))
			return;

		EntityItem eitem = (EntityItem) e;
		ItemStack stack = eitem.getEntityItem();
		if (stack == null)
			return;

		if (stack.getItem() != RegistrationHandler.itemEntity || !ItemEntity.hasEntityData(stack))
			return;

		BlockPos pos = new BlockPos(eitem.posX, eitem.posY, eitem.posZ);
		Entity entity = ItemEntity.getEntity(stack, world);

		if (entity != null)
		{
			entity.setPosition(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
			world.spawnEntityInWorld(entity);
		}

		ItemEntity.clearEntityData(stack);
		eitem.setEntityItemStack(null);
	}

	/**
	 * 1.7.10 没有 PlayerInteractEvent.EntityInteract，实体交互是独立事件类。
	 */
	@SubscribeEvent(priority = EventPriority.HIGH)
	public void onEntityRightClick(EntityInteractEvent event)
	{
		EntityPlayer player = event.entityPlayer;

		if (!(player instanceof EntityPlayerMP))
			return;

		ItemStack main = Hands.getMainhand(player);
		ItemStack off = Hands.getOffhand(player);
		World world = player.worldObj;
		Entity entity = event.target;
		BlockPos pos = new BlockPos(entity.posX, entity.posY, entity.posZ);

		if (main == null && off == null && CarryOnKeybinds.isKeyPressed(player))
		{
			ItemStack stack = new ItemStack(RegistrationHandler.itemEntity);

			if (entity.hurtResistantTime != 0)
				return;

			if (entity instanceof EntityAnimal)
				((EntityAnimal) entity).clearLeashed(true, true);

			if (!PickupHandler.canPlayerPickUpEntity(player, entity))
				return;

			if (!ItemEntity.storeEntityData(entity, world, stack))
				return;

			// 1.7.10 没有 IItemHandler；模组实体若有背包则走 IInventory
			if (entity instanceof IInventory)
			{
				IInventory inv = (IInventory) entity;
				for (int i = 0; i < inv.getSizeInventory(); i++)
					inv.setInventorySlotContents(i, null);
			}

			CarryOnOverride override = ScriptChecker.inspectEntity(entity);
			int overrideHash = override == null ? 0 : override.hashCode();

			CarryOn.network.sendToAllAround(new CarrySlotPacket(player.inventory.currentItem, player.getEntityId(), overrideHash), new TargetPoint(world.provider.dimensionId, player.posX, player.posY, player.posZ, 256));
			entity.setDead();
			Hands.setMainhand(player, stack);
			event.setCanceled(true);
		}
		else if (main != null && main.getItem() == RegistrationHandler.itemEntity && ItemEntity.hasEntityData(main) && !CarryOnKeybinds.isKeyPressed(player) && Configs.Settings.stackableEntities)
		{
			stackEntityOn(player, world, entity, pos, main);
		}
	}

	/** 把手里抱着的生物叠到目标生物背上。 */
	private void stackEntityOn(EntityPlayer player, World world, Entity entity, BlockPos pos, ItemStack main)
	{
		Entity entityHeld = ItemEntity.getEntity(main, world);

		if (entity.hurtResistantTime != 0 || !(entityHeld instanceof EntityLivingBase))
			return;

		if (world.isRemote || entityHeld.getUniqueID().equals(entity.getUniqueID()) || entityHeld.isDead || entity.isDead)
			return;

		double sizeHeldEntity = entityHeld.height * entityHeld.width;
		double distance = pos.distanceSqToCenter(player.posX, player.posY + 0.5, player.posZ);

		Entity lowestEntity = getLowestRidingEntity(entity);
		int numPassengers = getAllPassengers(lowestEntity);

		if (numPassengers >= Configs.Settings.maxEntityStackLimit - 1)
		{
			world.playSoundEffect(player.posX, player.posY, player.posZ, "note.bass", 0.5F, 1.5F);
			return;
		}

		Entity topEntity = getTopPassenger(lowestEntity);
		double sizeEntity = topEntity.height * topEntity.width;

		if (Configs.Settings.entitySizeMattersStacking && sizeHeldEntity > sizeEntity)
		{
			world.playSoundEffect(player.posX, player.posY, player.posZ, "note.bass", 0.5F, 1.5F);
			return;
		}

		if (topEntity instanceof EntityHorse)
			((EntityHorse) topEntity).setHorseTamed(true);

		if (distance < 6)
		{
			double tempX = entity.posX;
			double tempY = entity.posY;
			double tempZ = entity.posZ;

			entityHeld.setPosition(tempX, tempY + 2.6, tempZ);
			world.spawnEntityInWorld(entityHeld);
			entityHeld.mountEntity(topEntity);
			entityHeld.setPosition(tempX, tempY, tempZ);
		}
		else
		{
			entityHeld.setPosition(entity.posX, entity.posY, entity.posZ);
			world.spawnEntityInWorld(entityHeld);
			entityHeld.mountEntity(topEntity);
		}

		// 挂载失败时（目标已有乘客、被拒绝等）把刚生成的实体收回去，
		// 否则它会原地站着，看起来就像"复制了一只"。
		if (entityHeld.ridingEntity != topEntity)
		{
			entityHeld.setDead();
			world.playSoundEffect(player.posX, player.posY, player.posZ, "note.bass", 0.5F, 1.5F);
			return;
		}

		ItemEntity.clearEntityData(main);
		Hands.setMainhand(player, null);
		CarryOn.network.sendToAllAround(new CarrySlotPacket(9, player.getEntityId()), new TargetPoint(world.provider.dimensionId, player.posX, player.posY, player.posZ, 256));
		world.playSoundEffect(player.posX, player.posY, player.posZ, "random.pop", 0.5F, 1.5F);
	}

	/** 1.7.10 用 ridingEntity 链，没有 getLowestRidingEntity()。 */
	public static Entity getLowestRidingEntity(Entity entity)
	{
		Entity current = entity;
		while (current.ridingEntity != null)
			current = current.ridingEntity;
		return current;
	}

	public static int getAllPassengers(Entity entity)
	{
		int passengers = 0;
		Entity current = entity;
		while (current.riddenByEntity != null)
		{
			current = current.riddenByEntity;
			passengers++;
		}
		return passengers;
	}

	public static Entity getTopPassenger(Entity entity)
	{
		Entity current = entity;
		while (current.riddenByEntity != null)
			current = current.riddenByEntity;
		return current;
	}

	@SubscribeEvent
	public void onLivingUpdate(LivingUpdateEvent event)
	{
		EntityLivingBase entity = event.entityLiving;
		World world = entity.worldObj;
		ItemStack main = entity.getHeldItem();

		if (main == null || main.getItem() != RegistrationHandler.itemEntity || !ItemEntity.hasEntityData(main))
			return;

		BlockPos pos = new BlockPos(entity.posX, entity.posY, entity.posZ);
		BlockPos below = pos.offset(EnumFacing.DOWN);

		Material at = Worlds.getBlock(world, pos).getMaterial();
		Material under = Worlds.getBlock(world, below).getMaterial();

		if (at == Material.water || under == Material.water)
		{
			Entity contained = ItemEntity.getEntity(main, world);
			if (contained != null)
				entity.addVelocity(0, -0.01 * contained.height * contained.width, 0);
		}
	}
}
