package net.minecraft.util.text;

import net.minecraft.util.ChatStyle;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.util.text.event.HoverEvent;

/**
 * 1.8+ Style 的 1.7.10 回填，映射到 {@link ChatStyle}。
 */
public class Style
{
	private TextFormatting color;
	private Boolean bold;
	private Boolean italic;
	private Boolean underlined;
	private Boolean strikethrough;
	private Boolean obfuscated;
	private ClickEvent clickEvent;
	private HoverEvent hoverEvent;

	public static Style EMPTY = new Style();

	public TextFormatting getColor()
	{
		return this.color;
	}

	public Style setColor(TextFormatting color)
	{
		this.color = color;
		return this;
	}

	public Boolean getBold()
	{
		return this.bold;
	}

	public Style setBold(Boolean bold)
	{
		this.bold = bold;
		return this;
	}

	public Boolean getItalic()
	{
		return this.italic;
	}

	public Style setItalic(Boolean italic)
	{
		this.italic = italic;
		return this;
	}

	public Boolean getUnderlined()
	{
		return this.underlined;
	}

	public Style setUnderlined(Boolean underlined)
	{
		this.underlined = underlined;
		return this;
	}

	public Boolean getStrikethrough()
	{
		return this.strikethrough;
	}

	public Style setStrikethrough(Boolean strikethrough)
	{
		this.strikethrough = strikethrough;
		return this;
	}

	public Boolean getObfuscated()
	{
		return this.obfuscated;
	}

	public Style setObfuscated(Boolean obfuscated)
	{
		this.obfuscated = obfuscated;
		return this;
	}

	public ClickEvent getClickEvent()
	{
		return this.clickEvent;
	}

	public Style setClickEvent(ClickEvent event)
	{
		this.clickEvent = event;
		return this;
	}

	public HoverEvent getHoverEvent()
	{
		return this.hoverEvent;
	}

	public Style setHoverEvent(HoverEvent event)
	{
		this.hoverEvent = event;
		return this;
	}

	public ChatStyle createChatStyle()
	{
		ChatStyle style = new ChatStyle();
		if (this.color != null && this.color.toVanilla() != null)
			style.setColor(this.color.toVanilla());
		if (this.bold != null)
			style.setBold(this.bold);
		if (this.italic != null)
			style.setItalic(this.italic);
		if (this.underlined != null)
			style.setUnderlined(this.underlined);
		if (this.strikethrough != null)
			style.setStrikethrough(this.strikethrough);
		if (this.obfuscated != null)
			style.setObfuscated(this.obfuscated);
		if (this.clickEvent != null && this.clickEvent.toVanilla() != null)
			style.setChatClickEvent(this.clickEvent.toVanilla());
		if (this.hoverEvent != null && this.hoverEvent.toVanilla() != null)
			style.setChatHoverEvent(this.hoverEvent.toVanilla());
		return style;
	}
}
