package tschipp.carryon.common.item;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.backportapi.client.RenderTarget;
import net.backportapi.state.BlockStates;
import net.backportapi.util.Chat;
import net.backportapi.util.Facing;
import net.backportapi.hand.Hands;
import net.backportapi.util.Worlds;
import net.minecraft.block.Block;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.IIcon;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import tschipp.carryon.CarryOn;
import tschipp.carryon.common.config.Configs;
import tschipp.carryon.common.handler.ModelOverridesHandler;
import tschipp.carryon.network.client.CarrySlotPacket;

/**
 * 承载"被搬起的方块"的物品。
 *
 * <p>方块本身、它的 metadata 和 TileEntity 的完整 NBT 都存在这个物品的 NBT 里。
 */
public class ItemTile extends Item
{
	public static final String TILE_DATA_KEY = "tileData";

	/**
	 * 缓存的"重量"（0~4），决定移动减速等级。
	 *
	 * <p>拾取时按 NBT 序列化长度算一次就存下来。
	 * 早先是在 {@code onUpdate} 里每 tick 调 {@code NBTTagCompound.toString()}，
	 * 一个装满东西的箱子每 tick 要序列化上千字符——这是搬运时卡顿的主要来源。
	 */
	private static final String WEIGHT_KEY = "carryWeight";

