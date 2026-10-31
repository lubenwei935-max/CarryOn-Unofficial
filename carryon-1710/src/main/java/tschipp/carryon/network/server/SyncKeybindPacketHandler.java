package tschipp.carryon.network.server;

import net.minecraft.entity.player.EntityPlayerMP;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import tschipp.carryon.client.keybinds.CarryOnKeybinds;

public class SyncKeybindPacketHandler implements IMessageHandler<SyncKeybindPacket, IMessage>
{

	@Override
	public IMessage onMessage(final SyncKeybindPacket message, final MessageContext ctx)
	{
		net.backportapi.util.Scheduling.addScheduledTask(new Runnable()
		{
			@Override
			public void run()
			{
				EntityPlayerMP player = ctx.getServerHandler().playerEntity;
				if (player != null)
					CarryOnKeybinds.setKeyPressed(player, message.pressed);
			}
		});

		return null;
	}

}
