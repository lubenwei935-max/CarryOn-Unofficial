package tschipp.carryon.client.render;

import java.util.HashSet;
import java.util.Set;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.client.FMLClientHandler;
import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.util.MathHelper;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraftforge.common.util.ForgeDirection;
import tschipp.carryon.CarryOn;

/**
 * 用模组自己的「世界渲染器」把自定义渲染类型的方块画成手持 3D 模型。
 *
 * <h3>为什么</h3>
 * cfm 这类模组把几何全部写在 {@code ISimpleBlockRenderingHandler.renderWorldBlock}，
 * {@code renderInventoryBlock} 是空的、{@code shouldRender3DInInventory} 返回 false，
 * 也从不注册 {@code IItemRenderer}。所以 {@code RenderBlocks.renderBlockAsItem} 一个顶点
 * 都不会输出，而物品栏里的那张 2D 图标又是独立的 {@code Item} —— 上一版修复为了保证
 * 「不隐形」，退而画了那张图标。用户要的是模型，图标不够。
 *
 * <p>唯一能拿到 3D 几何的入口就是 {@code renderWorldBlock}。给它一个只有 (0,0,0)
 * 是目标方块的假 {@link IBlockAccess}，再自己开闭 Tessellator 批次即可。
 * cfm 的世界渲染器经反编译确认：只读 {@code getBlockMetadata} 和一个全局 renderPass，
 * <b>不读 TileEntity</b>，所以洗衣机/洗碗机这类容器方块同样适用。
 *
 * <h3>状态隔离</h3>
 * {@link RenderBlocks} 是全局单例，手部渲染发生在世界渲染之后。这里保存并完整还原
 * {@code blockAccess / overrideBlockTexture / renderAllFaces / partialRenderBounds /
 * flipTexture / uvRotate*}；GL 矩阵用一对 push/pop 包住；批次的开始与提交严格配对。
 * 任何异常都只让本类返回 false，由调用方退回 2D 图标，不会中断整帧渲染。
 *
 * <h3>日志证据</h3>
 * 每个方块只报一次，格式 {@code [CarryOn] world model OK/MISS: <name> ...}，
 * 便于直接从 fml-client-latest.log 判断模型到底是走 3D 还是退回了图标。
 */
@SideOnly(Side.CLIENT)
public final class CustomBlockModel
{
	/** 只有原点放着目标方块的假世界，渲染线程复用，避免每帧分配对象。 */
	private static final SingleBlockAccess ACCESS = new SingleBlockAccess();

	private static final Set<String> REPORTED = new HashSet<String>();
	private static final Set<String> WARNED = new HashSet<String>();

	private CustomBlockModel()
	{
	}

	/**
	 * @return true 表示确认画出了 3D 几何；false 表示应当退回 2D 图标。
	 *         注意：返回 false 时不保证一个顶点都没画，所以调用方要么信任 true，
	 *         要么在 false 时只画图标；本类内部已尽力让「没画出来」的情况返回 false。
	 */
	public static boolean render(Block block, int meta)
	{
		if (block == null)
			return false;

		int renderType = block.getRenderType();
		RenderBlocks rb = RenderBlocks.getInstance();
		if (rb == null)
			return false;

		ACCESS.set(block, meta);

		// ---- 保存全局单例的状态 ----
		IBlockAccess prevAccess = rb.blockAccess;
		IIcon prevOverride = rb.overrideBlockTexture;
		boolean prevAllFaces = rb.renderAllFaces;
		boolean prevPartial = rb.partialRenderBounds;
		boolean prevFlip = rb.flipTexture;
		int prevEast = rb.uvRotateEast;
		int prevWest = rb.uvRotateWest;
		int prevSouth = rb.uvRotateSouth;
		int prevNorth = rb.uvRotateNorth;
		int prevTop = rb.uvRotateTop;
		int prevBottom = rb.uvRotateBottom;

		Tessellator tess = Tessellator.instance;
		boolean handled = false;
		boolean batchOpen = false;
		boolean ok = false;
		int vertexData = 0;
		int passes = 0;

		// push 必须在 try 之外：finally 里无条件 pop，绝不能多弹别人的矩阵。
		GL11.glPushMatrix();
		try
		{
			// 3D 几何用的是方块图集；SpriteFallback 会切到物品图集，两者不能混用。
			FMLClientHandler.instance().getClient().getTextureManager()
					.bindTexture(TextureMap.locationBlocksTexture);
			GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

			rb.blockAccess = ACCESS;
			rb.renderAllFaces = false;
			rb.partialRenderBounds = false;
			rb.flipTexture = false;
			rb.uvRotateEast = 0;
			rb.uvRotateWest = 0;
			rb.uvRotateSouth = 0;
			rb.uvRotateNorth = 0;
			rb.uvRotateTop = 0;
			rb.uvRotateBottom = 0;
			rb.clearOverrideBlockTexture();
			rb.setRenderBounds(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D);

			// 世界几何是 0..1，物品/手持渲染的约定是几何中心在原点。
			GL11.glTranslatef(-0.5F, -0.5F, -0.5F);

			// pass 0 必画；pass 1 也要尝试：cfm 的玻璃门只在 renderPass == 1 时追加，
			// 而 canRenderInPass(pass) 的副作用正是把 ClientProxy.renderPass 设成 pass，
			// 所以必须先调它再调渲染器。普通方块的 canRenderInPass 会跳过 pass 1。
			int lastPass = Math.max(1, block.getRenderBlockPass());
			for (int pass = 0; pass <= lastPass; pass++)
			{
				boolean allowed;
				try
				{
					allowed = block.canRenderInPass(pass);
				}
				catch (Throwable ignored)
				{
					allowed = pass == 0;
				}
				if (!allowed)
					continue;

				rb.setRenderBounds(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D);

				beginQuads(tess);
				batchOpen = true;
				try
				{
					if (RenderingRegistry.instance()
							.renderWorldBlock(rb, ACCESS, 0, 0, 0, block, renderType))
						handled = true;
				}
				finally
				{
					try
					{
						vertexData += endQuads(tess);
					}
					finally
					{
						batchOpen = false;
						passes++;
					}
				}
			}
		}
		catch (Throwable t)
		{
			warnOnce(block, t);
			handled = false;
		}
		finally
		{
			if (batchOpen)
			{
				endQuads(tess);
				batchOpen = false;
			}
			GL11.glPopMatrix();

			// ---- 还原全局单例 ----
			rb.blockAccess = prevAccess;
			rb.overrideBlockTexture = prevOverride;
			rb.renderAllFaces = prevAllFaces;
			rb.partialRenderBounds = prevPartial;
			rb.flipTexture = prevFlip;
			rb.uvRotateEast = prevEast;
			rb.uvRotateWest = prevWest;
			rb.uvRotateSouth = prevSouth;
			rb.uvRotateNorth = prevNorth;
			rb.uvRotateTop = prevTop;
			rb.uvRotateBottom = prevBottom;
			rb.setRenderBounds(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D);

			// cfm 把 renderPass 存在 ClientProxy 的静态字段里。这里复位成 pass 0，
			// 免得下一帧世界渲染的第一批家具误以为自己还在透明 pass。
			try
			{
				block.canRenderInPass(0);
			}
			catch (Throwable ignored)
			{
			}

			ok = handled && vertexData > 0;
			reportOnce(block, renderType, ok, passes, vertexData);
		}

		return ok;
	}

