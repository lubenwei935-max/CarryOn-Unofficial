package tschipp.carryon.common.helper;

import net.backportapi.state.BlockStates;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTException;
import net.minecraft.nbt.NBTTagCompound;

public class StringParser
{
	public static Block getBlock(String string)
	{
		if (string == null)
			return null;

		// 去掉 {...} 部分；NBT 由 getTagCompound 单独解析。
		// 不用 tag.toString() 做替换：NBT 的字符串化格式未必与原文一致。
		if (string.contains("{"))
			string = string.substring(0, string.indexOf("{"));

		if (string.contains(";"))
			string = string.replace(string.substring(string.indexOf(";")), "");

		// 1.7.10 没有 Block.getBlockFromName，走注册表
		Object block = Block.blockRegistry.getObject(string);
		if (!(block instanceof Block))
		{
			new InvalidConfigException("Block Parsing Error. Invalid Name: " + string).printException();
			return null;
		}

		return (Block) block;
	}

	public static int getMeta(String string)
	{
		if (string == null)
			return 0;

		// 去掉 {...} 部分；NBT 由 getTagCompound 单独解析。
		// 不用 tag.toString() 做替换：NBT 的字符串化格式未必与原文一致。
		if (string.contains("{"))
			string = string.substring(0, string.indexOf("{"));

		if (string.contains(";"))
		{
			int meta = 0;
			try
			{
				meta = Integer.parseInt(string.substring(string.indexOf(";")).replace(";", ""));
			}
			catch (Exception e)
			{
				new InvalidConfigException("Meta Parsing Error at: " + string + " : " + e.getMessage()).printException();
			}
			return meta;
		}
		return 0;
	}

	public static IBlockState getBlockState(String string)
	{
		if (string == null)
			return null;

		Block block = getBlock(string);
		if (block == null)
			return null;

		int meta = getMeta(string);
		return BlockStates.getStateFromMeta(block, meta);
	}

	public static Item getItem(String string)
	{
		if (string == null)
			return null;

		// 去掉 {...} 部分；NBT 由 getTagCompound 单独解析。
		// 不用 tag.toString() 做替换：NBT 的字符串化格式未必与原文一致。
		if (string.contains("{"))
			string = string.substring(0, string.indexOf("{"));

		if (string.contains(";"))
			string = string.replace(string.substring(string.indexOf(";")), "");

		return (Item) Item.itemRegistry.getObject(string);
	}

	public static ItemStack getItemStack(String string)
	{
		if (string == null)
			return null;

		Item item = getItem(string);
		if (item == null)
			return null;

		ItemStack stack = new ItemStack(item, 1, getMeta(string));
		NBTTagCompound tag = getTagCompound(string);
		if (tag != null)
			stack.setTagCompound(tag);

		return stack;
	}

	public static NBTTagCompound getTagCompound(String string)
	{
		NBTTagCompound tag = null;
		if (string == null)
			return null;

		if (string.contains("{"))
		{
			if (!string.contains("}"))
			{
				new InvalidConfigException("Missing } at  : " + string).printException();
				return null;
			}

			String nbt = string.substring(string.indexOf("{"));
			string = string.replace(nbt, "");
			try
			{
				// 1.7.10 的 MCP 未映射此方法名，直接用 srg 名
				NBTBase parsed = (NBTTagCompound) JsonToNBT.func_150315_a(nbt);
				if (parsed instanceof NBTTagCompound)
					tag = (NBTTagCompound) parsed;
			}
			catch (NBTException e)
			{
				new InvalidConfigException("Error while parsing NBT: " + e.getMessage()).printException();
				return null;
			}
		}
		else if (string.contains("}"))
		{
			new InvalidConfigException("Missing { at  : " + string).printException();
		}

		return tag;
	}
}
