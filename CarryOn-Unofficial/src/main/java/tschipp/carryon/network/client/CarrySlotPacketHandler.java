package tschipp.carryon.network.client;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import tschipp.carryon.common.scripting.ScriptChecker;

public class CarrySlotPacketHandler implements IMessageHandler<CarrySlotPacket, IMessage>
{

	@Override
	public IMessage onMessage(final CarrySlotPacket message, final MessageContext ctx)
	{
		// 这个 Runnable 是在网络线程构造的，字段初始化也在网络线程执行。
		// Minecraft.theWorld 只能在客户端主线程读，所以全部挪进 run()。
		net.backportapi.util.Scheduling.addScheduledTask(new Runnable()
		{
			@Override
			public void run()
			{
				World world = Minecraft.getMinecraft().theWorld;
				if (world == null)
					return;

				Entity e = world.getEntityByID(message.entityid);
				if (!(e instanceof EntityPlayer))
					return;

				EntityPlayer player = (EntityPlayer) e;

				if (message.slot >= 9)
				{
					player.getEntityData().removeTag("carrySlot");
					player.getEntityData().removeTag("overrideKey");
				}
				else
				{
					player.getEntityData().setInteger("carrySlot", message.slot);
					if (message.carryOverride != 0)
						ScriptChecker.setCarryOnOverride(player, message.carryOverride);
				}
			}
		});

		return null;
	}

}
