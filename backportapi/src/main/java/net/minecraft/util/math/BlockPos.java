package net.minecraft.util.math;

import net.minecraft.util.EnumFacing;

/**
 * 1.8+ BlockPos 的 1.7.10 回填实现。
 *
 * 不可变坐标三元组。1.7.10 原生只有 int x/y/z 三元参数，
 * 这里提供对象化封装，使 1.8+ 风格的代码可以原样编译。
 */
public class BlockPos
{
	public static final BlockPos ORIGIN = new BlockPos(0, 0, 0);

	private final int x;
	private final int y;
	private final int z;

	public BlockPos(int x, int y, int z)
	{
		this.x = x;
		this.y = y;
		this.z = z;
	}

	public BlockPos(double x, double y, double z)
	{
		this(MathHelper.floor(x), MathHelper.floor(y), MathHelper.floor(z));
	}

	public int getX()
	{
		return this.x;
	}

	public int getY()
	{
		return this.y;
	}

	public int getZ()
	{
		return this.z;
	}

	public BlockPos add(int dx, int dy, int dz)
	{
		return dx == 0 && dy == 0 && dz == 0 ? this : new BlockPos(this.x + dx, this.y + dy, this.z + dz);
	}

	public BlockPos add(BlockPos other)
	{
		return add(other.getX(), other.getY(), other.getZ());
	}

	public BlockPos subtract(BlockPos other)
	{
		return add(-other.getX(), -other.getY(), -other.getZ());
	}

	public BlockPos offset(EnumFacing facing)
	{
		return offset(facing, 1);
	}

	public BlockPos offset(EnumFacing facing, int n)
	{
		if (facing == null)
			return this;
		return n == 0 ? this : new BlockPos(this.x + facing.getFrontOffsetX() * n, this.y + facing.getFrontOffsetY() * n, this.z + facing.getFrontOffsetZ() * n);
	}

	public BlockPos up()
	{
		return up(1);
	}

	public BlockPos up(int n)
	{
		return offset(EnumFacing.UP, n);
	}

	public BlockPos down()
	{
		return down(1);
	}

	public BlockPos down(int n)
	{
		return offset(EnumFacing.DOWN, n);
	}

	public BlockPos north()
	{
		return north(1);
	}

	public BlockPos north(int n)
	{
		return offset(EnumFacing.NORTH, n);
	}

	public BlockPos south()
	{
		return south(1);
	}

	public BlockPos south(int n)
	{
		return offset(EnumFacing.SOUTH, n);
	}

	public BlockPos west()
	{
		return west(1);
	}

	public BlockPos west(int n)
	{
		return offset(EnumFacing.WEST, n);
	}

	public BlockPos east()
	{
		return east(1);
	}

	public BlockPos east(int n)
	{
		return offset(EnumFacing.EAST, n);
	}

	public double distanceSq(double px, double py, double pz)
	{
		double dx = this.x + 0.5D - px;
		double dy = this.y + 0.5D - py;
		double dz = this.z + 0.5D - pz;
		return dx * dx + dy * dy + dz * dz;
	}

	/** 1.8 语义：到方块中心（+0.5）的平方距离。 */
	public double distanceSqToCenter(double px, double py, double pz)
	{
		return distanceSq(px, py, pz);
	}

	public double distanceSq(BlockPos other)
	{
		return distanceSq(other.getX(), other.getY(), other.getZ());
	}

	/** 打包成单个 long，用于 Map 键（避免对象分配）。 */
	public long toLong()
	{
		return ((long) this.x & 0x3FFFFFFL) << 38 | ((long) this.z & 0x3FFFFFFL) << 12 | ((long) this.y & 0xFFFL);
	}

	public static BlockPos fromLong(long packed)
	{
		int x = (int) (packed >> 38);
		int y = (int) (packed << 52 >> 52);
		int z = (int) (packed << 26 >> 38);
		return new BlockPos(x, y, z);
	}

	@Override
	public boolean equals(Object o)
	{
		if (this == o)
			return true;
		if (!(o instanceof BlockPos))
			return false;
		BlockPos p = (BlockPos) o;
		return this.x == p.getX() && this.y == p.getY() && this.z == p.getZ();
	}

	@Override
	public int hashCode()
	{
		return (this.y + this.z * 31) * 31 + this.x;
	}

	@Override
	public String toString()
	{
		return "BlockPos{x=" + this.x + ", y=" + this.y + ", z=" + this.z + "}";
	}

	/**
	 * 可变变体：避免在高频路径（光照采样等）反复分配对象。
	 * 注意：作为 Map 键使用时会破坏哈希，勿混用。
	 */
	public static class MutableBlockPos extends BlockPos
	{
		private int mx;
		private int my;
		private int mz;

		public MutableBlockPos()
		{
			this(0, 0, 0);
		}

		public MutableBlockPos(int x, int y, int z)
		{
			super(0, 0, 0);
			this.mx = x;
			this.my = y;
			this.mz = z;
		}

		@Override
		public int getX()
		{
			return this.mx;
		}

		@Override
		public int getY()
		{
			return this.my;
		}

		@Override
		public int getZ()
		{
			return this.mz;
		}

		public MutableBlockPos setPos(int x, int y, int z)
		{
			this.mx = x;
			this.my = y;
			this.mz = z;
			return this;
		}

		public MutableBlockPos setY(int y)
		{
			this.my = y;
			return this;
		}
	}
}
