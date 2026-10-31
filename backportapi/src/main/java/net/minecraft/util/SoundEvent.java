package net.minecraft.util;

/**
 * 1.8+ SoundEvent 的 1.7.10 回填。
 *
 * <p>1.7.10 播放声音用的是字符串（如 {@code "random.bow"}），没有 SoundEvent 对象。
 * 这个类只是一个"带名字的字符串"包装，播放时用 {@link #getSoundName()}。
 */
public class SoundEvent
{
	private final ResourceLocation registryName;

	public SoundEvent(ResourceLocation registryName)
	{
		this.registryName = registryName;
	}

	public static SoundEvent of(String soundName)
	{
		return new SoundEvent(new ResourceLocation(soundName));
	}

	public ResourceLocation getRegistryName()
	{
		return this.registryName;
	}

	/** 交给 1.7.10 的 world.playSoundAtEntity / playSoundEffect 使用的字符串。 */
	public String getSoundName()
	{
		return this.registryName == null ? "" : this.registryName.toString();
	}

	@Override
	public String toString()
	{
		return getSoundName();
	}
}
