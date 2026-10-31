package net.minecraft.util;

/**
 * 1.8+ IThreadListener 的 1.7.10 回填。
 *
 * <p>1.8 用 {@code IThreadListener.addScheduledTask} 把任务调回主线程；
 * 1.7.10 的等价物是 {@code MinecraftServer.func_152344_a(Runnable)}。
 * 本接口只为让移植代码的签名成立，实际调度见
 * {@link net.backportapi.util.Scheduling}。
 */
public interface IThreadListener
{
	void addScheduledTask(Runnable runnable);

	boolean isCallingFromMinecraftThread();
}
