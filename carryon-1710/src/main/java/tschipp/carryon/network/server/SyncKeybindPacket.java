package tschipp.carryon.network.server;

import io.netty.buffer.ByteBuf;
import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;

public class SyncKeybindPacket implements IMessage
{
	public boolean pressed;

	public SyncKeybindPacket()
	{
	}

	public SyncKeybindPacket(boolean pressed)
	{
		this.pressed = pressed;
	}

	@Override
	public void fromBytes(ByteBuf buf)
	{
		this.pressed = ByteBufUtils.readVarInt(buf, 4) == 1;
	}

	@Override
	public void toBytes(ByteBuf buf)
	{
		ByteBufUtils.writeVarInt(buf, pressed ? 1 : 0, 4);
	}
}
