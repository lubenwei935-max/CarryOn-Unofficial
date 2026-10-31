package net.minecraft.client.renderer;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

/**
 * 1.8+ GlStateManager 的 1.7.10 回填。
 *
 * <p>1.8 引入 GlStateManager 主要是为了<b>缓存</b> GL 状态、减少冗余调用；
 * 功能上等价于直接调用 GL11。这里不做状态缓存（性能略差，但语义完全一致），
 * 目的是让 1.8 风格的渲染代码可以原样编译运行。
 *
 * <p>注意：本类只在客户端加载，服务端不要引用。
 */
public class GlStateManager
{
	// GL12 常量，GL11 里没有定义
	private static final int GL_RESCALE_NORMAL = 0x803A;

	private GlStateManager()
	{
	}

	public static void pushMatrix()
	{
		GL11.glPushMatrix();
	}

	public static void popMatrix()
	{
		GL11.glPopMatrix();
	}

	public static void translate(float x, float y, float z)
	{
		GL11.glTranslatef(x, y, z);
	}

	public static void translate(double x, double y, double z)
	{
		GL11.glTranslated(x, y, z);
	}

	public static void rotate(float angle, float x, float y, float z)
	{
		GL11.glRotatef(angle, x, y, z);
	}

	public static void scale(float x, float y, float z)
	{
		GL11.glScalef(x, y, z);
	}

	public static void scale(double x, double y, double z)
	{
		GL11.glScaled(x, y, z);
	}

	public static void color(float r, float g, float b)
	{
		GL11.glColor3f(r, g, b);
	}

	public static void color(float r, float g, float b, float a)
	{
		GL11.glColor4f(r, g, b, a);
	}

	public static void resetColor()
	{
		GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
	}

	public static void enableTexture2D()
	{
		GL11.glEnable(GL11.GL_TEXTURE_2D);
	}

	public static void disableTexture2D()
	{
		GL11.glDisable(GL11.GL_TEXTURE_2D);
	}

	public static void enableBlend()
	{
		GL11.glEnable(GL11.GL_BLEND);
	}

	public static void disableBlend()
	{
		GL11.glDisable(GL11.GL_BLEND);
	}

	public static void blendFunc(int src, int dst)
	{
		GL11.glBlendFunc(src, dst);
	}

	public static void tryBlendFuncSeparate(int src, int dst, int srcAlpha, int dstAlpha)
	{
		GL11.glBlendFunc(src, dst);
	}

	public static void enableLighting()
	{
		GL11.glEnable(GL11.GL_LIGHTING);
	}

	public static void disableLighting()
	{
		GL11.glDisable(GL11.GL_LIGHTING);
	}

	public static void enableDepth()
	{
		GL11.glEnable(GL11.GL_DEPTH_TEST);
	}

	public static void disableDepth()
	{
		GL11.glDisable(GL11.GL_DEPTH_TEST);
	}

	public static void enableCull()
	{
		GL11.glEnable(GL11.GL_CULL_FACE);
	}

	public static void disableCull()
	{
		GL11.glDisable(GL11.GL_CULL_FACE);
	}

	public static void enableAlpha()
	{
		GL11.glEnable(GL11.GL_ALPHA_TEST);
	}

	public static void disableAlpha()
	{
		GL11.glDisable(GL11.GL_ALPHA_TEST);
	}

	public static void enableRescaleNormal()
	{
		GL11.glEnable(GL_RESCALE_NORMAL);
	}

	public static void disableRescaleNormal()
	{
		GL11.glDisable(GL_RESCALE_NORMAL);
	}

	public static void enableColorMaterial()
	{
		GL11.glEnable(GL11.GL_COLOR_MATERIAL);
	}

	public static void disableColorMaterial()
	{
		GL11.glDisable(GL11.GL_COLOR_MATERIAL);
	}

	public static void enableNormalize()
	{
		GL11.glEnable(GL11.GL_NORMALIZE);
	}

	public static void disableNormalize()
	{
		GL11.glDisable(GL11.GL_NORMALIZE);
	}

	public static void enablePolygonOffset()
	{
		GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
	}

	public static void disablePolygonOffset()
	{
		GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
	}

	public static void doPolygonOffset(float factor, float units)
	{
		GL11.glPolygonOffset(factor, units);
	}

	public static void setActiveTexture(int texture)
	{
		GL13.glActiveTexture(texture);
	}

	/** 1.8 的 bindTexture(int)，1.7.10 直接转发。 */
	public static void bindTexture(int texture)
	{
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
	}

	public static void shadeModel(int mode)
	{
		GL11.glShadeModel(mode);
	}

	public static void clear(int mask)
	{
		GL11.glClear(mask);
	}

	public static void clearColor(float r, float g, float b, float a)
	{
		GL11.glClearColor(r, g, b, a);
	}

	public static void matrixMode(int mode)
	{
		GL11.glMatrixMode(mode);
	}

	public static void loadIdentity()
	{
		GL11.glLoadIdentity();
	}

	public static void enable(int cap)
	{
		GL11.glEnable(cap);
	}

	public static void disable(int cap)
	{
		GL11.glDisable(cap);
	}

	public static void glBlendFunc(int src, int dst)
	{
		GL11.glBlendFunc(src, dst);
	}
}
