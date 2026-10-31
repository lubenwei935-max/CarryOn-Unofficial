package net.minecraft.entity.player;

/**
 * 1.8+ EnumPlayerModelParts 的 1.7.10 回填。
 *
 * <p>1.7.10 没有"皮肤部件开关"这一层（渲染时由 ModelBiped 的 showModel 直接控制）。
 * 保留此枚举只为让 1.8 风格代码可编译；{@link #getPartMask()} 返回 1.8 的位掩码，
 * 便于移植代码做判断。
 */
public enum EnumPlayerModelParts
{
	CAPE(0),
	JACKET(1),
	LEFT_SLEEVE(2),
	RIGHT_SLEEVE(3),
	LEFT_PANTS_LEG(4),
	RIGHT_PANTS_LEG(5),
	HAT(6);

	private final int partId;

	private EnumPlayerModelParts(int partId)
	{
		this.partId = partId;
	}

	public int getPartId()
	{
		return this.partId;
	}

	public int getPartMask()
	{
		return 1 << this.partId;
	}
}
