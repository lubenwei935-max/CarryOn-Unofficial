package net.minecraft.util.text;

/**
 * 1.8+ TextFormatting 的 1.7.10 回填。
 * 名称与 1.8 一致，{@link #toVanilla()} 映射到 {@code EnumChatFormatting}。
 */
public enum TextFormatting
{
	BLACK('0'),
	DARK_BLUE('1'),
	DARK_GREEN('2'),
	DARK_AQUA('3'),
	DARK_RED('4'),
	DARK_PURPLE('5'),
	GOLD('6'),
	GRAY('7'),
	DARK_GRAY('8'),
	BLUE('9'),
	GREEN('a'),
	AQUA('b'),
	RED('c'),
	LIGHT_PURPLE('d'),
	YELLOW('e'),
	WHITE('f'),
	OBFUSCATED('k'),
	BOLD('l'),
	STRIKETHROUGH('m'),
	UNDERLINE('n'),
	ITALIC('o'),
	RESET('r');

	private final char code;

	private TextFormatting(char code)
	{
		this.code = code;
	}

	public char getCode()
	{
		return this.code;
	}

	/** 映射到 1.7.10 的 EnumChatFormatting；找不到时返回 null。 */
	public net.minecraft.util.EnumChatFormatting toVanilla()
	{
		for (net.minecraft.util.EnumChatFormatting f : net.minecraft.util.EnumChatFormatting.values())
		{
			if (f.name().equals(this.name()))
				return f;
		}
		return null;
	}

	/** 把 "§c" 这样的格式化前缀拼到文本前。 */
	public String format(String text)
	{
		return "\u00a7" + this.code + text;
	}

	@Override
	public String toString()
	{
		return "\u00a7" + this.code;
	}
}
