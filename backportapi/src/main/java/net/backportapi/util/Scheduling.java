package net.backportapi.util;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import cpw.mods.fml.common.FMLCommonHandler;

/**
 * 主线程任务调度（1.8 的 {@code IThreadListener.addScheduledTask} 的 1.7.10 等价物）。
 *
 * <p>1.7.10 的 {@code MinecraftServer} 没有暴露调度队列，所以这里自己维护一个，
 * 由 {@link SchedulingTicker} 在 tick 结束时排空。已在主线程时直接执行，避免无谓延迟。
 */
public final class Scheduling
{
	private static final Queue<Runnable> QUEUE = new ConcurrentLinkedQueue<Runnable>();

	/** 主线程（客户端为 Client thread，服务端为 Server thread），在 init() 时捕获。 */
	private static volatile Thread mainThread;
	private static volatile boolean registered;

	private Scheduling()
	{
	}

	/**
	 * 在 preInit 调用。此时正处于主线程，因此可安全捕获。
	 * 注册的是独立的 public 类 {@link SchedulingTicker}，避免 ASMEventHandler 的访问错误。
	 */
	public static void init()
	{
		if (registered)
			return;

		mainThread = Thread.currentThread();
		registered = true;
		FMLCommonHandler.instance().bus().register(new SchedulingTicker());
	}

	public static void addScheduledTask(Runnable runnable)
	{
		if (runnable == null)
			return;

		if (isCallingFromMinecraftThread())
		{
			runnable.run();
			return;
		}

		QUEUE.add(runnable);
	}

	public static boolean isCallingFromMinecraftThread()
	{
		Thread main = mainThread;
		return main == null || Thread.currentThread() == main;
	}

	public static void markServerThread()
	{
		if (mainThread == null)
			mainThread = Thread.currentThread();
	}

	public static void drain()
	{
		Runnable task;
		while ((task = QUEUE.poll()) != null)
		{
			try
			{
				task.run();
			}
			catch (Throwable t)
			{
				t.printStackTrace();
			}
		}
	}
}
