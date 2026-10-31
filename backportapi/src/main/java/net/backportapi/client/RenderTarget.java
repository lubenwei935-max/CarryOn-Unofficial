package net.backportapi.client;

import net.minecraft.util.IIcon;

/**
 * 1.7.10 版的 "baked model" 抽象。
 *
 * <h3>为什么需要它</h3>
 * 1.8+ 有 {@code IBakedModel}：一个对象就代表"怎么画这个东西"，
 * 可以自由替换（模型覆盖、自定义渲染都靠它）。
 * 1.7.10 没有这层抽象——渲染是硬编码的：
 * <ul>
 *   <li>方块 → {@code RenderBlocks.renderBlockAsItem(block, meta, brightness)}</li>
 *   <li>物品 → {@code ItemRenderer.renderItem(entity, stack, pass)}</li>
 * </ul>
 *
 * <p>本接口把这两种绘制方式统一成一个可替换的对象，
 * 于是"把 A 渲染成 B"这种需求（例如把点燃的熔炉渲染成熔炉）
 * 可以像 1.8 一样用一个值来表达。
 *
 * <h3>使用约定</h3>
 * {@link #render(float)} <b>不</b>负责矩阵变换、贴图绑定和光照，
 * 调用方需自行 push/pop 矩阵、绑定方块贴图、设置 lightmap。
 * 这与 1.8 中 {@code RenderItem.renderItem(stack, model)} 的分工一致。
 */
public interface RenderTarget
{
	/** 在当前矩阵下绘制。brightness 通常传 1.0F。 */
	void render(float brightness);

	/** 2D 图标（背包/快捷栏用）；无法提供时返回 null。 */
	IIcon getIcon();

	/** 显示名，用于物品 tooltip。 */
	String getDisplayName();

	/** true = 方块形态，false = 物品形态。 */
	boolean isBlock();
}
