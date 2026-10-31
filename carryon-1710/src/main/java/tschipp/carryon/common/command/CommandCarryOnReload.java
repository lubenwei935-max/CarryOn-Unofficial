package tschipp.carryon.common.command;

import java.util.Collections;
import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;
import tschipp.carryon.CarryOn;
import tschipp.carryon.common.config.Configs;
import tschipp.carryon.common.scripting.ScriptReader;
import tschipp.carryon.network.client.ScriptReloadPacket;

/**
 * {@code /reloadscripts} —— 重新加载 JSON 拾取脚本并同步到所有客户端。
 */
public class CommandCarryOnReload extends CommandBase
{
	@Override
	public String getCommandName()
	{
		return "reloadscripts";
	}

	@Override
	public String getCommandUsage(ICommandSender sender)
	{
		return "/reloadscripts";
	}

	@Override
	public int getRequiredPermissionLevel()
	{
		return 2;
	}

	@Override
	public void processCommand(ICommandSender sender, String[] args) throws CommandException
	{
		if (Configs.Settings.useScripts)
		{
			ScriptReader.reloadScripts();
			sender.addChatMessage(new ChatComponentText("Successfully reloaded scripts!"));
			CarryOn.network.sendToAll(new ScriptReloadPacket());
		}
		else
		{
			sender.addChatMessage(new ChatComponentText("To use custom Carry On scripts, enable them in the config!"));
		}
	}

	@Override
	@SuppressWarnings("rawtypes")
	public List addTabCompletionOptions(ICommandSender sender, String[] args)
	{
		return Collections.emptyList();
	}
}
