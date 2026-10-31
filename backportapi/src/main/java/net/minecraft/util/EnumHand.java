package net.minecraft.util;

/**
 * 1.9+ EnumHand 的 1.7.10 回填。
 *
 * 1.7.10 本身没有副手。副手能力由 Backhand 模组提供，
 * 通过 {@link net.backportapi.hand.Hands} 访问；Backhand 缺席时
 * OFF_HAND 恒为空，代码可以无分支地运行。
 */
public enum EnumHand
{
	MAIN_HAND,
	OFF_HAND;
}
