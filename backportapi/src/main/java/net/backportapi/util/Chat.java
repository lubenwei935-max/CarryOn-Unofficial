package net.backportapi.util;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.text.ITextComponent;

/**
 * 聊天消息发送桥。
 *
 * 1.7.10 的方法是 {@code EntityPlayer.addChatComponentMessage(IChatComponent)}，
 * 1.8 才改名为 addChatMessage(ITextComponent)。
 */
public final class Chat
{
	private Chat()
	{
	}

	public static void send(EntityPlayer player, ITextComponent message)
	{
		if (player != null && message != null)
			player.addChatComponentMessage(message.toChatComponent());
	}

	public static void sendRaw(EntityPlayer player, String text)
	{
		send(player, new net.minecraft.util.text.TextComponentString(text));
	}

	/** 把 1.8 风格组件转回 vanilla 组件。 */
	public static net.minecraft.util.IChatComponent toVanilla(ITextComponent component)
	{
		return component == null ? null : component.toChatComponent();
	}
}
