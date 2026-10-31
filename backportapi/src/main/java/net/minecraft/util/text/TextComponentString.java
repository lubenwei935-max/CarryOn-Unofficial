package net.minecraft.util.text;

import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;

/**
 * 1.8+ TextComponentString 的 1.7.10 回填，内部持有 {@link ChatComponentText}。
 */
public class TextComponentString implements ITextComponent
{
	private final ChatComponentText handle;
	private Style style;

	public TextComponentString(String text)
	{
		this.handle = new ChatComponentText(text == null ? "" : text);
	}

	@Override
	public String getUnformattedText()
	{
		return this.handle.getUnformattedText();
	}

	@Override
	public String getFormattedText()
	{
		return this.handle.getFormattedText();
	}

	@Override
	public ITextComponent appendText(String text)
	{
		this.handle.appendText(text);
		return this;
	}

	@Override
	public ITextComponent appendSibling(ITextComponent sibling)
	{
		if (sibling != null)
			this.handle.appendSibling(sibling.toChatComponent());
		return this;
	}

	@Override
	public Style getStyle()
	{
		return this.style == null ? Style.EMPTY : this.style;
	}

	@Override
	public ITextComponent setStyle(Style style)
	{
		this.style = style;
		if (style != null)
			this.handle.setChatStyle(style.createChatStyle());
		return this;
	}

	@Override
	public IChatComponent toChatComponent()
	{
		return this.handle;
	}

	@Override
	public String toString()
	{
		return this.handle.getUnformattedText();
	}
}
