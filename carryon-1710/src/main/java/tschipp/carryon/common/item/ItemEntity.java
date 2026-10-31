package tschipp.carryon.common.item;

import cpw.mods.fml.common.network.NetworkRegistry.TargetPoint;
import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.backportapi.hand.Hands;
import net.backportapi.util.Worlds;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.IIcon;
import net.minecraft.util.StatCollector;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import tschipp.carryon.CarryOn;
import tschipp.carryon.common.config.Configs;
import tschipp.carryon.network.client.CarrySlotPacket;

/**
 * 承载"被搬起的生物"的物品。
 *
 * <p>实体类型名与其完整 NBT 存在物品的 NBT 里，
 * 放下时用 {@code EntityList.createEntityByName} 重建。
 */
public class ItemEntity extends Item
{
	public static final String ENTITY_DATA_KEY = "entityData";

	/**
	 * 缓存的"体积等级"（0~4），决定移动减速等级。
	 *
	 * <p>拾取时算一次存下来。早先是在 {@code onUpdate} 里每 tick 调
	 * {@link #getEntity}，那会 {@code EntityList.createEntityByName + readFromNBT}
	 * 重建一只完整实体——每 tick 一次，搬运任何生物都在白白分配对象。
	 */
	private static final String SIZE_KEY = "carrySize";

