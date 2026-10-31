package net.minecraft.init;

import net.minecraft.util.SoundEvent;

/**
 * 1.8+ SoundEvents 的 1.7.10 回填。
 *
 * <p>只收录常用的一批；值就是 1.7.10 直接可播的字符串资源名。
 * 需要别的音效时用 {@code SoundEvent.of("...")} 自建即可。
 */
public final class SoundEvents
{
	private SoundEvents()
	{
	}

	// 方块
	public static final SoundEvent BLOCK_CHEST_OPEN = SoundEvent.of("random.chestopen");
	public static final SoundEvent BLOCK_CHEST_CLOSE = SoundEvent.of("random.chestclosed");
	public static final SoundEvent BLOCK_CHEST_LOCKED = SoundEvent.of("random.door_close");
	public static final SoundEvent BLOCK_WOOD_BREAK = SoundEvent.of("dig.wood");
	public static final SoundEvent BLOCK_WOOD_PLACE = SoundEvent.of("dig.wood");
	public static final SoundEvent BLOCK_STONE_BREAK = SoundEvent.of("dig.stone");
	public static final SoundEvent BLOCK_STONE_PLACE = SoundEvent.of("dig.stone");
	public static final SoundEvent BLOCK_METAL_BREAK = SoundEvent.of("dig.metal");
	public static final SoundEvent BLOCK_METAL_PLACE = SoundEvent.of("dig.metal");
	public static final SoundEvent BLOCK_GLASS_BREAK = SoundEvent.of("random.glass");
	public static final SoundEvent BLOCK_GLASS_PLACE = SoundEvent.of("random.glass");

	// 实体
	public static final SoundEvent ENTITY_ITEM_PICKUP = SoundEvent.of("random.pop");
	public static final SoundEvent ENTITY_ITEM_BREAK = SoundEvent.of("random.break");
	public static final SoundEvent ENTITY_PLAYER_HURT = SoundEvent.of("game.player.hurt");
	public static final SoundEvent ENTITY_PLAYER_DEATH = SoundEvent.of("game.player.die");
	public static final SoundEvent ENTITY_GENERIC_EXPLODE = SoundEvent.of("random.explode");

	// 界面/交互
	public static final SoundEvent UI_BUTTON_CLICK = SoundEvent.of("gui.button.press");
	public static final SoundEvent ENTITY_EXPERIENCE_ORB_PICKUP = SoundEvent.of("random.orb");
}
