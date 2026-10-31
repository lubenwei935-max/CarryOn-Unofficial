package tschipp.carryon.common.handler;

import java.util.ArrayList;
import java.util.List;

import net.backportapi.client.RenderTarget;
import net.backportapi.client.RenderTargets;
import net.backportapi.util.NbtFilter;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import tschipp.carryon.CarryOn;
import tschipp.carryon.common.config.Configs;
import tschipp.carryon.common.helper.StringParser;

/**
 * 模型覆盖：让"搬在手里的 A"渲染成"B"。
 *
 * <h3>为什么需要</h3>
 * 有些方块搬起来之后用自身模型渲染会很难看或干脆不对：
 * <ul>
 *   <li>{@code lit_furnace} 没有物品形态，图标是空的</li>
 *   <li>{@code tallgrass} / {@code leaves2} 作为方块是十字/复杂形状，抱在怀里很怪</li>
 *   <li>{@code hopper} / {@code cauldron} 的物品形态是扁平图标，不是 3D 方块</li>
 * </ul>
 *
 * <h3>配置格式</h3>
 * <pre>
 * 原方块[;meta][{nbt}] -&gt; [(block)|(item)]目标[;meta][{nbt}]
 *
 * minecraft:lit_furnace-&gt;minecraft:furnace
 * minecraft:tallgrass;1-&gt;(item)minecraft:tallgrass;1
 * quark:custom_chest{type:"spruce"}-&gt;quark:custom_chest;0
 * </pre>
 *
 * <p>与上游一致的两处约定：
 * <ul>
 *   <li>左侧 meta 为 0（或不写）表示<b>匹配该方块的所有 meta</b>；</li>
 *   <li>不带 {@code (block)} / {@code (item)} 前缀时默认按 {@code item} 处理。</li>
 * </ul>
 *
 * <h3>1.7.10 的实现差异</h3>
 * 上游存的是 {@code IBlockState} / {@code ItemStack}，渲染时再转成 {@code IBakedModel}。
 * 1.7.10 没有 baked model，所以这里直接存成
 * {@link RenderTarget}（BackportAPI 提供的"可替换绘制目标"抽象）。
 */
public class ModelOverridesHandler
{
	private static final List<Entry> OVERRIDES = new ArrayList<Entry>();

	private static final class Entry
	{
		final String blockName;
		final int meta; // <= 0 表示任意 meta
		final NBTTagCompound filter;
		final RenderTarget target;

		Entry(String blockName, int meta, NBTTagCompound filter, RenderTarget target)
		{
			this.blockName = blockName;
			this.meta = meta;
			this.filter = filter;
			this.target = target;
		}
	}

	public static void initOverrides()
	{
		OVERRIDES.clear();

		String[] lines = Configs.ModelOverrides.modelOverrides;
		if (lines == null)
			return;

		for (int i = 0; i < lines.length; i++)
		{
			try
			{
				parse(lines[i], i);
			}
			catch (Exception e)
			{
				CarryOn.LOGGER.warn("Failed to parse model override at line " + i + ": " + lines[i] + " (" + e.getMessage() + ")");
			}
		}

		CarryOn.LOGGER.info("Loaded " + OVERRIDES.size() + " model override(s)");
	}

	private static void parse(String line, int index)
	{
		if (line == null)
			return;

		line = line.trim();
		if (line.isEmpty() || !line.contains("->"))
			return;

		int arrow = line.indexOf("->");
		String from = line.substring(0, arrow).trim();
		String to = line.substring(arrow + 2).trim();

		// (block) / (item) 前缀
		String type = "item";
		int lp = to.indexOf('(');
		int rp = to.indexOf(')');
		if (lp >= 0 && rp > lp)
		{
			type = to.substring(lp + 1, rp).trim();
			to = (to.substring(0, lp) + to.substring(rp + 1)).trim();
		}

		Block fromBlock = StringParser.getBlock(from);
		if (fromBlock == null)
		{
			CarryOn.LOGGER.warn("Model override #" + index + ": unknown block '" + from + "'");
			return;
		}

		int fromMeta = StringParser.getMeta(from);
		NBTTagCompound filter = StringParser.getTagCompound(from);

		RenderTarget target;
		if ("block".equals(type))
		{
			IBlockState state = StringParser.getBlockState(to);
			target = state == null ? null : RenderTargets.ofState(state);
		}
		else
		{
			ItemStack stack = StringParser.getItemStack(to);
			target = stack == null ? null : RenderTargets.ofItem(stack, StringParser.getMeta(to));
		}

		if (target == null)
		{
			CarryOn.LOGGER.warn("Model override #" + index + ": cannot resolve target '" + to + "'");
			return;
		}

		String name = Block.blockRegistry.getNameForObject(fromBlock);
		OVERRIDES.add(new Entry(name, fromMeta, filter, target));
	}

	/** 取得该方块状态应使用的绘制目标；无匹配时返回 null（调用方回退到默认）。 */
	public static RenderTarget getRenderTarget(IBlockState state, NBTTagCompound nbt)
	{
		if (state == null || OVERRIDES.isEmpty())
			return null;

		String name = Block.blockRegistry.getNameForObject(state.getBlock());
		int meta = state.getMeta();

		for (Entry e : OVERRIDES)
		{
			if (!e.blockName.equals(name))
				continue;

			// meta <= 0 视为通配（与上游一致）
			if (e.meta > 0 && e.meta != meta)
				continue;

			if (!NbtFilter.matches(nbt, e.filter))
				continue;

			return e.target;
		}

		return null;
	}
}