	public ItemEntity()
	{
		this.setUnlocalizedName("entity_item");
		this.setTextureName("carryon:tile");
		GameRegistry.registerItem(this, "entity_item");
		this.setMaxStackSize(1);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void registerIcons(IIconRegister register)
	{
		this.itemIcon = register.registerIcon("carryon:tile");
	}

	@Override
	@SideOnly(Side.CLIENT)
	public IIcon getIconFromDamage(int meta)
	{
		return this.itemIcon;
	}

	@Override
	public String getItemStackDisplayName(ItemStack stack)
	{
		if (hasEntityData(stack))
			return StatCollector.translateToLocal(getEntityName(stack));

		return "";
	}

	public static boolean hasEntityData(ItemStack stack)
	{
		if (stack != null && stack.hasTagCompound())
		{
			NBTTagCompound tag = stack.getTagCompound();
			return tag.hasKey(ENTITY_DATA_KEY) && tag.hasKey("entity");
		}
		return false;
	}

	public static boolean storeEntityData(Entity entity, World world, ItemStack stack)
	{
		if (entity == null || stack == null)
			return false;

		NBTTagCompound entityData = new NBTTagCompound();
		entity.writeToNBT(entityData);

		String name = EntityList.getEntityString(entity);

		NBTTagCompound tag = stack.hasTagCompound() ? stack.getTagCompound() : new NBTTagCompound();
		if (tag.hasKey(ENTITY_DATA_KEY))
			return false;

		tag.setTag(ENTITY_DATA_KEY, entityData);
		tag.setString("entity", name);
		tag.setInteger(SIZE_KEY, computeSize(entity));
		stack.setTagCompound(tag);
		return true;
	}

	/** 体积等级 = 高×宽，上限 4。<b>只在拾取时调用一次</b>。 */
	private static int computeSize(Entity entity)
	{
		int i = (int) (entity.height * entity.width);
		return i > 4 ? 4 : i;
	}

	/**
	 * 1.7.10 的放置入口。只在服务端执行，避免双份生成实体。
	 * 正常流程由 {@code ItemEntityEvents.onBlockClick} 接管，原因见
	 * {@link ItemTile#onItemUse} 的注释。
	 */
	@Override
	public boolean onItemUse(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side, float hitX, float hitY, float hitZ)
	{
		if (world.isRemote)
			return false;

		return placeFromItem(stack, player, world, x, y, z, side);
	}

	/**
	 * 把物品里的生物放回世界。只在服务端调用。
	 *
	 * <h3>为什么先清数据、后生成实体</h3>
	 * 这个方法有两个入口：{@code ItemEntityEvents.onBlockClick}（主路径）和
	 * {@link #onItemUse}（原版兜底）。正常情况下后者被 {@code useItem = DENY} 挡住，
	 * 但只要有一次两条路都走到（某些模组/客户端预测会绕过 DENY），
	 * 就会<b>放出两只一模一样的生物</b>。
	 * 把"清空 NBT + 清空手上物品"提到生成之前，这个方法就变成<b>幂等</b>的：
	 * 第二次进来时 {@code hasEntityData} 已经是 false，直接返回。
	 */
	public boolean placeFromItem(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side)
	{
		if (world == null || world.isRemote || !hasEntityData(stack))
			return false;

		String name = getEntityName(stack);
		NBTTagCompound data = getEntityData(stack);
		if (name == null)
			return false;

		// 先把数据取走并清空，之后无论被调用多少次都只可能生成一只
		clearEntityData(stack);
		Hands.setMainhand(player, null);

		BlockPos pos = new BlockPos(x, y, z);
		BlockPos finalPos = Worlds.isReplaceable(world, pos) ? pos : pos.offset(net.minecraft.util.EnumFacing.getFront(side));

		Entity entity = createEntity(name, data, world);
		if (entity == null)
			return false;

		// 保险：NBT 里带着原实体的 UUID，如果那个"原体"其实没被真正移除
		// （拾取时 setDead 没生效、或它被别的模组复活），世界里就会同时存在两只一样的生物。
		// 这里把同 UUID 的旧实体清掉，保证"放下"之后只有一只。
		removeStaleCopy(world, entity);

		// 1.7.10 用 setLocationAndAngles 代替 setPositionAndRotation
		entity.setLocationAndAngles(finalPos.getX() + 0.5D, finalPos.getY(), finalPos.getZ() + 0.5D, 180.0F + player.rotationYawHead, 0.0F);
		world.spawnEntityInWorld(entity);

		if (entity instanceof EntityLiving)
			((EntityLiving) entity).playLivingSound();

		player.getEntityData().removeTag("overrideKey");
		CarryOn.network.sendToAllAround(new CarrySlotPacket(9, player.getEntityId()), new TargetPoint(world.provider.dimensionId, player.posX, player.posY, player.posZ, 256));

		return true;
	}

	/** 用物品里存的"类型名 + NBT"重建实体（不依赖 ItemStack，便于先清空再生成）。 */
	private static Entity createEntity(String name, NBTTagCompound data, World world)
	{
		Entity entity = EntityList.createEntityByName(name, world);
		if (entity != null && data != null)
			entity.readFromNBT(data);
		return entity;
	}

	/** 把世界里和待生成实体同 UUID 的旧实体清掉（正常情况下不会有）。 */
	@SuppressWarnings("unchecked")
	private static void removeStaleCopy(World world, Entity entity)
	{
		try
		{
			java.util.UUID id = entity.getUniqueID();
			if (id == null)
				return;

			for (Object o : new java.util.ArrayList<Object>(world.loadedEntityList))
			{
				Entity other = (Entity) o;
				if (other == entity || other.isDead)
					continue;

				if (id.equals(other.getUniqueID()))
				{
					other.setDead();
				}
			}
		}
		catch (Throwable t)
		{
			t.printStackTrace();
		}
	}

	@Override
	public void onUpdate(ItemStack stack, World world, Entity entity, int itemSlot, boolean isSelected)
	{
		if (!hasEntityData(stack))
			return;

		if (!(entity instanceof EntityLivingBase))
			return;

		if (entity instanceof EntityPlayer && !Configs.Settings.slownessInCreative && ((EntityPlayer) entity).capabilities.isCreativeMode)
			return;

		((EntityLivingBase) entity).addPotionEffect(new PotionEffect(Potion.moveSlowdown.id, 1, potionLevel(stack)));
	}

	public static void clearEntityData(ItemStack stack)
	{
		if (stack != null && stack.hasTagCompound())
		{
			NBTTagCompound tag = stack.getTagCompound();
			tag.removeTag(ENTITY_DATA_KEY);
			tag.removeTag("entity");
			tag.removeTag(SIZE_KEY);
		}
	}

	public static NBTTagCompound getEntityData(ItemStack stack)
	{
		if (stack != null && stack.hasTagCompound())
			return stack.getTagCompound().getCompoundTag(ENTITY_DATA_KEY);
		return null;
	}

	public static Entity getEntity(ItemStack stack, World world)
	{
		if (world == null)
			return null;

		String name = getEntityName(stack);
		if (name == null)
			return null;

		NBTTagCompound data = getEntityData(stack);

		// 1.7.10 叫 createEntityByName
		Entity entity = EntityList.createEntityByName(name, world);
		if (entity != null && data != null)
			entity.readFromNBT(data);

		return entity;
	}

	public static String getEntityName(ItemStack stack)
	{
		if (stack != null && stack.hasTagCompound())
			return stack.getTagCompound().getString("entity");
		return null;
	}

	private int potionLevel(ItemStack stack)
	{
		int i;

		if (!Configs.Settings.heavyEntities)
		{
			i = 1;
		}
		else
		{
			NBTTagCompound tag = stack.getTagCompound();
			i = tag != null && tag.hasKey(SIZE_KEY) ? tag.getInteger(SIZE_KEY) : 1;
		}

		return (int) (i * Configs.Settings.entitySlownessMultiplier);
	}
}
