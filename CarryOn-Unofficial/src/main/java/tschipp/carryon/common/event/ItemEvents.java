package tschipp.carryon.common.event;

import cpw.mods.fml.common.eventhandler.Event.Result;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent;
import cpw.mods.fml.common.network.NetworkRegistry.TargetPoint;
import net.backportapi.util.Chat;
import net.backportapi.hand.Hands;
import net.backportapi.util.Worlds;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.BreakSpeed;
import net.minecraftforge.event.entity.player.PlayerEvent.StartTracking;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent.BreakEvent;
import tschipp.carryon.CarryOn;
import tschipp.carryon.common.config.Configs;
import tschipp.carryon.common.handler.PickupGuard;
import tschipp.carryon.common.handler.PickupHandler;
import tschipp.carryon.common.handler.RegistrationHandler;
import tschipp.carryon.common.item.ItemEntity;
import tschipp.carryon.common.item.ItemTile;
import tschipp.carryon.common.scripting.CarryOnOverride;
import tschipp.carryon.common.scripting.ScriptChecker;
import tschipp.carryon.network.client.CarrySlotPacket;

public class ItemEvents
{
	@SubscribeEvent(priority = EventPriority.HIGH)
	public void onBlockClick(PlayerInteractEvent event)
	{
		if (event.action != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK)
			return;

		EntityPlayer player = event.entityPlayer;
		ItemStack stack = Hands.getMainhand(player);

		if (stack == null || stack.getItem() != RegistrationHandler.itemTile || !ItemTile.hasTileData(stack))
			return;

		// 完全接管这次右键：既不让方块交互（打开箱子、按按钮…）抢走，
		// 也不让原版再调 onItemUse。
		//
		// 原版在玩家"未潜行"时会先调用方块的 onBlockActivated，一旦它返回 true
		// 就再也不会走到物品的 onItemUse —— 表现就是"只有蹲下右键才能放下"。
		event.useBlock = Result.DENY;
		event.useItem = Result.DENY;

		if (!event.world.isRemote)
			((ItemTile) stack.getItem()).placeFromItem(stack, player, event.world, event.x, event.y, event.z, event.face);
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public void onItemDropped(EntityJoinWorldEvent event)
	{
		// 抱起方块的那一瞬间，breakBlock 会为某些方块（林业工作台就是典型）再生成一个
		// "掉落本体"的 EntityItem。它和已经存进携带物品 NBT 的是同一份东西，
		// 不拦掉就是"抱起 1 个、净赚 1 个"。这里在实体加入世界的最早时机拦掉。
		if (PickupGuard.trySuppress(event))
			return;

		Entity e = event.entity;
		World world = event.world;

		if (!(e instanceof EntityItem))
			return;

		EntityItem eitem = (EntityItem) e;
		ItemStack stack = eitem.getEntityItem();
		if (stack == null)
			return;

		Item item = stack.getItem();
		if (item != RegistrationHandler.itemTile || !ItemTile.hasTileData(stack))
			return;

		BlockPos pos = new BlockPos(eitem.posX, eitem.posY, eitem.posZ);
		BlockPos finalPos = pos;
		Block block = ItemTile.getBlock(stack);

		if (block == null)
			return;

		if (!Worlds.isReplaceable(world, pos) || !Worlds.canPlaceBlockAt(block, world, pos))
		{
			for (EnumFacing facing : net.backportapi.util.Facing.VALUES)
			{
				BlockPos offsetPos = pos.offset(facing);
				if (Worlds.isReplaceable(world, offsetPos) && Worlds.canPlaceBlockAt(block, world, offsetPos))
				{
					finalPos = offsetPos;
					break;
				}
			}
		}

		IBlockState dropState = ItemTile.getBlockState(stack);
		Worlds.setBlockState(world, finalPos, dropState);

		// 同 ItemTile.placeFromItem：setBlock 会回调 onBlockAdded，箱子在那里面
		// 会把 meta 重置成 3（朝南）。这里没有"放置者"可言，保持搬起时的朝向即可。
		if (world.getBlockMetadata(finalPos.getX(), finalPos.getY(), finalPos.getZ()) != dropState.getMeta())
			world.setBlockMetadataWithNotify(finalPos.getX(), finalPos.getY(), finalPos.getZ(), dropState.getMeta(), 3);

		TileEntity tile = Worlds.getTileEntity(world, finalPos);
		if (tile != null)
		{
			NBTTagCompound data = ItemTile.getTileData(stack);
			if (data != null)
				tile.readFromNBT(data);
			tile.xCoord = finalPos.getX();
			tile.yCoord = finalPos.getY();
			tile.zCoord = finalPos.getZ();
		}

		ItemTile.clearTileData(stack);
		eitem.setEntityItemStack(null);
	}

	@SubscribeEvent
	public void onPlayerLogin(PlayerLoggedInEvent event)
	{
		if (!(event.player instanceof EntityPlayer))
			return;

		EntityPlayer player = event.player;
		World world = player.worldObj;
		ItemStack carried = Hands.getMainhand(player);

		if (carried == null)
			return;

		if (carried.getItem() != RegistrationHandler.itemTile && carried.getItem() != RegistrationHandler.itemEntity)
			return;

		CarryOnOverride override;
		if (carried.getItem() == RegistrationHandler.itemTile)
			override = ScriptChecker.inspectBlock(ItemTile.getBlockState(carried), world, new BlockPos(player.posX, player.posY, player.posZ), ItemTile.getTileData(carried));
		else
			override = ScriptChecker.inspectEntity(ItemEntity.getEntity(carried, world));

		int hash = override == null ? 0 : override.hashCode();
		CarryOn.network.sendTo(new CarrySlotPacket(player.inventory.currentItem, player.getEntityId(), hash), (EntityPlayerMP) player);
	}

	@SubscribeEvent
	public void onEntityStartTracking(StartTracking event)
	{
		Entity e = event.target;
		EntityPlayer tracker = event.entityPlayer;

		if (!(e instanceof EntityPlayer) || !(tracker instanceof EntityPlayerMP))
			return;

		EntityPlayer player = (EntityPlayer) e;
		World world = player.worldObj;
		ItemStack carried = Hands.getMainhand(player);

		if (carried == null)
			return;

		if (carried.getItem() != RegistrationHandler.itemTile && carried.getItem() != RegistrationHandler.itemEntity)
			return;

		CarryOnOverride override;
		if (carried.getItem() == RegistrationHandler.itemTile)
			override = ScriptChecker.inspectBlock(ItemTile.getBlockState(carried), world, new BlockPos(player.posX, player.posY, player.posZ), ItemTile.getTileData(carried));
		else
			override = ScriptChecker.inspectEntity(ItemEntity.getEntity(carried, world));

		int hash = override == null ? 0 : override.hashCode();
		CarryOn.network.sendTo(new CarrySlotPacket(player.inventory.currentItem, player.getEntityId(), hash), (EntityPlayerMP) tracker);
	}

	@SubscribeEvent
	public void harvestSpeed(BreakSpeed event)
	{
		EntityPlayer player = event.entityPlayer;
		if (player == null || Configs.Settings.hitWhileCarrying)
			return;

		ItemStack stack = Hands.getMainhand(player);
		if (stack != null && (stack.getItem() == RegistrationHandler.itemTile || stack.getItem() == RegistrationHandler.itemEntity))
			event.newSpeed = 0;
	}

	@SubscribeEvent
	public void attackEntity(AttackEntityEvent event)
	{
		EntityPlayer player = event.entityPlayer;
		if (player == null || Configs.Settings.hitWhileCarrying)
			return;

		ItemStack stack = Hands.getMainhand(player);
		if (stack != null && (stack.getItem() == RegistrationHandler.itemTile || stack.getItem() == RegistrationHandler.itemEntity))
			event.setCanceled(true);
	}

	@SubscribeEvent
	public void harvestSpeed(BreakEvent event)
	{
		EntityPlayer player = event.getPlayer();
		if (player == null || Configs.Settings.hitWhileCarrying)
			return;

		ItemStack stack = Hands.getMainhand(player);
		if (stack != null && (stack.getItem() == RegistrationHandler.itemTile || stack.getItem() == RegistrationHandler.itemEntity))
			event.setCanceled(true);
	}

	@SubscribeEvent
	public void playerAttack(LivingAttackEvent event)
	{
		EntityLivingBase eliving = event.entityLiving;

		if (!(eliving instanceof EntityPlayer) || !Configs.Settings.dropCarriedWhenHit)
			return;

		EntityPlayer player = (EntityPlayer) eliving;
		ItemStack stack = Hands.getMainhand(player);

		if (stack == null || (stack.getItem() != RegistrationHandler.itemTile && stack.getItem() != RegistrationHandler.itemEntity))
			return;

		if (!player.worldObj.isRemote)
		{
			Hands.setMainhand(player, null);
			EntityItem item = new EntityItem(player.worldObj, player.posX, player.posY, player.posZ, stack);
			CarryOn.network.sendToAllAround(new CarrySlotPacket(9, player.getEntityId()), new TargetPoint(player.worldObj.provider.dimensionId, player.posX, player.posY, player.posZ, 256));
			player.worldObj.spawnEntityInWorld(item);
		}
	}

	/**
	 * 主入口：潜行 + 右键 搬起方块。
	 */
	@SubscribeEvent
	public void onBlockRightClick(PlayerInteractEvent event)
	{

		if (event.action != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK)
			return;
		EntityPlayer player = event.entityPlayer;

		if (!(player instanceof EntityPlayerMP))
			return;

		ItemStack main = Hands.getMainhand(player);
		ItemStack off = Hands.getOffhand(player);
		World world = event.world;
		BlockPos pos = new BlockPos(event.x, event.y, event.z);
		IBlockState state = Worlds.getBlockState(world, pos);

		if (!(main == null && off == null && player.isSneaking()))
			return;

		ItemStack stack = new ItemStack(RegistrationHandler.itemTile);
		TileEntity te = Worlds.getTileEntity(world, pos);

		if (!PickupHandler.canPlayerPickUpBlock(player, te, world, pos))
			return;

		IBlockState actual = state.getActualState(world, pos);

		if (!ItemTile.storeTileData(te, world, pos, actual, stack))
			return;

		NBTTagCompound tag = new NBTTagCompound();
		TileEntity tileNow = Worlds.getTileEntity(world, pos);
		if (tileNow != null)
			tileNow.writeToNBT(tag);

		CarryOnOverride override = ScriptChecker.inspectBlock(state, world, pos, tag);
		int overrideHash = override == null ? 0 : override.hashCode();

		try
		{
			CarryOn.network.sendToAllAround(new CarrySlotPacket(player.inventory.currentItem, player.getEntityId(), overrideHash), new TargetPoint(world.provider.dimensionId, player.posX, player.posY, player.posZ, 256));
			removeBlockForPickup(world, pos, state, te, false);
			Hands.setMainhand(player, stack);
			event.useBlock = Result.DENY;
			event.setCanceled(true);
		}
		catch (Exception e)
		{
			try
			{
				CarryOn.network.sendToAllAround(new CarrySlotPacket(player.inventory.currentItem, player.getEntityId(), overrideHash), new TargetPoint(world.provider.dimensionId, player.posX, player.posY, player.posZ, 256));
				removeBlockForPickup(world, pos, state, te, true);
				Hands.setMainhand(player, stack);
				event.useBlock = Result.DENY;
				event.setCanceled(true);
			}
			catch (Exception ex)
			{
				CarryOn.network.sendToAllAround(new CarrySlotPacket(9, player.getEntityId()), new TargetPoint(world.provider.dimensionId, player.posX, player.posY, player.posZ, 256));
				Worlds.setBlockState(world, pos, state);
				if (tag != null && !tag.hasNoTags())
					TileEntity.createAndLoadEntity(tag);

				Chat.send(player, new TextComponentString(TextFormatting.RED.format("Error detected. Cannot pick up block.")));
			}
		}
	}

	/**
	 * 把"移除方块"这一步统一包进 {@link PickupGuard}。
	 *
	 * <p>1.7.10 移除方块一定会走 {@code Chunk.func_150807_a} 并回调旧方块的
	 * {@code breakBlock}，而有些方块（林业工作台）把"掉落本体"写在那里。
	 * 这里是唯一会移除被拾取方块的地方，包在这一层就不会漏。
	 *
	 * <p>同时把"先删 TE、再删方块"的顺序固定下来：反过来会让容器库存被
	 * {@code breakBlock} 倒出来，而库存又已经存进了携带 NBT，那才是真正的复制。
	 *
	 * @param emptyFirst 异常回退路径沿用旧行为：先把 TE 库存清空再删；正常路径传 false
	 */
	private static void removeBlockForPickup(World world, BlockPos pos, IBlockState state, TileEntity te, boolean emptyFirst)
	{
		PickupGuard guard = PickupGuard.push(world, pos, state.getBlock(), state.getMeta());

		try
		{
			if (te != null && emptyFirst)
				emptyTileEntity(te);

			Worlds.removeTileEntity(world, pos);
			Worlds.setBlockToAir(world, pos);
		}
		finally
		{
			guard.pop();
		}
	}

	/** 清空 TileEntity 的物品（1.7.10 走 IInventory，没有 IItemHandler）。 */
	public static void emptyTileEntity(TileEntity te)
	{
		if (te == null || te.isInvalid())
			return;

		if (te instanceof IInventory)
		{
			IInventory inv = (IInventory) te;
			for (int i = 0; i < inv.getSizeInventory(); i++)
				inv.setInventorySlotContents(i, null);
			inv.markDirty();
		}
	}

	@SubscribeEvent
	public void onRespawn(PlayerEvent.Clone event)
	{
		EntityPlayer original = event.original;
		EntityPlayer player = event.entityPlayer;
		boolean wasDead = event.wasDeath;
		GameRules rules = player.worldObj.getGameRules();
		boolean keepInv = rules.getGameRuleBooleanValue("keepInventory");

		boolean wasCarrying = player.inventory.hasItemStack(new ItemStack(RegistrationHandler.itemTile)) || player.inventory.hasItemStack(new ItemStack(RegistrationHandler.itemEntity));

		if ((wasDead ? keepInv : true) && wasCarrying)
		{
			int carrySlot = original.inventory.currentItem;

			// 1.7.10 没有 removeStackFromSlot
			ItemStack stack = player.inventory.getStackInSlot(carrySlot);
			player.inventory.setInventorySlotContents(carrySlot, null);

			if (stack == null)
				return;

			World world = player.worldObj;
			EntityItem item = new EntityItem(world);
			item.setEntityItemStack(stack);
			item.setPosition(player.posX, player.posY, player.posZ);
			world.spawnEntityInWorld(item);
		}
	}
}
