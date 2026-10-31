package net.minecraft.util;

/**
 * 1.8+ SoundCategory 的 1.7.10 回填。
 *
 * <p>1.7.10 的声音系统没有分类通道（音量由 GameSettings 的各个滑块直接控制），
 * 保留此枚举只为让 1.8 风格的方法签名成立。
 */
public enum SoundCategory
{
	MASTER,
	MUSIC,
	RECORDS,
	WEATHER,
	BLOCKS,
	HOSTILE,
	NEUTRAL,
	PLAYERS,
	AMBIENT;
}
