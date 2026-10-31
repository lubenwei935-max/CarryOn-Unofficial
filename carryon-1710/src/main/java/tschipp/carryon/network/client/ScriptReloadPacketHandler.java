package tschipp.carryon.network.client;

import net.minecraft.client.Minecraft;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import tschipp.carryon.common.scripting.ScriptReader;

public class ScriptReloadPacketHandler implements IMessageHandler<ScriptReloadPacket, IMessage>
{

	@Override
	public IMessage onMessage(ScriptReloadPacket message, MessageContext ctx)
	{
		net.backportapi.util.Scheduling.addScheduledTask(new Runnable()
		{
			@Override
			public void run()
			{
				if (Minecraft.getMinecraft().thePlayer != null)
					ScriptReader.reloadScripts();
			}
		});

		return null;
	}

}
