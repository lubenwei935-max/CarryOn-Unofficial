package tschipp.carryon.common.handler;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

/**
 * 放置后的朝向修正器。
 *
 * <p>有些方块的"正面朝哪"并不在 metadata 里，而是存在 TileEntity 的字段里
 * （例如林业的箱子用 {@code TileForestry.orientation}、神秘时代的蒸馏器用
 * {@code TileAlembic.facing}）。CarryOn 的放置流程是
 * {@code setBlockState → onBlockPlacedBy → tile.readFromNBT}，
 * 于是这类方块会出现两种情况：
 * <ul>
 *   <li>朝向只由 ItemBlock 设置（林业）——CarryOn 直接写世界，永远不经过 ItemBlock，
 *       字段保持构造时的默认值；</li>
 *   <li>朝向由 {@code onBlockPlacedBy} 算对了（神秘时代）——紧接着被
 *       {@code readFromNBT} 读回来的旧值冲掉。</li>
 * </ul>
 *
 * <p>所以修正必须发生在 <b>NBT 恢复之后</b>，见
 * {@link PostPlaceRotators#apply(World, int, int, int, Block, int, EntityPlayer)}。
 *
 * <p><b>实现约束</b>：适配器一律通过反射访问目标模组的类，不允许出现
 * {@code import forestry.*} / {@code import thaumcraft.*}——目标 mod 缺席时
 * 本模组必须照常加载。
 */
public interface IPostPlaceRotator
{
	/**
	 * 在方块已放置、TE NBT 已恢复之后调用，用于修正 TE 中的朝向。
	 *
	 * @return true 表示该方块已被本适配器接管（无论最终是否改变了朝向）
	 */
	boolean rotate(World world, int x, int y, int z, Block block, int meta, EntityPlayer player);
}