	public ItemTile()
	{
		this.setUnlocalizedName("tile_item");
		this.setTextureName(CarryOn.MODID + ":tile");
		GameRegistry.registerItem(this, "tile_item");
		this.setMaxStackSize(1);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void registerIcons(IIconRegister register)
	{
		this.itemIcon = register.registerIcon(CarryOn.MODID + ":tile");
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
		if (stack != null && hasTileData(stack))
		{
			// 模型覆盖同时决定显示名（例如点燃的熔炉显示为"熔炉"）
			RenderTarget target = ModelOverridesHandler.getRenderTarget(getBlockState(stack), getTileData(stack));
			if (target != null)
				return target.getDisplayName();

			ItemStack istack = getItemStack(stack);
			if (istack != null)
				return istack.getDisplayName();
		}
		return "";
	}

	/**
	 * 让快捷栏/背包里显示"被搬的那个方块"的图标，而不是统一的箱子贴图。
	 *
	 * <p>1.7.10 没有物品模型系统，图标只能通过 {@code IIcon} 提供；
	 * 这里按当前 ItemStack 的 NBT 动态解析，等价于 1.8 的 ItemMeshDefinition。
	 */
	@Override
	@SideOnly(Side.CLIENT)
	public IIcon getIconIndex(ItemStack stack)
	{
		if (stack != null && hasTileData(stack))
		{
			RenderTarget target = ModelOverridesHandler.getRenderTarget(getBlockState(stack), getTileData(stack));
			if (target != null)
			{
				IIcon icon = target.getIcon();
				if (icon != null)
					return icon;
			}

			ItemStack carried = getItemStack(stack);
			if (carried != null)
			{
				IIcon icon = carried.getIconIndex();
				if (icon != null)
					return icon;
			}
		}

		return super.getIconIndex(stack);
	}

	/**
	 * 1.7.10 的放置入口。
	 *
	 * <p>实际放置逻辑在 {@link #placeFromItem}；这里只在<b>服务端</b>执行，
	 * 避免客户端预测和服务端各放一次。
	 *
	 * <p>注意：正常流程下这个方法不会被调用——{@code ItemEvents.onBlockClick}
	 * 会接管右键并把 {@code useItem} 设为 DENY。原因是原版在玩家<b>未潜行</b>时
	 * 会先调用方块的 {@code onBlockActivated}（例如打开箱子），一旦它返回 true
	 * 就再也不会走到物品的 onItemUse，表现为"只有蹲下才能放下"。
	 */
	@Override
	public boolean onItemUse(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side, float hitX, float hitY, float hitZ)
	{
		if (world.isRemote)
			return false;

		return placeFromItem(stack, player, world, x, y, z, side);
	}

	/** 把物品里的方块放回世界。只在服务端调用。 */
	public boolean placeFromItem(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side)
	{
		if (!hasTileData(stack))
			return false;

		BlockPos pos = new BlockPos(x, y, z);
		EnumFacing facing = EnumFacing.getFront(side);

		try
		{
			Vec3d vec = Vec3d.fromVec3(player.getLookVec());
			EnumFacing facing2 = Facing.getFacingFromVector((float) vec.xCoord, 0f, (float) vec.zCoord);
			BlockPos pos2 = pos;

			Block containedblock = getBlock(stack);
			IBlockState containedstate = getBlockState(stack);

			if (!Worlds.isReplaceable(world, pos2))
				pos2 = pos.offset(facing);

			if (Worlds.isReplaceable(world, pos2) && containedblock != null)
			{
				boolean canPlace = Worlds.canPlaceBlockAt(containedblock, world, pos2);

				if (canPlace && player.canPlayerEdit(pos2.getX(), pos2.getY(), pos2.getZ(), side, stack) && Worlds.canBlockBePlaced(world, containedblock, pos2, false, side, null, stack))
				{
					boolean set = false;
					IBlockState placedState = containedstate;

					Iterator<IProperty<?>> iterator = containedstate.getPropertyNames().iterator();
					while (iterator.hasNext())
					{
						IProperty<?> prop = iterator.next();
						Object[] allowedValues = prop.getAllowedValues().toArray();

						if (prop instanceof PropertyDirection && this.equal(allowedValues, Facing.HORIZONTALS))
						{
							// 楼梯的 meta 记的是"玩家朝向"（台阶朝玩家这一侧抬起），
							// 箱子/熔炉记的是"正面朝向"，两者差 180°，所以楼梯不取反。
							EnumFacing target = containedblock instanceof BlockStairs ? facing2 : Facing.getOpposite(facing2);
							placedState = containedstate.withProperty((IProperty) prop, target);
							set = true;
						}
						else if (prop instanceof PropertyDirection && this.equal(allowedValues, Facing.VALUES))
						{
							facing2 = Facing.getFacingFromVector((float) vec.xCoord, (float) vec.yCoord, (float) vec.zCoord);
							placedState = containedstate.withProperty((IProperty) prop, Facing.getOpposite(facing2));
							set = true;
						}
					}

					// ① 先放进世界。
					//
					// 坑：1.7.10 的 World.setBlock 会经 Chunk.func_150807_a 回调
					// Block.onBlockAdded，而 BlockChest.onBlockAdded 会按"背靠哪个实心块"
					// 重新算一遍朝向（func_149954_e）。空地上四个方向都没有实心邻居，
					// 它就无条件写 meta=3 —— 而 3 就是"正面朝南"。
					// 也就是说：上面算好的朝向在这一步被原版自己冲掉了。
					Worlds.setBlockState(world, pos2, placedState);
					int placedMeta = world.getBlockMetadata(pos2.getX(), pos2.getY(), pos2.getZ());

					// ② 补上原版 ItemBlock.placeBlockAt 的第二步：让方块自己按放置者
					// 朝向决定 meta。箱子/末影箱/铁砧/发射器都在 onBlockPlacedBy 里
					// 把正面转向玩家——少了这一步，箱子就"永远朝南"。
					//
					// 只在两种情况下调用，避免误伤：
					//   a) 属性路径算出的 meta 确实被 onBlockAdded 冲掉了（箱子就是这种）；
					//   b) 方块有 TileEntity（= 容器）且 BlockRotation 不认识它（模组容器）。
					// 不能无条件调用：楼梯的 onBlockPlacedBy 会整段重写 meta，丢掉"倒置"位
					// （bit2）；铁砧更糟，会把"损坏"位一起清掉。
					// 用"放下去有没有 TileEntity"当容器判据，正好把铁砧/门/床这类挡在外面。
					//
					// 注意别用 Block.hasTileEntity(int)：MCP 9.05 的 srg 里只有无参版本
					// （func_149716_u()Z），带 int 的签名在 forgebin 里叫 hasTileEntity(int)，
					// 重混淆时查不到会静默跳过 → 运行时 NoSuchMethodError。
					Item placeItem = Item.getItemFromBlock(containedblock);
					boolean clobbered = set && placedMeta != placedState.getMeta();
					boolean container = Worlds.getTileEntity(world, pos2) != null;

					if (placeItem != null && (clobbered || (!set && container)))
						containedblock.onBlockPlacedBy(world, pos2.getX(), pos2.getY(), pos2.getZ(), player,
								new ItemStack(placeItem, 1, placedState.getMeta()));

					// 方块自己也不管朝向（onBlockPlacedBy 是空实现）→ 直接写 meta。
					// 这里用 setBlockMetadataWithNotify 而不是再 setBlock 一次，
					// 否则会二次触发 onBlockAdded，把 meta 又冲掉。
					if (clobbered && world.getBlockMetadata(pos2.getX(), pos2.getY(), pos2.getZ()) == placedMeta)
						world.setBlockMetadataWithNotify(pos2.getX(), pos2.getY(), pos2.getZ(), placedState.getMeta(), 3);

					TileEntity tile = Worlds.getTileEntity(world, pos2);
					if (tile != null)
					{
						tile.readFromNBT(getTileData(stack));
						// 1.7.10 没有 setPos()，坐标是公开字段
						tile.xCoord = pos2.getX();
						tile.yCoord = pos2.getY();
						tile.zCoord = pos2.getZ();
					}

					clearTileData(stack);
					world.playSoundAtEntity(player, containedblock.stepSound.func_150496_b(), 1.0f, 0.5f);

					// 留一行诊断：放置后的实际 meta 才是"朝向对不对"的唯一事实来源。
					// 1.7.10 的朝向全在 meta 里，光看代码看不出渲染器会怎么转。
					CarryOn.LOGGER.info("[CarryOn] place-block " + Block.blockRegistry.getNameForObject(containedblock)
							+ " pos=" + pos2.getX() + "," + pos2.getY() + "," + pos2.getZ()
							+ " meta=" + world.getBlockMetadata(pos2.getX(), pos2.getY(), pos2.getZ())
							+ " look=" + facing2 + " yaw=" + player.rotationYaw
							+ " prop=" + set + " clobbered=" + clobbered);

					Hands.setMainhand(player, null);
					player.getEntityData().removeTag("overrideKey");
					CarryOn.network.sendToAllAround(new CarrySlotPacket(9, player.getEntityId()), new cpw.mods.fml.common.network.NetworkRegistry.TargetPoint(world.provider.dimensionId, player.posX, player.posY, player.posZ, 256));
					return true;
				}
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();

			if (world != null && world.isRemote)
			{
				CarryOn.LOGGER.info("Block: " + getBlock(stack));
				CarryOn.LOGGER.info("BlockState: " + getBlockState(stack));
				CarryOn.LOGGER.info("Meta: " + getMeta(stack));
				CarryOn.LOGGER.info("ItemStack: " + getItemStack(stack));

				Chat.sendRaw(player, TextFormatting.RED.format("Error detected. Cannot place block. Execute \"/carryon clear\" to remove the item"));
			}
		}

		return false;
	}

	@Override
	public void onUpdate(ItemStack stack, World world, Entity entity, int itemSlot, boolean isSelected)
	{
		if (!hasTileData(stack))
			return;

		if (!(entity instanceof EntityLivingBase))
			return;

		if (entity instanceof EntityPlayer && !Configs.Settings.slownessInCreative && ((EntityPlayer) entity).capabilities.isCreativeMode)
			return;

		// 1.7.10 的 PotionEffect 第一个参数是 int 药水 ID
		((EntityLivingBase) entity).addPotionEffect(new PotionEffect(Potion.moveSlowdown.id, 1, potionLevel(stack)));
	}

	public static boolean hasTileData(ItemStack stack)
	{
		if (stack != null && stack.hasTagCompound())
		{
			NBTTagCompound tag = stack.getTagCompound();
			return tag.hasKey(TILE_DATA_KEY) && tag.hasKey("block") && tag.hasKey("meta") && tag.hasKey("stateid");
		}
		return false;
	}

	public static boolean storeTileData(TileEntity tile, World world, BlockPos pos, IBlockState state, ItemStack stack)
	{
		if (stack == null)
			return false;

		NBTTagCompound chest = new NBTTagCompound();
		if (tile != null)
			tile.writeToNBT(chest);

		NBTTagCompound tag = stack.hasTagCompound() ? stack.getTagCompound() : new NBTTagCompound();
		if (tag.hasKey(TILE_DATA_KEY))
			return false;

		tag.setTag(TILE_DATA_KEY, chest);

		Block block = state.getBlock();
		int meta = state.getMeta();

		// 不再构造临时 ItemStack 去读它的 damage：那个 getter 在 MCP 9.05 与
		// 编译用 jar 之间命名不一致（getMetadata / getItemDamage），
		// 重混淆会漏改。这里直接用同一个值，少一次分配也更稳。
		int dropMeta = block.damageDropped(meta);

		tag.setString("block", Block.blockRegistry.getNameForObject(block));
		tag.setInteger("meta", dropMeta);
		tag.setInteger("stateid", BlockStates.getStateId(state));
		tag.setInteger(WEIGHT_KEY, computeWeight(chest));
		stack.setTagCompound(tag);
		return true;
	}

	/**
	 * 按 NBT 序列化长度估算重量（0~4）。<b>只在拾取时调用一次</b>。
	 *
	 * <p>这是上游"方块越复杂走得越慢"的原始算法，语义保持原样；
	 * 区别只在于把结果缓存进物品 NBT，而不是每 tick 重算。
	 */
	private static int computeWeight(NBTTagCompound data)
	{
		int i = data == null ? 0 : data.toString().length() / 500;
		return i > 4 ? 4 : i;
	}

	public static void clearTileData(ItemStack stack)
	{
		if (stack != null && stack.hasTagCompound())
		{
			NBTTagCompound tag = stack.getTagCompound();
			tag.removeTag(TILE_DATA_KEY);
			tag.removeTag("block");
			tag.removeTag("meta");
			tag.removeTag("stateid");
			tag.removeTag(WEIGHT_KEY);
		}
	}

	public static NBTTagCompound getTileData(ItemStack stack)
	{
		if (stack != null && stack.hasTagCompound())
			return stack.getTagCompound().getCompoundTag(TILE_DATA_KEY);
		return null;
	}

	public static Block getBlock(ItemStack stack)
	{
		if (stack != null && stack.hasTagCompound())
		{
			int id = stack.getTagCompound().getInteger("stateid");
			return BlockStates.getStateById(id).getBlock();
		}
		return null;
	}

	public static int getMeta(ItemStack stack)
	{
		if (stack != null && stack.hasTagCompound())
			return stack.getTagCompound().getInteger("meta");
		return 0;
	}

	public static ItemStack getItemStack(ItemStack stack)
	{
		Block block = getBlock(stack);
		if (block == null)
			return null;

		Item item = Item.getItemFromBlock(block);
		if (item == null)
			return null;

		return new ItemStack(item, 1, getMeta(stack));
	}

	public static IBlockState getBlockState(ItemStack stack)
	{
		if (stack != null && stack.hasTagCompound())
		{
			int id = stack.getTagCompound().getInteger("stateid");
			return BlockStates.getStateById(id);
		}
		return BlockStates.getDefaultState(Blocks.air);
	}

	public static boolean isLocked(BlockPos pos, World world)
	{
		TileEntity te = Worlds.getTileEntity(world, pos);
		if (te != null)
		{
			NBTTagCompound tag = new NBTTagCompound();
			te.writeToNBT(tag);
			return tag.hasKey("Lock") ? !tag.getString("Lock").equals("") : false;
		}
		return false;
	}

	private boolean equal(Object[] a, Object[] b)
	{
		if (a.length != b.length)
			return false;

		List<Object> lA = Arrays.asList(a);
		List<Object> lB = Arrays.asList(b);

		return lA.containsAll(lB);
	}

	/**
	 * 减速等级。重量在拾取时已经算好，这里只读一个 int。
	 *
	 * <p>旧存档里搬着的物品没有 {@link #WEIGHT_KEY}（更新前拾取的），
	 * 退回 0 即可——那正好是"小容器"原本的等级。
	 */
	private int potionLevel(ItemStack stack)
	{
		int i;

		if (!Configs.Settings.heavyTiles)
		{
			i = 1;
		}
		else
		{
			NBTTagCompound tag = stack.getTagCompound();
			i = tag != null && tag.hasKey(WEIGHT_KEY) ? tag.getInteger(WEIGHT_KEY) : 0;
		}

		return (int) (i * Configs.Settings.blockSlownessMultiplier);
	}
}
