package net.minecraft.util.text;

import net.minecraft.util.IChatComponent;

/**
 * 1.8+ ITextComponent 的 1.7.10 回填。
 *
 * <p>1.7.10 的对应物是 {@link IChatComponent}。本接口不另建一套渲染管线，
 * 而是<b>适配</b>到底层 IChatComponent：每个实现都持有一个 vanilla 组件，
 * 通过 {@link #toChatComponent()} 交回原版 API（addChatComponentMessage、
 * ItemStack.setStackDisplayName 等）。
 */
public interface ITextComponent
{
	String getUnformattedText();

	String getFormattedText();

	ITextComponent appendText(String text);

	ITextComponent appendSibling(ITextComponent sibling);

	Style getStyle();

	ITextComponent setStyle(Style style);

	/** 交回 1.7.10 原生组件，用于调用原版 API。 */
	IChatComponent toChatComponent();
}
