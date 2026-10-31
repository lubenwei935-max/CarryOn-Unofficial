package net.minecraft.util.text.event;

/**
 * 1.8+ ClickEvent 的 1.7.10 回填，映射到 {@code net.minecraft.event.ClickEvent}。
 */
public class ClickEvent
{
	public enum Action
	{
		OPEN_URL,
		OPEN_FILE,
		RUN_COMMAND,
		SUGGEST_COMMAND,
		CHANGE_PAGE
	}

	private final Action action;
	private final String value;

	public ClickEvent(Action action, String value)
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

	/**
	 * 映射到 1.7.10 的 ClickEvent。
	 * CHANGE_PAGE 在 1.7.10 不存在，返回 null（调用方需容错）。
	 */
	public net.minecraft.event.ClickEvent toVanilla()
	{
		net.minecraft.event.ClickEvent.Action vanilla = null;
		for (net.minecraft.event.ClickEvent.Action a : net.minecraft.event.ClickEvent.Action.values())
		{
			if (a.name().equals(this.action.name()))
			{
				vanilla = a;
				break;
			}
		}
		return vanilla == null ? null : new net.minecraft.event.ClickEvent(vanilla, this.value);
	}
}
