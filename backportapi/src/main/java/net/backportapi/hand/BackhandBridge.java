package net.backportapi.hand;

import java.lang.reflect.Method;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

/**
 * Backhand 模组的反射桥。
 *
 * <p>用反射而不是编译期依赖，这样：
 * <ul>
 *   <li>没装 Backhand 时库照常工作，副手恒为空；</li>
 *   <li>不会把 Backhand 变成硬前置。</li>
 * </ul>
 *
 * <p>对接的 API（Backhand 1.8.17）：
 * <pre>
 * xonin.backhand.api.core.BackhandUtils.getOffhandItem(EntityPlayer)
 * xonin.backhand.api.core.BackhandUtils.setPlayerOffhandItem(EntityPlayer, ItemStack)
 * xonin.backhand.api.core.BackhandUtils.isValidPlayer(Entity)
 * </pre>
 */
final class BackhandBridge
{
	private static final String UTILS = "xonin.backhand.api.core.BackhandUtils";

	private static boolean resolved;
	private static Method getOffhand;
	private static Method setOffhand;
	private static Method isValidPlayer;

	private BackhandBridge()
	{
	}

	private static synchronized void resolve()
	{
		if (resolved)
			return;
		resolved = true;

		try
		{
			Class<?> utils = Class.forName(UTILS);
			getOffhand = utils.getMethod("getOffhandItem", EntityPlayer.class);
			setOffhand = utils.getMethod("setPlayerOffhandItem", EntityPlayer.class, ItemStack.class);
			isValidPlayer = utils.getMethod("isValidPlayer", net.minecraft.entity.Entity.class);
		}
		catch (Throwable ignored)
		{
			getOffhand = null;
			setOffhand = null;
			isValidPlayer = null;
		}
	}

	static boolean isAvailable()
	{
		resolve();
		return getOffhand != null && setOffhand != null;
	}

	static ItemStack get(EntityPlayer player)
	{
		resolve();
		if (getOffhand == null)
			return null;
		try
		{
			return (ItemStack) getOffhand.invoke(null, player);
		}
		catch (Throwable t)
		{
			return null;
		}
	}

	static void set(EntityPlayer player, ItemStack stack)
	{
		resolve();
		if (setOffhand == null)
			return;
		try
		{
			setOffhand.invoke(null, player, stack);
		}
		catch (Throwable ignored)
		{
		}
	}
}
