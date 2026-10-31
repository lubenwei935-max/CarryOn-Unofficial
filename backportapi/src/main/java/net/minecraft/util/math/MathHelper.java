package net.minecraft.util.math;

import java.util.Random;

/**
 * 1.8+ MathHelper 的 1.7.10 回填。
 *
 * <p>不能继承 {@code net.minecraft.util.MathHelper}（其构造函数不可访问），
 * 因此改为静态委托：1.7.10 已有的方法原样转发，1.8 新增的方法在此实现。
 * 这样 1.8 风格代码（{@code MathHelper.floor(...)}）与 1.7.10 风格代码
 * （{@code MathHelper.floor_double(...)}）可以在同一份源码里共存。
 */
public final class MathHelper
{
	private MathHelper()
	{
	}

	// ---------- 1.7.10 原有方法（转发） ----------

	public static float sin(float value)
	{
		return net.minecraft.util.MathHelper.sin(value);
	}

	public static float cos(float value)
	{
		return net.minecraft.util.MathHelper.cos(value);
	}

	public static float sqrt_float(float value)
	{
		return net.minecraft.util.MathHelper.sqrt_float(value);
	}

	public static float sqrt_double(double value)
	{
		return net.minecraft.util.MathHelper.sqrt_double(value);
	}

	public static int floor_float(float value)
	{
		return net.minecraft.util.MathHelper.floor_float(value);
	}

	public static int floor_double(double value)
	{
		return net.minecraft.util.MathHelper.floor_double(value);
	}

	public static long floor_double_long(double value)
	{
		return net.minecraft.util.MathHelper.floor_double_long(value);
	}

	public static float abs(float value)
	{
		return net.minecraft.util.MathHelper.abs(value);
	}

	public static int abs_int(int value)
	{
		return net.minecraft.util.MathHelper.abs_int(value);
	}

	public static int ceiling_float_int(float value)
	{
		return net.minecraft.util.MathHelper.ceiling_float_int(value);
	}

	public static int ceiling_double_int(double value)
	{
		return net.minecraft.util.MathHelper.ceiling_double_int(value);
	}

	public static int clamp_int(int value, int min, int max)
	{
		return net.minecraft.util.MathHelper.clamp_int(value, min, max);
	}

	public static float clamp_float(float value, float min, float max)
	{
		return net.minecraft.util.MathHelper.clamp_float(value, min, max);
	}

	public static double clamp_double(double value, double min, double max)
	{
		return net.minecraft.util.MathHelper.clamp_double(value, min, max);
	}

	public static double denormalizeClamp(double min, double max, double t)
	{
		return net.minecraft.util.MathHelper.denormalizeClamp(min, max, t);
	}

	public static double abs_max(double a, double b)
	{
		return net.minecraft.util.MathHelper.abs_max(a, b);
	}

	public static int bucketInt(int value, int bucketSize)
	{
		return net.minecraft.util.MathHelper.bucketInt(value, bucketSize);
	}

	public static int getRandomIntegerInRange(Random rand, int min, int max)
	{
		return net.minecraft.util.MathHelper.getRandomIntegerInRange(rand, min, max);
	}

	public static float randomFloatClamp(Random rand, float min, float max)
	{
		return net.minecraft.util.MathHelper.randomFloatClamp(rand, min, max);
	}

	public static double getRandomDoubleInRange(Random rand, double min, double max)
	{
		return net.minecraft.util.MathHelper.getRandomDoubleInRange(rand, min, max);
	}

	public static float wrapAngleTo180_float(float angle)
	{
		return net.minecraft.util.MathHelper.wrapAngleTo180_float(angle);
	}

	public static double wrapAngleTo180_double(double angle)
	{
		return net.minecraft.util.MathHelper.wrapAngleTo180_double(angle);
	}

	public static int roundUpToPowerOfTwo(int value)
	{
		return net.minecraft.util.MathHelper.roundUpToPowerOfTwo(value);
	}

	public static int calculateLogBaseTwo(int value)
	{
		return net.minecraft.util.MathHelper.calculateLogBaseTwo(value);
	}

	// ---------- 1.8 新增 / 改名 ----------

	/** 1.8 起 floor_double 改名为 floor。 */
	public static int floor(double value)
	{
		return net.minecraft.util.MathHelper.floor_double(value);
	}

	/** 1.8 新增。 */
	public static int ceil(double value)
	{
		int i = (int) value;
		return value > i ? i + 1 : i;
	}

	public static int clamp(int value, int min, int max)
	{
		return net.minecraft.util.MathHelper.clamp_int(value, min, max);
	}

	public static double clampedLerp(double min, double max, double t)
	{
		return t < 0.0D ? min : (t > 1.0D ? max : min + (max - min) * t);
	}
}
