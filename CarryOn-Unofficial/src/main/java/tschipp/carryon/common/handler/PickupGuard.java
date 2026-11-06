package tschipp.carryon.common.handler;

import net.minecraft.block.Block;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MathHelper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import tschipp.carryon.CarryOn;

/**
 * "正在抱起某个方块"的作用域上下文。
 *
 * <h3>它解决什么问题</h3>
 * 1.7.10 移除方块一定会走 {@code Chunk.func_150807_a}，它在换掉方块之后会回调
 * 旧方块的 {@code breakBlock}。有些方块（例如林业的工作台）把"掉落本体"写在了
 * {@code breakBlock} 里：{@code tryDropAsWorktable} → {@code spawnEntityInWorld}。
 * 于是"抱起方块"这个动作会额外产出一个同种方块的物品实体——抱起 1 个，净赚 1 个。
 *
 * <p>这个掉落是 {@code breakBlock} 的合法行为，不能整个跳过（很多模组在里面做
 * 多方块/网络/状态清理），所以这里只做一个**时间窗**：只在真正执行移除的那几行
 * 代码里标记"我正在抱起 (world, x, y, z) 这个方块"，让
 * {@link #trySuppress(EntityJoinWorldEvent)} 把这一瞬间生成的、坐标与方块都相同的
 * 掉落物实体拦掉。
 *
 * <h3>为什么是 ThreadLocal + 栈</h3>
 * 服务端主线程单线程执行，用 ThreadLocal 就够；保存 {@code previous} 是为了支持
 * 嵌套（例如某个模组在 {@code breakBlock} 里又移除了另一个方块），pop 时能精确
 * 恢复到上一层，而不是把上下文直接清空。
 *
 * <h3>匹配规则</h3>
 * 必须同时满足：同一个 World 实例、同一个方块坐标、同一个 Block。掉落物坐标是
 * "方块坐标 + [0.1, 0.9]"，取整后仍等于方块坐标，所以坐标取整比较既严格又稳定。
 */
public final class PickupGuard
{
	private static final ThreadLocal<PickupGuard> ACTIVE = new ThreadLocal<PickupGuard>();

	/** 被拾取的方块所在世界（比引用，不比 dimensionId）。 */
	private final World world;
	private final int x;
	private final int y;
	private final int z;
	private final Block block;
	private final int meta;
	/** 上一层上下文，用于嵌套时恢复。 */
	private final PickupGuard previous;

	private PickupGuard(World world, int x, int y, int z, Block block, int meta, PickupGuard previous)
	{
		this.world = world;
		this.x = x;
		this.y = y;
		this.z = z;
		this.block = block;
		this.meta = meta;
		this.previous = previous;
	}

	/** 进入"正在移除这个方块"的作用域。必须在 finally 里 {@link #pop()}。 */
	public static PickupGuard push(World world, BlockPos pos, Block block, int meta)
	{
		PickupGuard guard = new PickupGuard(world, pos.getX(), pos.getY(), pos.getZ(), block, meta, ACTIVE.get());
		ACTIVE.set(guard);
		return guard;
	}

	/** 退出作用域，恢复到上一层（没有上一层就彻底清掉）。 */
	public void pop()
	{
		if (this.previous == null)
			ACTIVE.remove();
		else
			ACTIVE.set(this.previous);
	}

	public static boolean isActive()
	{
		return ACTIVE.get() != null;
	}

	/**
	 * 是否是"同一个世界 + 同一个坐标 + 同一个方块"。
	 *
	 * <p>留给 {@code breakBlock} 层的注入方案复用；当前实现走
	 * {@link #trySuppress(EntityJoinWorldEvent)}，不强制比较 meta。
	 */
	public static boolean matches(World world, int x, int y, int z, Block block)
	{
		PickupGuard ctx = ACTIVE.get();
		if (ctx == null)
			return false;

		return ctx.world == world
				&& ctx.x == x
				&& ctx.y == y
				&& ctx.z == z
				&& ctx.block == block;
	}

	/**
	 * 把"抱起移除期间产生的同源掉落物"拦掉。
	 *
	 * @return true 表示这次实体加入已经被本方法处理（调用方应立即 return）
	 */
	public static boolean trySuppress(EntityJoinWorldEvent event)
	{
		PickupGuard ctx = ACTIVE.get();
		if (ctx == null)
			return false;

		// 只在服务端拦：客户端也会收到这个实体，动画/音效不需要管
		if (event.world == null || event.world.isRemote)
			return false;

		if (!(event.entity instanceof EntityItem))
			return false;

		EntityItem item = (EntityItem) event.entity;

		if (item.worldObj != ctx.world)
			return false;

		if (MathHelper.floor_double(item.posX) != ctx.x
				|| MathHelper.floor_double(item.posY) != ctx.y
				|| MathHelper.floor_double(item.posZ) != ctx.z)
			return false;

		ItemStack stack = item.getEntityItem();
		if (stack == null)
			return false;

		// 掉落实体里的物品必须就是被抱起的那个方块
		if (Block.getBlockFromItem(stack.getItem()) != ctx.block)
			return false;

		event.setCanceled(true);
		// 1.7.10 的 spawnEntityInWorld 在 post 事件之前就把它加进 loadedEntityList 了，
		// 取消事件只是让方法提前 return，不会把它移除；必须自己标记死亡，
		// 下一 tick 的 updateEntities 会把它清掉（不会落地、不会被玩家捡起）。
		item.setDead();

		CarryOn.LOGGER.info("[CarryOn] suppress-drop " + Block.blockRegistry.getNameForObject(ctx.block)
				+ " pos=" + ctx.x + "," + ctx.y + "," + ctx.z + " meta=" + ctx.meta);

		return true;
	}
}
