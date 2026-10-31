package tschipp.carryon.client.event;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.backportapi.client.RenderTarget;
import net.backportapi.client.RenderTargets;
import net.backportapi.hand.Hands;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.MathHelper;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import tschipp.carryon.CarryOn;
import tschipp.carryon.client.keybinds.CarryOnKeybinds;
import tschipp.carryon.common.config.Configs;
import tschipp.carryon.common.handler.ModelOverridesHandler;
import tschipp.carryon.common.handler.RegistrationHandler;
import tschipp.carryon.common.item.ItemEntity;
import tschipp.carryon.common.item.ItemTile;
import tschipp.carryon.network.server.SyncKeybindPacket;

/**
 * 搬运物的渲染。
 *
 * <h3>第三人称：挂在玩家模型自己的坐标里，不要自己算相机</h3>
 * 之前试过在 {@code RenderWorldLastEvent} 里"重建视图矩阵"，两次都错，原因是
 * 那个矩阵的原点<b>不是脚底而是眼睛</b>：原版 {@code EntityPlayer.yOffset = 1.62}，
 * {@code RendererLivingEntity} 渲染模型时先 {@code translate(0, -yOffset, 0)}，
 * 所以"玩家位置 - 相机位置"这组数落到矩阵里其实在眼睛高度；
 * 而且第三人称相机还被 {@code orientCamera} 向后拉了 4 格。
 * 自己重建就得同时猜对这两件事，环境（Angelica）一改矩阵状态就全错。
 *
 * <p>正解是<b>不再自己算</b>：改用 {@code RenderPlayerEvent.Post}，它在原版画完玩家模型
 * 之后触发，此时矩阵已 pop 回原状，而事件携带的 {@code x/y/z} 正是原版用来定位玩家模型的
 * 那一组坐标。直接 {@code translate(x, y - yOffset, z)} 就落在玩家脚底，
 * 之后的高度/前后/缩放都是相对玩家的世界单位，与相机在哪、朝哪无关。
 *
 * <h3>第一人称：先平移再缩放</h3>
 * OpenGL 是右乘：{@code glScalef(s); glTranslatef(t)} 的实际偏移是 {@code s*t}。
 * 之前把"世界单位"的平移量写在 scale 之后，结果被放大了 2.5 倍（箱子又远又小）。
 * 现在统一 {@code glTranslatef → glScalef}，平移量就是世界单位。
 *
 * <h3>数值来自上游 1.12.2</h3>
 * 方块第一人称 {@code scale(2.5)} + 等效世界偏移 {@code (0,-1.5,-2.5)}；
 * 方块第三人称 {@code scale(0.6)} + {@code (0, 0.96, ±0.39)}；
 * 生物第一人称 {@code scale(0.8)}；生物第三人称 {@code scale((10-h*w)*0.08)}。
 */
@SideOnly(Side.CLIENT)
public class RenderEvents
{
	/** 第一人称投影矩阵的复用缓冲区（16 个 float，堆外）。 */
	private static final java.nio.FloatBuffer PERSPECTIVE_BUFFER = java.nio.ByteBuffer.allocateDirect(64)
			.order(java.nio.ByteOrder.nativeOrder()).asFloatBuffer();

	// ---------- 第一人称 ----------

	@SubscribeEvent
	public void renderHand(RenderHandEvent event)
	{
		Minecraft mc = Minecraft.getMinecraft();
		EntityPlayer player = mc.thePlayer;

		if (player == null || mc.gameSettings.hideGUI || mc.gameSettings.thirdPersonView != 0)
			return;

		ItemStack stack = Hands.getMainhand(player);
		if (!isCarried(stack))
			return;

		try
		{
			// 原版画手时会把投影换成固定 70°（getFOVModifier(partial, false)），
			// 而本事件在 renderHand() <b>之前</b>触发，此刻还是<b>世界</b>的投影。
			// 玩家把 FOV 调到 110° 时，手里的东西就会比原版小一半——这就是之前
			// "箱子偏小"的真正原因。这里自己切一次投影，画完再还回去。
			GL11.glMatrixMode(GL11.GL_PROJECTION);
			GL11.glPushMatrix();
			GL11.glLoadIdentity();
			applyPerspective(70.0F, (float) mc.displayWidth / (float) mc.displayHeight, 0.05F, 512.0F);
			GL11.glMatrixMode(GL11.GL_MODELVIEW);

			GL11.glPushMatrix();

			// 归零：此后的一切都在"眼位为原点、-Z 为前方"的相机空间里
			GL11.glLoadIdentity();

			// 原版在 renderHand() 里会先清一次深度再画手；本事件在它<b>之前</b>触发，
			// 不清的话贴墙站时箱子会被墙挡住（手里拿着的东西不该被遮挡）。
			GL11.glDepthMask(true);
			GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);

			GL11.glEnable(GL12.GL_RESCALE_NORMAL);
			GL11.glDisable(GL11.GL_CULL_FACE);
			GL11.glEnable(GL11.GL_BLEND);
			OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);

