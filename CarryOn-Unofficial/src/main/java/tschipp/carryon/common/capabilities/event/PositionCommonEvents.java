package tschipp.carryon.common.capabilities.event;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.backportapi.capability.Capabilities;
import net.backportapi.forge.event.AttachCapabilitiesEvent;
import net.backportapi.util.Worlds;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import tschipp.carryon.CarryOn;
import tschipp.carryon.common.capabilities.IPosition;
import tschipp.carryon.common.capabilities.PositionProvider;

public class PositionCommonEvents
{
	@SubscribeEvent
	public void onAttachCaps(AttachCapabilitiesEvent<Entity> event)
	{
		if (event.getObject() instanceof EntityPlayer)
			event.addCapability(new ResourceLocation(CarryOn.MODID, "position"), new PositionProvider());
	}

	@SubscribeEvent
	public void onBlockRight(PlayerInteractEvent event)
	{

		if (event.action != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK)
			return;
		BlockPos pos = new BlockPos(event.x, event.y, event.z);
		World world = event.world;
		EntityPlayer player = event.entityPlayer;

		if (event.isCanceled() || player == null || player instanceof FakePlayer)
			return;

		TileEntity te = Worlds.getTileEntity(world, pos);
		if (te == null)
			return;

		// 1.7.10 没有 player.getCapability()，改走 BackportAPI 的门面
		IPosition cap = Capabilities.get(player, PositionProvider.POSITION_CAPABILITY);
		if (cap != null)
		{
			cap.setBlockActivated(true);
			cap.setPos(pos);
		}
	}
}
