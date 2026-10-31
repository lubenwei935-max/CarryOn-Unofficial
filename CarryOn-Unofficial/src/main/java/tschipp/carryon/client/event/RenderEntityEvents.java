package tschipp.carryon.client.event;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.backportapi.hand.Hands;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.RenderPlayerEvent;
import tschipp.carryon.common.config.Configs;
import tschipp.carryon.common.handler.RegistrationHandler;
import tschipp.carryon.common.item.ItemEntity;
import tschipp.carryon.common.item.ItemTile;

/**
 * 搬运时的手臂姿态。
 *
 * <h3>为什么不能改真手臂的角度</h3>
 * 1.7.10 的 {@code ModelBase.render} 每帧都会调用 {@code ModelBiped.setRotationAngles}，
 * 它会把 {@code bipedLeftArm/RightArm} 的角度全部重写。
 * 所以在 {@code RenderPlayerEvent.Pre} 里设 {@code rotateAngleX} 是无效的。
 *
 * <h3>做法：隐藏真手臂 + 把假手臂挂到 bipedBody</h3>
 * 与上游一致。假手臂作为 {@code bipedBody} 的子节点参与模型渲染，
 * 因此不需要额外 GL 变换，也会自动跟随身体旋转、使用同一张皮肤贴图。
 *
 * <p>角度：方块 {@code 0.85}，生物 {@code 1.05}（弧度，正数 = 往前抬）。
 * 盒子坐标是写死的绝对坐标——1.7.10 的 {@code ModelRenderer.rotationPoint}
 * 是旋转轴（不是 1.8 那种额外偏移），不能照搬上游的表达式。
 */
@SideOnly(Side.CLIENT)
public class RenderEntityEvents
{
	private ModelRenderer fakeLeftArm;
	private ModelRenderer fakeRightArm;
	private boolean initialised;

	@SubscribeEvent
	public void onPlayerRenderPre(RenderPlayerEvent.Pre event)
	{
		if (!Configs.Settings.renderArms)
			return;

		try
		{
			EntityPlayer player = event.entityPlayer;
			ItemStack stack = Hands.getMainhand(player);

			if (!isCarried(stack))
				return;

			ModelBiped model = event.renderer.modelBipedMain;
			if (model == null || model.bipedBody == null)
				return;

			if (!this.initialised)
			{
				// 纹理坐标对应 1.7.10 玩家皮肤布局：右臂 (40,16)，左臂 (32,48)
				this.fakeRightArm = new ModelRenderer(model, 40, 16);
				this.fakeLeftArm = new ModelRenderer(model, 32, 48);

				// 旋转点保持 (0,0,0)：子节点空间的原点<b>就是肩膀</b>
				// （盒子 y 0..12 渲染在父空间 y 0..12 = 世界 1.5~0.75）。
				// 注意子空间 +Y 是世界<b>向下</b>，所以千万别写负值——写 -12 会把整条手臂
				// 抬到头顶上去（上一版就是这个现象）。
				this.fakeLeftArm.setRotationPoint(0.0F, 0.0F, 0.0F);
				this.fakeRightArm.setRotationPoint(0.0F, 0.0F, 0.0F);

				// 盒子放在身体两侧、略外扩，并带一点膨胀，像把东西搂在怀里
				this.fakeLeftArm.addBox(4.2F, 0.0F, -2.0F, 4, 12, 4, 0.08F);
				this.fakeRightArm.addBox(-7.9F, 0.0F, -2.0F, 4, 12, 4, 0.08F);

				this.initialised = true;
			}

			// 手臂从肩膀向下伸（子空间 +Y = 世界向下），绕 X 轴负角度才会把手推向 -Z
			// （模型 -Z 是正前方）。-1.0 rad ≈ 57°，手落在 y≈1.09、身前 0.63 格，
			// 正好托在箱子两侧偏上。
			boolean block = stack.getItem() == RegistrationHandler.itemTile;
			float rotX = block ? -1.0F : -1.25F;

			float rotY = block ? 0.0F : 0.15F;

			this.fakeLeftArm.rotateAngleX = rotX;
			this.fakeRightArm.rotateAngleX = rotX;
			this.fakeLeftArm.rotateAngleY = rotY;
			this.fakeRightArm.rotateAngleY = -rotY;
			this.fakeLeftArm.rotateAngleZ = 0.0F;
			this.fakeRightArm.rotateAngleZ = 0.0F;

			model.bipedLeftArm.showModel = false;
			model.bipedRightArm.showModel = false;

			model.bipedBody.addChild(this.fakeLeftArm);
			model.bipedBody.addChild(this.fakeRightArm);
		}
		catch (Throwable t)
		{
			t.printStackTrace();
		}
	}

	@SubscribeEvent
	public void onPlayerRenderPost(RenderPlayerEvent.Post event)
	{
		try
		{
			ModelBiped model = event.renderer.modelBipedMain;
			if (model == null || model.bipedBody == null)
				return;

			if (model.bipedBody.childModels != null)
			{
				model.bipedBody.childModels.remove(this.fakeLeftArm);
				model.bipedBody.childModels.remove(this.fakeRightArm);
			}

			model.bipedLeftArm.showModel = true;
			model.bipedRightArm.showModel = true;
		}
		catch (Throwable t)
		{
			t.printStackTrace();
		}
	}

	private boolean isCarried(ItemStack stack)
	{
		if (stack == null)
			return false;

		if (stack.getItem() == RegistrationHandler.itemTile)
			return ItemTile.hasTileData(stack);

		if (stack.getItem() == RegistrationHandler.itemEntity)
			return ItemEntity.hasEntityData(stack);

		return false;
	}
}
