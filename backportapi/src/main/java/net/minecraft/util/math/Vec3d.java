package net.minecraft.util.math;

import net.minecraft.util.Vec3;

/**
 * 1.8+ Vec3d 的 1.7.10 回填实现。
 * 与 1.7.10 的 {@link Vec3} 互转。
 */
public class Vec3d
{
	public static final Vec3d ZERO = new Vec3d(0, 0, 0);

	public final double xCoord;
	public final double yCoord;
	public final double zCoord;

	public Vec3d(double x, double y, double z)
	{
		if (x == -0.0D)
			x = 0.0D;
		if (y == -0.0D)
			y = 0.0D;
		if (z == -0.0D)
			z = 0.0D;
		this.xCoord = x;
		this.yCoord = y;
		this.zCoord = z;
	}

	public static Vec3d fromVec3(Vec3 vec)
	{
		return vec == null ? ZERO : new Vec3d(vec.xCoord, vec.yCoord, vec.zCoord);
	}

	public Vec3 toVec3()
	{
		return Vec3.createVectorHelper(this.xCoord, this.yCoord, this.zCoord);
	}

	public Vec3d add(Vec3d other)
	{
		return new Vec3d(this.xCoord + other.xCoord, this.yCoord + other.yCoord, this.zCoord + other.zCoord);
	}

	public Vec3d addVector(double x, double y, double z)
	{
		return new Vec3d(this.xCoord + x, this.yCoord + y, this.zCoord + z);
	}

	public Vec3d subtract(Vec3d other)
	{
		return new Vec3d(this.xCoord - other.xCoord, this.yCoord - other.yCoord, this.zCoord - other.zCoord);
	}

	public Vec3d scale(double factor)
	{
		return new Vec3d(this.xCoord * factor, this.yCoord * factor, this.zCoord * factor);
	}

	public double dotProduct(Vec3d other)
	{
		return this.xCoord * other.xCoord + this.yCoord * other.yCoord + this.zCoord * other.zCoord;
	}

	public Vec3d crossProduct(Vec3d other)
	{
		return new Vec3d(this.yCoord * other.zCoord - this.zCoord * other.yCoord, this.zCoord * other.xCoord - this.xCoord * other.zCoord, this.xCoord * other.yCoord - this.yCoord * other.xCoord);
	}

	public Vec3d normalize()
	{
		double d = Math.sqrt(this.xCoord * this.xCoord + this.yCoord * this.yCoord + this.zCoord * this.zCoord);
		return d < 1.0E-4D ? ZERO : new Vec3d(this.xCoord / d, this.yCoord / d, this.zCoord / d);
	}

	public double distanceTo(Vec3d other)
	{
		double dx = this.xCoord - other.xCoord;
		double dy = this.yCoord - other.yCoord;
		double dz = this.zCoord - other.zCoord;
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	public double squareDistanceTo(Vec3d other)
	{
		double dx = this.xCoord - other.xCoord;
		double dy = this.yCoord - other.yCoord;
		double dz = this.zCoord - other.zCoord;
		return dx * dx + dy * dy + dz * dz;
	}

	@Override
	public String toString()
	{
		return "(" + this.xCoord + ", " + this.yCoord + ", " + this.zCoord + ")";
	}
}