	/** 开始四边形批次；若上一段批次还没收尾，先替它提交，避免 Tessellator 卡死。 */
	private static void beginQuads(Tessellator tess)
	{
		try
		{
			tess.startDrawingQuads();
		}
		catch (IllegalStateException alreadyDrawing)
		{
			try
			{
				tess.draw();
			}
			catch (Throwable ignored)
			{
			}
			tess.startDrawingQuads();
		}
	}

	/** 提交批次；返回 draw() 给出的顶点数据量，用于日志与成功判定。 */
	private static int endQuads(Tessellator tess)
	{
		try
		{
			return tess.draw();
		}
		catch (IllegalStateException notDrawing)
		{
			return 0;
		}
	}

	private static String nameOf(Block block)
	{
		try
		{
			return String.valueOf(Block.blockRegistry.getNameForObject(block));
		}
		catch (Throwable t)
		{
			return block.getClass().getName();
		}
	}

	private static void reportOnce(Block block, int renderType, boolean ok, int passes, int vertexData)
	{
		String name = nameOf(block);
		if (!REPORTED.add(name))
			return;

		String msg = "[CarryOn] world model " + (ok ? "OK" : "MISS") + ": " + name
				+ " renderType=" + renderType + " passes=" + passes + " vertexData=" + vertexData;
		if (ok)
			CarryOn.LOGGER.info(msg);
		else
			CarryOn.LOGGER.warn(msg + " -> falling back to 2D icon");
	}

	private static void warnOnce(Block block, Throwable t)
	{
		String name = nameOf(block);
		if (!WARNED.add(name))
			return;
		CarryOn.LOGGER.warn("[CarryOn] world model threw for " + name
				+ " (renderType=" + block.getRenderType() + ") -> falling back to 2D icon", t);
	}

	/**
	 * 只有 (0,0,0) 是目标方块的假世界。亮度给满，搬运物不随所处位置变暗；
	 * 四周全是空气，所以 {@code renderStandardBlock} 会画出全部外表面。
	 */
	private static final class SingleBlockAccess implements IBlockAccess
	{
		private Block block = Blocks.air;
		private int meta;

		void set(Block block, int meta)
		{
			this.block = block;
			this.meta = meta;
		}

		@Override
		public Block getBlock(int x, int y, int z)
		{
			return (x | y | z) == 0 ? this.block : Blocks.air;
		}

		@Override
		public TileEntity getTileEntity(int x, int y, int z)
		{
			return null;
		}

		@Override
		public int getLightBrightnessForSkyBlocks(int x, int y, int z, int min)
		{
			return 0xF000F0;
		}

		@Override
		public int getBlockMetadata(int x, int y, int z)
		{
			return (x | y | z) == 0 ? this.meta : 0;
		}

		@Override
		public int isBlockProvidingPowerTo(int x, int y, int z, int direction)
		{
			return 0;
		}

		@Override
		public boolean isAirBlock(int x, int y, int z)
		{
			return (x | y | z) != 0;
		}

		@Override
		public BiomeGenBase getBiomeGenForCoords(int x, int z)
		{
			try
			{
				net.minecraft.client.Minecraft mc = FMLClientHandler.instance().getClient();
				World world = mc == null ? null : mc.theWorld;
				EntityPlayer player = mc == null ? null : mc.thePlayer;
				if (world != null && player != null)
					return world.getBiomeGenForCoords(MathHelper.floor_double(player.posX),
							MathHelper.floor_double(player.posZ));
			}
			catch (Throwable ignored)
			{
			}
			return BiomeGenBase.plains;
		}

		@Override
		public int getHeight()
		{
			return 256;
		}

		@Override
		public boolean extendedLevelsInChunkCache()
		{
			return false;
		}

		@Override
		public boolean isSideSolid(int x, int y, int z, ForgeDirection side, boolean _default)
		{
			return (x | y | z) == 0 ? false : _default;
		}
	}
}
