package tschipp.carryon.common.capabilities;

import net.minecraft.nbt.NBTBase;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityInject;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;

/**
 * 玩家身上的 IPosition 能力提供者。
 *
 * <p>注意：1.7.10 没有 FML 的自动字段注入，
 * {@code POSITION_CAPABILITY} 需要在 preInit 里通过
 * {@code Capabilities.inject(PositionProvider.class)} 填充。
 */
public class PositionProvider implements ICapabilitySerializable
{
	@CapabilityInject
	public static Capability<IPosition> POSITION_CAPABILITY = null;

	private IPosition instance = POSITION_CAPABILITY == null ? new TEPosition() : POSITION_CAPABILITY.getDefaultInstance();

	@Override
	public boolean hasCapability(Capability<?> capability, EnumFacing facing)
	{
		return capability == POSITION_CAPABILITY;
	}

	@Override
	public <T> T getCapability(Capability<T> capability, EnumFacing facing)
	{
		return capability == POSITION_CAPABILITY ? (T) this.instance : null;
	}

	@Override
	public NBTBase serializeNBT()
	{
		return POSITION_CAPABILITY.getStorage().writeNBT(POSITION_CAPABILITY, this.instance, null);
	}

	@Override
	public void deserializeNBT(NBTBase nbt)
	{
		POSITION_CAPABILITY.getStorage().readNBT(POSITION_CAPABILITY, this.instance, null, nbt);
	}
}