			if (isBlock(stack))
				renderHandBlock(mc, player, stack, event.partialTicks);
			else
				renderHandEntity(player, stack, event.partialTicks);

			GL11.glDisable(GL11.GL_BLEND);
			GL11.glEnable(GL11.GL_CULL_FACE);
			GL11.glDisable(GL12.GL_RESCALE_NORMAL);
			GL11.glPopMatrix();

			// 还原投影（原版 renderHand 会自己再设一次，这里还回去只是为了不污染别人）
			GL11.glMatrixMode(GL11.GL_PROJECTION);
			GL11.glPopMatrix();
			GL11.glMatrixMode(GL11.GL_MODELVIEW);
		}
		catch (Throwable t)
		{
			t.printStackTrace();
		}
	}

	private void renderHandBlock(Minecraft mc, EntityPlayer player, ItemStack stack, float partial)
	{
		RenderHelper.enableStandardItemLighting();
		applyLighting(player);
		mc.getTextureManager().bindTexture(TextureMap.locationBlocksTexture);
		GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

		// 先平移、后缩放：这样 handOffsetY/Z 就是世界单位（1 = 1 格）
		GL11.glTranslatef(0.0F, (float) Configs.Settings.handOffsetY, (float) Configs.Settings.handOffsetZ);

		float scale = (float) Configs.Settings.handScale;
		GL11.glScalef(scale, scale, scale);

		if (isChestLike(stack))
		{
			// 1.7.10 的箱子模型（renderBlockAsItem 的 renderType 22 分支）锁扣朝 <b>+X</b>，
			// 1.8+ 的方块模型锁扣朝 -Z。
			//
			// 朝向：上游对箱子写的是 rotate(180,Y)，效果是让<b>锁扣背对玩家</b>
			// （截图里原版的正面是一片暗色、看不到锁扣）。按 +X 换算过来就是 +90°。
			// 之前写成 -90°，锁扣正好怼在镜头上，所以看起来"正面朝内"。
			//
			// 俯仰：上游是 Ry(180)·Rx(-8)，而 Ry(180) 会把 X 轴翻成 -X，
			// 所以它等效于相机系的 <b>+8°</b>。这里直接写 +8°，倾斜方向才和原版一致
			// （远边抬高、能多看到一点顶面）；写成 -8° 会反向。
			//
			// 顺序：GL 右乘，最后调用的最先作用于顶点。先写 pitch 后写 yaw
			// = 先偏航再俯仰，倾斜绕的是<b>屏幕水平轴</b>，不会变成翻滚。
			GL11.glRotatef(8.0F, 1.0F, 0.0F, 0.0F);
			GL11.glRotatef(90.0F, 0.0F, 1.0F, 0.0F);
		}
		else
		{
			GL11.glRotatef(8.0F, 1.0F, 0.0F, 0.0F);
		}

		drawBlock(stack, player);

		RenderHelper.disableStandardItemLighting();

		// 原版只有在<b>空手</b>时才会画第一人称手臂（ItemRenderer 的 else 分支），
		// 我们把手持物换成了自己的渲染，那条分支永远走不到，所以这里自己补一只手，
		// 变换照抄原版空手分支，位置就在右下角。
		renderFirstPersonArm(mc, player, partial);
	}

	/** 第一人称手臂：完全照搬原版 {@code ItemRenderer} 空手分支的变换。 */
	private void renderFirstPersonArm(Minecraft mc, EntityPlayer player, float partial)
	{
		Render render = RenderManager.instance.getEntityRenderObject(player);
		if (!(render instanceof net.minecraft.client.renderer.entity.RenderPlayer))
			return;

		net.minecraft.client.renderer.entity.RenderPlayer renderPlayer = (net.minecraft.client.renderer.entity.RenderPlayer) render;

		float swing = player.getSwingProgress(partial);
		float s1 = MathHelper.sin(swing * (float) Math.PI);
		float s2 = MathHelper.sin(MathHelper.sqrt_float(swing) * (float) Math.PI);
		float s3 = MathHelper.sin(swing * swing * (float) Math.PI);

		GL11.glPushMatrix();

		GL11.glTranslatef(-s2 * 0.3F, MathHelper.sin(MathHelper.sqrt_float(swing) * (float) Math.PI * 2.0F) * 0.4F, -s1 * 0.4F);
		GL11.glTranslatef(0.8F * 0.8F, -0.75F * 0.8F, -0.9F * 0.8F);
		GL11.glRotatef(45.0F, 0.0F, 1.0F, 0.0F);
		GL11.glRotatef(s2 * 70.0F, 0.0F, 1.0F, 0.0F);
		GL11.glRotatef(-s3 * 20.0F, 0.0F, 0.0F, 1.0F);

		if (player instanceof net.minecraft.client.entity.AbstractClientPlayer)
			mc.getTextureManager().bindTexture(((net.minecraft.client.entity.AbstractClientPlayer) player).getLocationSkin());

		GL11.glTranslatef(-1.0F, 3.6F, 3.5F);
		GL11.glRotatef(120.0F, 0.0F, 0.0F, 1.0F);
		GL11.glRotatef(200.0F, 1.0F, 0.0F, 0.0F);
		GL11.glRotatef(-135.0F, 0.0F, 1.0F, 0.0F);
		GL11.glTranslatef(5.6F, 0.0F, 0.0F);

		// 手臂被 RenderEntityEvents 在第三人称里隐藏过，这里确保是显示状态
		renderPlayer.modelBipedMain.bipedRightArm.showModel = true;
		renderPlayer.renderFirstPersonArm(player);

		GL11.glPopMatrix();
	}

	private void renderHandEntity(EntityPlayer player, ItemStack stack, float partial)
	{
		Entity entity = ItemEntity.getEntity(stack, player.worldObj);
		if (entity == null)
			return;

		RenderHelper.enableStandardItemLighting();

		// 上游：scale(0.8) → rotate(180,Y) → translate(0,-height-0.1,width+0.1)
		float height = entity.height;
		float width = entity.width;

		GL11.glScalef(0.8F, 0.8F, 0.8F);
		GL11.glRotatef(180.0F, 0.0F, 1.0F, 0.0F);
		GL11.glTranslatef(0.0F, -height - 0.1F, width + 0.1F);

		prepareEntity(entity, player);
		renderEntity(entity, partial);

		RenderHelper.disableStandardItemLighting();
	}


	/**
	 * 设置透视投影。
	 *
	 * <p><b>不要用 {@code glFrustum}</b>：MC 自己的 {@code Project.gluPerspective}
	 * 其实是手算矩阵 + {@code glMultMatrix}，从来没用过 {@code glFrustum}。
	 * 在 lwjgl3ify / Angelica 这套兼容层上 {@code glFrustum} 很可能被忽略，
	 * 结果就是手里的东西按<b>世界 FOV</b>（玩家设的 110°）渲染，
	 * 看起来比原版小一倍——这正是"第一视角箱子偏小"的真正原因。
	 *
	 * <p>矩阵缓冲区是静态复用的：这个方法每帧都会调用，
	 * 每次 {@code allocateDirect} 都要向驱动申请一块堆外内存，属于纯粹的浪费。
	 */
	private static void applyPerspective(float fovDegrees, float aspect, float near, float far)
	{
		float f = 1.0F / (float) Math.tan(Math.toRadians(fovDegrees) / 2.0D);

		java.nio.FloatBuffer m = PERSPECTIVE_BUFFER;
		m.clear();
		m.put(f / aspect).put(0.0F).put(0.0F).put(0.0F);
		m.put(0.0F).put(f).put(0.0F).put(0.0F);
		m.put(0.0F).put(0.0F).put((far + near) / (near - far)).put(-1.0F);
		m.put(0.0F).put(0.0F).put(2.0F * far * near / (near - far)).put(0.0F);
		m.flip();

		GL11.glMultMatrix(m);
	}

	// ---------- 第三人称 ----------

	/**
	 * 在玩家模型画完之后接着画——坐标直接沿用原版定位玩家模型的那一组，
	 * 因此不需要知道相机在哪、也不需要重建视图矩阵。
	 */
	@SubscribeEvent
	public void onRenderPlayerPost(RenderPlayerEvent.Post event)
	{
		Minecraft mc = Minecraft.getMinecraft();
		EntityPlayer player = event.entityPlayer;

		if (player == null || mc.gameSettings.hideGUI)
			return;

		// 第一人称由 renderHand 负责
		if (player == mc.thePlayer && mc.gameSettings.thirdPersonView == 0)
			return;

		ItemStack stack = Hands.getMainhand(player);
		if (!isCarried(stack))
			return;

		float partial = event.partialRenderTick;
		float yaw = player.prevRenderYawOffset + (player.renderYawOffset - player.prevRenderYawOffset) * partial;

		try
		{
			// 1.7.10 的 RenderPlayerEvent 不带坐标，自己算——用的就是原版
			// RenderManager.renderEntityStatic 那一套（玩家插值坐标 - renderPos），
			// 再减 yOffset（玩家 1.62）落到脚底。因为此刻矩阵和画玩家模型时是同一个，
			// 算出来的位置必然和模型对齐，与相机在哪无关。
			double x = player.lastTickPosX + (player.posX - player.lastTickPosX) * partial - RenderManager.renderPosX;
			double y = player.lastTickPosY + (player.posY - player.lastTickPosY) * partial - RenderManager.renderPosY;
			double z = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * partial - RenderManager.renderPosZ;

			GL11.glPushMatrix();
			GL11.glTranslated(x, y - player.yOffset, z);

			GL11.glEnable(GL12.GL_RESCALE_NORMAL);
			GL11.glDisable(GL11.GL_CULL_FACE);
			GL11.glEnable(GL11.GL_BLEND);
			OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);

			if (isBlock(stack))
				renderThirdPersonBlock(mc, player, stack, partial, yaw);
			else
				renderThirdPersonEntity(player, stack, partial, yaw);

			GL11.glDisable(GL11.GL_BLEND);
			GL11.glEnable(GL11.GL_CULL_FACE);
			GL11.glDisable(GL12.GL_RESCALE_NORMAL);
			GL11.glPopMatrix();
		}
		catch (Throwable t)
		{
			t.printStackTrace();
		}
	}

	private void renderThirdPersonBlock(Minecraft mc, EntityPlayer player, ItemStack stack, float partial, float yaw)
	{
		applyLighting(player);
		mc.getTextureManager().bindTexture(TextureMap.locationBlocksTexture);
		GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

		boolean chest = isChestLike(stack);
		boolean flip = Configs.Settings.facePlayer ? !chest : chest;

		// 朝向：让搬运物的"正面"朝外（箱子朝向玩家视线方向，其它方块转 180°）
		GL11.glRotatef(-yaw + (flip ? 0.0F : 180.0F), 0.0F, 1.0F, 0.0F);

		// 先平移、后缩放：偏移是世界单位
		float offY = (float) Configs.Settings.carryOffsetY;
		float offZ = (float) Configs.Settings.carryOffsetZ;
		GL11.glTranslatef(0.0F, offY, flip ? offZ : -offZ);

		if (player.isSneaking())
			GL11.glTranslatef(0.0F, -0.25F, 0.0F);

		// 箱子的模型正面在 1.7.10 里是 +X，需要单独补 -90°。
		// <b>必须放在平移之后</b>：否则这 -90° 会把"往前 0.4 格"也一起转到侧面去
		// （上一版箱子跑到左下角就是这个原因）。
		if (chest)
			GL11.glRotatef(-90.0F, 0.0F, 1.0F, 0.0F);

		float scale = (float) Configs.Settings.carryScale;
		GL11.glScalef(scale, scale, scale);

		drawBlock(stack, player);
	}

	private void renderThirdPersonEntity(EntityPlayer player, ItemStack stack, float partial, float yaw)
	{
		Entity entity = ItemEntity.getEntity(stack, player.worldObj);
		if (entity == null)
			return;

		float height = entity.height;
		float width = entity.width;

		// 上游：scale((10-h*w)*0.08) → rotate(rotation,Y) → translate(0,1,max(w-0.1,0.7))
		float scale = (10.0F - height * width) * 0.08F;
		float forward = Math.max(width - 0.1F, 0.7F);

		GL11.glRotatef(-yaw, 0.0F, 1.0F, 0.0F);
		GL11.glScalef(scale, scale, scale);
		GL11.glTranslatef(0.0F, 1.0F, forward);

		if (player.isSneaking())
			GL11.glTranslatef(0.0F, -0.3F, 0.0F);

		prepareEntity(entity, player);
		renderEntity(entity, partial);
	}

	// ---------- 共用 ----------

	private void drawBlock(ItemStack stack, EntityPlayer player)
	{
		RenderTarget target = ModelOverridesHandler.getRenderTarget(ItemTile.getBlockState(stack), ItemTile.getTileData(stack));

		if (target == null)
		{
			Block block = ItemTile.getBlock(stack);
			if (block == null)
				return;
			target = RenderTargets.ofBlock(block, ItemTile.getMeta(stack));
		}

		if (target != null)
			target.render(1.0F);
	}

	/** 让搬运中的生物面朝正前方、站在玩家身上（上游也是这么做的）。 */
	private void prepareEntity(Entity entity, EntityPlayer player)
	{
		entity.setPosition(player.posX, player.posY, player.posZ);
		entity.rotationYaw = 0.0F;
		entity.prevRotationYaw = 0.0F;
		entity.rotationPitch = 0.0F;
		entity.prevRotationPitch = 0.0F;

		if (entity instanceof net.minecraft.entity.EntityLivingBase)
		{
			net.minecraft.entity.EntityLivingBase living = (net.minecraft.entity.EntityLivingBase) entity;
			living.rotationYawHead = 0.0F;
			living.prevRotationYawHead = 0.0F;
			living.renderYawOffset = 0.0F;
			living.prevRenderYawOffset = 0.0F;
			living.limbSwing = 0.0F;
			living.limbSwingAmount = 0.0F;
			living.prevLimbSwingAmount = 0.0F;
		}
	}

	@SuppressWarnings("rawtypes")
	private void renderEntity(Entity entity, float partial)
	{
		Render render = RenderManager.instance.getEntityRenderObject(entity);
		if (render == null)
			return;

		render.doRender(entity, 0.0D, 0.0D, 0.0D, 0.0F, partial);
	}

	private void applyLighting(EntityPlayer player)
	{
		int i = player.worldObj.getLightBrightnessForSkyBlocks(MathHelper.floor_double(player.posX), MathHelper.floor_double(player.posY + player.getEyeHeight()), MathHelper.floor_double(player.posZ), 0);
		OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, i % 65536, i / 65536);
	}

	private boolean isCarried(ItemStack stack)
	{
		return isBlock(stack) || isEntity(stack);
	}

	private boolean isBlock(ItemStack stack)
	{
		return stack != null && stack.getItem() == RegistrationHandler.itemTile && ItemTile.hasTileData(stack);
	}

	private boolean isEntity(ItemStack stack)
	{
		return stack != null && stack.getItem() == RegistrationHandler.itemEntity && ItemEntity.hasEntityData(stack);
	}

	private boolean isChestLike(ItemStack stack)
	{
		Block block = ItemTile.getBlock(stack);
		return block == net.minecraft.init.Blocks.chest || block == net.minecraft.init.Blocks.ender_chest || block == net.minecraft.init.Blocks.trapped_chest;
	}

	// ---------- tick：按键同步 + 槽位锁定 ----------

	@SubscribeEvent
	public void onPlayerTick(TickEvent.PlayerTickEvent event)
	{
		if (event.side != Side.CLIENT || event.phase != TickEvent.Phase.END || event.player == null)
			return;

		EntityPlayer player = event.player;
		Minecraft mc = Minecraft.getMinecraft();

		// 1) Shift 键状态同步到服务端（没有这段，服务端永远判定为没按）
		if (player == mc.thePlayer)
		{
			boolean keyPressed = CarryOnKeybinds.carryKey != null && CarryOnKeybinds.carryKey.getIsKeyPressed();
			boolean playerKeyPressed = CarryOnKeybinds.isKeyPressed(player);

			if (keyPressed && !playerKeyPressed)
			{
				CarryOnKeybinds.setKeyPressed(player, true);
				CarryOn.network.sendToServer(new SyncKeybindPacket(true));
			}
			else if (!keyPressed && playerKeyPressed)
			{
				CarryOnKeybinds.setKeyPressed(player, false);
				CarryOn.network.sendToServer(new SyncKeybindPacket(false));
			}
		}

		// 2) 搬运中锁定快捷栏：与上游一致，强行把 currentItem 拉回 carrySlot
		NBTTagCompound data = player.getEntityData();
		if (data.hasKey("carrySlot"))
		{
			int slot = data.getInteger("carrySlot");
			if (slot >= 0 && slot < 9 && player.inventory.currentItem != slot)
				player.inventory.currentItem = slot;
		}
	}
}
