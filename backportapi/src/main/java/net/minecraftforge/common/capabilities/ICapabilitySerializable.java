package net.minecraftforge.common.capabilities;

import net.minecraft.nbt.NBTBase;

/**
 * 1.8+ ICapabilitySerializable 的 1.7.10 回填。
 * 既提供能力，又能把自己序列化进 NBT。
 */
public interface ICapabilitySerializable<N extends NBTBase> extends ICapabilityProvider
{
	N serializeNBT();

	void deserializeNBT(N nbt);
}
