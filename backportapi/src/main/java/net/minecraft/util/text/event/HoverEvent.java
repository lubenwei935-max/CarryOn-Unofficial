package net.minecraft.util.text.event;

/**
 * 1.8+ HoverEvent 的 1.7.10 回填，映射到 {@code net.minecraft.event.HoverEvent}。
 *
 * 注意：1.7.10 只有 SHOW_TEXT / SHOW_ACHIEVEMENT / SHOW_ITEM，
 * 没有 SHOW_ENTITY；{@link #toVanilla()} 对不支持的 action 返回 null。
 */
public class HoverEvent
{
	public enum Action
	{
		SHOW_TEXT,
		SHOW_ITEM,
		SHOW_ENTITY
	}

	private final Action action;
	private final String value;

	public HoverEvent(Action action, String value)
	{
		this.action = action;
		this.value = value;
	}

	public Action getAction()
	{
		return this.action;
	}

	public String getValue()
	{
		return this.value;
	}

	public net.minecraft.event.HoverEvent toVanilla()
	{
		net.minecraft.event.HoverEvent.Action vanilla = null;
		for (net.minecraft.event.HoverEvent.Action a : net.minecraft.event.HoverEvent.Action.values())
		{
			if (a.name().equals(this.action.name()))
			{
				vanilla = a;
				break;
			}
		}
		if (vanilla == null)
			return null;

		// 1.7.10 的 HoverEvent 需要 IChatComponent 作为值，这里把纯文本包一层
		return new net.minecraft.event.HoverEvent(vanilla, new net.minecraft.util.ChatComponentText(this.value));
	}
}
