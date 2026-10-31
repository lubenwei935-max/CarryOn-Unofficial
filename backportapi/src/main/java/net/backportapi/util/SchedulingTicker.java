package net.backportapi.util;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * 主线程任务队列的 tick 驱动。
 *
 * <p><b>为什么必须是独立的 public 顶层类：</b>
 * FML 的 {@code EventBus} 会为每个 {@code @SubscribeEvent} 方法生成一个
 * {@code ASMEventHandler_* } 类，并把它放在独立的 {@code ASMClassLoader} 里。
 * 那个生成类要回调本类的方法，而它处于<b>不同的运行时包</b>，
 * 因此目标类必须是 {@code public} —— 否则 JVM 抛
 * {@code IllegalAccessError: failed to access class ...}。
 *
 * <p>匿名内部类（{@code new Object(){...}}）没有 public 修饰符，正是这个坑的典型触发点。
 */
public class SchedulingTicker
{
	@SubscribeEvent
	public void onServerTick(TickEvent.ServerTickEvent event)
	{
		if (event.phase != TickEvent.Phase.END)
			return;

		Scheduling.markServerThread();
		Scheduling.drain();
	}

	@SubscribeEvent
	public void onClientTick(TickEvent.ClientTickEvent event)
	{
		if (event.phase != TickEvent.Phase.END)
			return;

		Scheduling.drain();
	}
}
