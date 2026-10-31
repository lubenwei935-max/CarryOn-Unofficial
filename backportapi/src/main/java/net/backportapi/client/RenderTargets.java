package net.backportapi.client;

import org.lwjgl.opengl.GL11;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;

/**
 * {@link RenderTarget} 的工厂与实现。
 *
 * <p>对应 1.8 的 {@code Minecraft.getBlockRendererDispatcher().getModelForState(state)}
 * 与 {@code Minecraft.getRenderItem().getItemModelWithOverrides(stack, world, player)}。
 *
 * <h3>1.7.10 的三种绘制形态</h3>
 * <ol>
 *   <li><b>方块</b> → {@code RenderBlocks.renderBlockAsItem(block, meta, 1.0F)}</li>
 *   <li><b>方块物品</b>（ItemBlock）→ 同样走 3D 方块，因为 1.8 的方块物品模型就是方块本身</li>
 *   <li><b>扁平物品</b> → 用 {@code ItemRenderer.renderItemIn2D} 画带厚度的精灵</li>
 * </ol>
 */
public final class RenderTargets
{
	private RenderTargets()
	{
	}

	/** 方块形态。 */
	public static RenderTarget ofBlock(Block block, int meta)
	{
		return block == null ? null : new BlockTarget(block, meta);
	}

	/** 方块形态，等价于 1.8 的 getModelForState。 */
	public static RenderTarget ofState(net.minecraft.block.state.IBlockState state)
	{
		return state == null ? null : new BlockTarget(state.getBlock(), state.getMeta());
	}

	/**
	 * 物品形态，不指定 meta。方块物品会退化为扁平精灵。
	 */
	public static RenderTarget ofItem(ItemStack stack)
	{
		return ofItem(stack, 0);
	}

	/**
	 * 物品形态。若该物品是 ItemBlock，则按方块渲染（meta 由调用方给出，
	 * 避免依赖 ItemStack 上那个在 1.7.10 命名不一致的 getter）。
	 */
	public static RenderTarget ofItem(ItemStack stack, int meta)
	{
		if (stack == null)
			return null;

		Item item = stack.getItem();
		if (item instanceof ItemBlock)
		{
			Block block = ((ItemBlock) item).field_150939_a;
			if (block != null)
				return new BlockTarget(block, meta);
		}

		return new ItemTarget(stack);
	}

	// ---------- 方块 ----------

	private static final class BlockTarget implements RenderTarget
	{
		private final Block block;
		private final int meta;

		BlockTarget(Block block, int meta)
		{
			this.block = block;
			this.meta = meta & 15;
		}

		@Override
		public void render(float brightness)
		{
			GL11.glPushMatrix();

			// 1.7.10 的 renderBlockAsItem 并不保证"几何中心在原点"：
			//   渲染类型 0（标准方块）内部有 translate(-0.5,-0.5,-0.5)，中心对齐；
			//   渲染类型 22（箱子/末影箱）走 TESR：模型本身 y 是 0.125~1.0，
			//   经过 TESR 的 (x, 1-y, 1-z) 之后变成 0~0.875，再被 renderBlockAsItem
			//   自带的 translate(-0.5,-0.5,-0.5) 变成 -0.5~0.375。
			//   也就是说它<b>已经基本居中</b>了（中心在 -0.0625，只差 1/16 格）。
			// 之前这里写成 -0.4375（误以为模型仍停在 0~0.875），结果把箱子整体压低了
			// 半格——第一人称放大 2.5 倍后就是低了 1.25 格，几乎整只沉到屏幕外。
			if (this.block.getRenderType() == 22)
				GL11.glTranslatef(0.0F, 0.0625F, 0.0F);

			RenderBlocks.getInstance().renderBlockAsItem(this.block, this.meta, brightness);

			GL11.glPopMatrix();
		}

		@Override
		public IIcon getIcon()
		{
			Item item = Item.getItemFromBlock(this.block);
			if (item == null)
				return null;
			return item.getIconIndex(new ItemStack(this.block, 1, this.meta));
		}

		@Override
		public String getDisplayName()
		{
			Item item = Item.getItemFromBlock(this.block);
			if (item == null)
				return this.block.getLocalizedName();
			return new ItemStack(this.block, 1, this.meta).getDisplayName();
		}

		@Override
		public boolean isBlock()
		{
			return true;
		}
	}

	// ---------- 扁平物品 ----------

	private static final class ItemTarget implements RenderTarget
	{
		private final ItemStack stack;

		ItemTarget(ItemStack stack)
		{
			this.stack = stack;
		}

		@Override
		public void render(float brightness)
		{
			IIcon icon = this.stack.getIconIndex();
			if (icon == null)
				return;

			Minecraft mc = Minecraft.getMinecraft();
			boolean blockAtlas = this.stack.getItem() instanceof ItemBlock;
			mc.getTextureManager().bindTexture(blockAtlas ? TextureMap.locationBlocksTexture : TextureMap.locationItemsTexture);

			GL11.glPushMatrix();
			GL11.glEnable(GL11.GL_BLEND);
			GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
			GL11.glEnable(GL11.GL_ALPHA_TEST);
			GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

			Tessellator tess = Tessellator.instance;
			tess.startDrawingQuads();
			tess.setNormal(0.0F, 0.0F, 1.0F);
			ItemRenderer.renderItemIn2D(tess, icon.getMaxU(), icon.getMinV(), icon.getMinU(), icon.getMaxV(), icon.getIconWidth(), icon.getIconHeight(), 0.0625F);
			tess.draw();

			GL11.glDisable(GL11.GL_BLEND);
			GL11.glPopMatrix();
		}

		@Override
		public IIcon getIcon()
		{
			return this.stack.getIconIndex();
		}

		@Override
		public String getDisplayName()
		{
			return this.stack.getDisplayName();
		}

		@Override
		public boolean isBlock()
		{
			return false;
		}
	}
}
