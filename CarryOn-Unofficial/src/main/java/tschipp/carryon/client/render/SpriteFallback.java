package tschipp.carryon.client.render;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.client.FMLClientHandler;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.backportapi.client.RenderTarget;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;

/**
 * 兼容性回退：用「物品形态」的 2D 图标把搬运物画出来。
 *
 * <h3>为什么需要</h3>
 * 1.7.10 里有一大批模组（MrCrayfish's Furniture 等）采用「方块 / 物品解耦」注册：
 * 方块只实现 {@code ISimpleBlockRenderingHandler.renderWorldBlock}（世界里怎么画），
 * 而物品栏形态是一个独立的 {@code Item} + 2D 图标，
 * {@code renderInventoryBlock} 为空、{@code shouldRender3DInInventory} 返回 false。
 * {@code RenderBlocks.renderBlockAsItem} 遇到这种方块会走进那个空实现，
 * 一个顶点都不输出，于是 CarryOn 的第一人称里模型凭空消失。
 *
 * <p>本类不尝试去调 {@code renderWorldBlock}（那需要伪造 IBlockAccess、自己开闭
 * Tessellator 批次、处理 pass 状态，副作用很大），而是走这些模组自己选定的
 * 物品形态：画它的 2D 图标。这和原版物品栏、手上拿着该物品时看到的东西一致。
 *
 * <h3>低风险约束</h3>
 * 只在调用方判定「该 renderType 没有 3D 物品模型」时才会用到本类；
 * 不改写 {@code RenderBlocks} 单例的任何状态，不改 {@code blockAccess}，
 * 不碰 {@code canRenderInPass}，只在自己的 render 里开闭一次 Tessellator 批次。
 */
@SideOnly(Side.CLIENT)
public final class SpriteFallback implements RenderTarget
{
	private final ItemStack stack;

	public SpriteFallback(ItemStack stack)
	{
		this.stack = stack;
	}

	@Override
	public void render(float brightness)
	{
		if (this.stack == null || this.stack.getItem() == null)
			return;

		IIcon icon = this.stack.getIconIndex();
		if (icon == null)
			return;

		// 1.7.10 有两张图集：方块贴图在方块图集、独立 Item 的贴图在物品图集。
		// 用 FMLClientHandler 取客户端，避免依赖某些整合包里被移除的
		// Minecraft.getMinecraft() 静态方法。
		boolean blockAtlas = this.stack.getItem() instanceof ItemBlock;
		FMLClientHandler.instance().getClient().getTextureManager()
				.bindTexture(blockAtlas ? TextureMap.locationBlocksTexture : TextureMap.locationItemsTexture);

		GL11.glPushMatrix();
		GL11.glEnable(GL11.GL_BLEND);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
		GL11.glEnable(GL11.GL_ALPHA_TEST);
		GL11.glColor4f(brightness, brightness, brightness, 1.0F);

		Tessellator tess = Tessellator.instance;
		tess.startDrawingQuads();
		tess.setNormal(0.0F, 0.0F, 1.0F);
		ItemRenderer.renderItemIn2D(tess, icon.getMaxU(), icon.getMinV(),
				icon.getMinU(), icon.getMaxV(), icon.getIconWidth(), icon.getIconHeight(), 0.0625F);
		tess.draw();

		GL11.glDisable(GL11.GL_BLEND);
		GL11.glPopMatrix();
	}

	@Override
	public IIcon getIcon()
	{
		return this.stack == null || this.stack.getItem() == null ? null : this.stack.getIconIndex();
	}

	@Override
	public String getDisplayName()
	{
		return this.stack == null ? "" : this.stack.getDisplayName();
	}

	@Override
	public boolean isBlock()
	{
		return false;
	}
}
