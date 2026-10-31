package net.minecraftforge.common.capabilities;

import net.minecraft.util.EnumFacing;

/**
 * 1.8+ ICapabilityProvider 的 1.7.10 回填。
 */
public interface ICapabilityProvider
{
	boolean hasCapability(Capability<?> capability, EnumFacing facing);

	<T> T getCapability(Capability<T> capability, EnumFacing facing);
}
