package net.minecraft.init;

import net.minecraft.potion.Potion;

/**
 * 1.8+ MobEffects 的 1.7.10 回填。
 *
 * <p>1.8 把药水效果从 {@code Potion.xxx} 搬到了 {@code MobEffects.XXX}。
 * 这里直接指向 1.7.10 的 Potion 静态字段，因此
 * {@code MobEffects.SLOWNESS} 与 {@code Potion.moveSlowdown} 是同一个对象。
 *
 * <p>构造 {@code PotionEffect} 时注意：1.7.10 的第一个参数是 <b>int 药水 ID</b>，
 * 所以写 {@code new PotionEffect(MobEffects.SLOWNESS.id, 20, 1)}。
 */
public final class MobEffects
{
	private MobEffects()
	{
	}

	public static final Potion SPEED = Potion.moveSpeed;
	public static final Potion SLOWNESS = Potion.moveSlowdown;
	public static final Potion HASTE = Potion.digSpeed;
	public static final Potion MINING_FATIGUE = Potion.digSlowdown;
	public static final Potion STRENGTH = Potion.damageBoost;
	public static final Potion INSTANT_HEALTH = Potion.heal;
	public static final Potion INSTANT_DAMAGE = Potion.harm;
	public static final Potion JUMP_BOOST = Potion.jump;
	public static final Potion NAUSEA = Potion.confusion;
	public static final Potion REGENERATION = Potion.regeneration;
	public static final Potion RESISTANCE = Potion.resistance;
	public static final Potion FIRE_RESISTANCE = Potion.fireResistance;
	public static final Potion WATER_BREATHING = Potion.waterBreathing;
	public static final Potion INVISIBILITY = Potion.invisibility;
	public static final Potion BLINDNESS = Potion.blindness;
	public static final Potion NIGHT_VISION = Potion.nightVision;
	public static final Potion HUNGER = Potion.hunger;
	public static final Potion WEAKNESS = Potion.weakness;
	public static final Potion POISON = Potion.poison;
	public static final Potion WITHER = Potion.wither;
	public static final Potion HEALTH_BOOST = Potion.field_76434_w;
	public static final Potion ABSORPTION = Potion.field_76444_x;
	public static final Potion SATURATION = Potion.field_76443_y;
}
