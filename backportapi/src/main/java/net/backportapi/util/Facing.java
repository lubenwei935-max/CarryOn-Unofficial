package net.backportapi.util;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

/**
 * 1.8 的 EnumFacing 工具方法回填。
 *
 * 1.7.10 的 EnumFacing 缺少 VALUES / HORIZONTALS / getOpposite /
 * getFacingFromVector / getDirectionVec，这里集中补齐。
 */
public final class Facing
{
	/** 1.8 的 EnumFacing.VALUES。1.7.10 只有私有 faceList。 */
	public static final EnumFacing[] VALUES = new EnumFacing[] { EnumFacing.DOWN, EnumFacing.UP, EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.WEST, EnumFacing.EAST };

	/** 1.8 的 EnumFacing.HORIZONTALS。 */
	public static final EnumFacing[] HORIZONTALS = new EnumFacing[] { EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.WEST, EnumFacing.EAST };

	private Facing()
	{
	}

	/** 1.8 的 getOpposite()。 */
	public static EnumFacing getOpposite(EnumFacing facing)
	{
		switch (facing)
		{
		case DOWN:
			return EnumFacing.UP;
		case UP:
			return EnumFacing.DOWN;
		case NORTH:
			return EnumFacing.SOUTH;
		case SOUTH:
			return EnumFacing.NORTH;
		case WEST:
			return EnumFacing.EAST;
		case EAST:
			return EnumFacing.WEST;
		default:
			return facing;
		}
	}

	/**
	 * 1.8 的 getFacingFromVector：取分量绝对值最大的轴。
	 * 与原版一致，平局时按 x > z > y 的优先序。
	 */
	public static EnumFacing getFacingFromVector(float x, float y, float z)
	{
		EnumFacing facing = EnumFacing.NORTH;
		float best = Float.MIN_VALUE;

		if (x != 0.0F && Math.abs(x) > best)
		{
			best = Math.abs(x);
			facing = x > 0.0F ? EnumFacing.EAST : EnumFacing.WEST;
		}
		if (y != 0.0F && Math.abs(y) > best)
		{
			best = Math.abs(y);
			facing = y > 0.0F ? EnumFacing.UP : EnumFacing.DOWN;
		}
		if (z != 0.0F && Math.abs(z) > best)
		{
			facing = z > 0.0F ? EnumFacing.SOUTH : EnumFacing.NORTH;
		}
		return facing;
	}

	/** 由 yaw 求水平朝向，等价于 1.8 的 getHorizontalFacing。 */
	public static EnumFacing getHorizontalFacing(double yaw)
	{
		int i = (int) Math.floor((yaw * 4.0D / 360.0D) + 0.5D) & 3;
		switch (i)
		{
		case 0:
			return EnumFacing.SOUTH;
		case 1:
			return EnumFacing.WEST;
		case 2:
			return EnumFacing.NORTH;
		default:
			return EnumFacing.EAST;
		}
	}

	public static BlockPos offset(BlockPos pos, EnumFacing facing)
	{
		return pos.offset(facing);
	}

	public static boolean isHorizontal(EnumFacing facing)
	{
		return facing != null && facing.getFrontOffsetY() == 0;
	}
}
