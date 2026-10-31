package tschipp.carryon.common.capabilities;

import net.minecraft.util.math.BlockPos;

/**
 * 记录玩家最近一次右键的方块实体坐标。
 *
 * <p>用于在容器 GUI 被意外关闭时判断"是不是搬走了一个正在打开的箱子"。
 * 1.8 用 Capability 承载；1.7.10 由 BackportAPI 的 Capability 层
 * （底层是 IExtendedEntityProperties）承载，接口保持不变。
 */
public interface IPosition
{
	public BlockPos getPos();

	public void setPos(BlockPos pos);

	public boolean isBlockActivated();

	public void setBlockActivated(boolean b);
}
