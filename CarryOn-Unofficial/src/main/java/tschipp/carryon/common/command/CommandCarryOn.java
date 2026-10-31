package tschipp.carryon.common.command;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import net.backportapi.hand.Hands;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import tschipp.carryon.CarryOn;
import tschipp.carryon.common.handler.RegistrationHandler;
import tschipp.carryon.common.item.ItemEntity;
import tschipp.carryon.common.item.ItemTile;
import tschipp.carryon.network.client.CarrySlotPacket;

/**
 * {@code /carryon clear} —— 清除玩家身上搬运中的方块/生物。
 * {@code /carryon debug} —— 打印当前搬运对象的诊断信息。
 *
 * <p>1.7.10 的 ICommand 用 {@code processCommand}，不是 1.8 的 {@code execute}。
 */
public class CommandCarryOn extends CommandBase
{
	private final List<String> aliases = Arrays.asList("carryon");

	@Override
	public String getCommandName()
	{
		return "carryon";
	}

	@Override
	public String getCommandUsage(ICommandSender sender)
	{
		return "/carryon <clear|debug>";
	}

	@Override
	public List<String> getCommandAliases()
	{
		return this.aliases;
	}

	@Override
	public int getRequiredPermissionLevel()
	{
		return 2;
	}

	@Override
	public void processCommand(ICommandSender sender, String[] args) throws CommandException
	{
		if (args.length == 0)
			throw new WrongUsageException(this.getCommandUsage(sender));

		String mode = args[0].toLowerCase();

		if ("clear".equals(mode))
			clear(sender);
		else if ("debug".equals(mode))
			debug(sender);
		else
			throw new WrongUsageException(this.getCommandUsage(sender));
	}

	private void clear(ICommandSender sender) throws CommandException
	{
		if (!(sender instanceof EntityPlayer))
			throw new WrongUsageException("This command must be run by a player");

		EntityPlayer player = (EntityPlayer) sender;

		// 1.7.10 没有 clearMatchingItems；clearInventory(item, -1) 清空该物品的全部 meta
		int cleared = 0;
		cleared += player.inventory.clearInventory(RegistrationHandler.itemTile, -1);
		cleared += player.inventory.clearInventory(RegistrationHandler.itemEntity, -1);

		CarryOn.network.sendTo(new CarrySlotPacket(9, player.getEntityId()), (EntityPlayerMP) player);

		sender.addChatMessage(new ChatComponentText("Cleared " + cleared + (cleared == 1 ? " Item!" : " Items!")));
	}

	private void debug(ICommandSender sender)
	{
		if (!(sender instanceof EntityPlayer))
			return;

		EntityPlayer player = (EntityPlayer) sender;
		ItemStack main = Hands.getMainhand(player);

		if (main == null)
		{
			sender.addChatMessage(new ChatComponentText("Not carrying anything"));
			return;
		}

		if (main.getItem() == RegistrationHandler.itemTile)
		{
			String block = String.valueOf(ItemTile.getBlock(main));
			String state = String.valueOf(ItemTile.getBlockState(main));
			String meta = String.valueOf(ItemTile.getMeta(main));
			String stack = String.valueOf(ItemTile.getItemStack(main));

			sender.addChatMessage(new ChatComponentText("Block: " + block));
			sender.addChatMessage(new ChatComponentText("BlockState: " + state));
			sender.addChatMessage(new ChatComponentText("Meta: " + meta));
			sender.addChatMessage(new ChatComponentText("ItemStack: " + stack));

			CarryOn.LOGGER.info("Block: " + block);
			CarryOn.LOGGER.info("BlockState: " + state);
			CarryOn.LOGGER.info("Meta: " + meta);
			CarryOn.LOGGER.info("ItemStack: " + stack);
		}
		else if (main.getItem() == RegistrationHandler.itemEntity)
		{
			String entity = String.valueOf(ItemEntity.getEntity(main, player.worldObj));
			String name = String.valueOf(ItemEntity.getEntityName(main));

			sender.addChatMessage(new ChatComponentText("Entity: " + entity));
			sender.addChatMessage(new ChatComponentText("Entity Name: " + name));

			CarryOn.LOGGER.info("Entity: " + entity);
			CarryOn.LOGGER.info("Entity Name: " + name);
		}
	}

	@Override
	@SuppressWarnings("rawtypes")
	public List addTabCompletionOptions(ICommandSender sender, String[] args)
	{
		if (args.length == 1)
			return getListOfStringsMatchingLastWord(args, new String[] { "debug", "clear" });

		return Collections.emptyList();
	}

	@Override
	public boolean isUsernameIndex(String[] args, int index)
	{
		return false;
	}
}
