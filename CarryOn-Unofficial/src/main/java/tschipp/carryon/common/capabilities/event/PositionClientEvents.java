package tschipp.carryon.common.capabilities.event;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent.PlayerTickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.backportapi.capability.Capabilities;
import net.backportapi.forge.event.PlayerContainerEvent;
import net.backportapi.util.Worlds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.client.event.GuiScreenEvent;
import tschipp.carryon.common.capabilities.IPosition;
import tschipp.carryon.common.capabilities.PositionProvider;

/**
 * 容器 GUI 打开期间，若对应的 TileEntity 已经不存在（被搬走了），
 * 就强制关闭界面，避免刷物品。
 */
public class PositionClientEvents
{
	@SideOnly(Side.CLIENT)
	@SubscribeEvent
	public void onGui(GuiScreenEvent.DrawScreenEvent event)
	{
		if (event.gui == null)
			return;

		EntityPlayer player = Minecraft.getMinecraft().thePlayer;
		if (player == null || !(event.gui instanceof GuiContainer))
			return;

		IPosition cap = Capabilities.get(player, PositionProvider.POSITION_CAPABILITY);
		if (cap == null || !cap.isBlockActivated())
			return;

		World world = player.worldObj;
		if (world == null)
			return;

		TileEntity te = Worlds.getTileEntity(world, cap.getPos());
		if (te == null)
		{
			Minecraft.getMinecraft().currentScreen = null;
			Minecraft.getMinecraft().setIngameFocus();
			cap.setBlockActivated(false);
			cap.setPos(new BlockPos(0, 0, 0));
		}
	}

	@SideOnly(Side.CLIENT)
	@SubscribeEvent
	public void onGuiClose(PlayerContainerEvent.Close event)
	{
		EntityPlayer player = event.entityPlayer;
		IPosition cap = Capabilities.get(player, PositionProvider.POSITION_CAPABILITY);
		if (cap != null)
		{
			cap.setBlockActivated(false);
			cap.setPos(new BlockPos(0, 0, 0));
		}
	}

	@SideOnly(Side.CLIENT)
	@SubscribeEvent
	public void onPlayerTick(PlayerTickEvent event)
	{
		if (event.side != Side.CLIENT || event.player == null)
			return;

		IPosition cap = Capabilities.get(event.player, PositionProvider.POSITION_CAPABILITY);
		if (cap != null && cap.isBlockActivated() && Minecraft.getMinecraft().currentScreen == null)
		{
			cap.setBlockActivated(false);
			cap.setPos(new BlockPos(0, 0, 0));
		}
	}
}
