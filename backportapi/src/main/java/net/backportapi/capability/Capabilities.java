package net.backportapi.capability;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityDispatcher;
import net.minecraftforge.common.capabilities.CapabilityInject;
import net.minecraftforge.common.capabilities.CapabilityManager;

/**
 * Capability 访问门面。
 *
 * <p>1.8 里写 {@code player.getCapability(CAP, null)}；
 * 1.7.10 里改写成 {@code Capabilities.get(player, CAP)}，其余逻辑不变。
 */
public final class Capabilities
{
	/** TileEntity 没有创建事件，只能显式挂载；用弱引用表避免泄漏。 */
	private static final Map<TileEntity, CapabilityDispatcher> TILE_CAPS = new WeakHashMap<TileEntity, CapabilityDispatcher>();

	private Capabilities()
	{
	}

	// ---------- 实体 ----------

	public static CapabilityDispatcher dispatcher(Entity entity)
	{
		CapabilityStorage storage = CapabilityStorage.get(entity);
		return storage == null ? null : storage.getDispatcher();
	}

	public static <T> boolean has(Entity entity, Capability<T> capability)
	{
		CapabilityDispatcher dispatcher = dispatcher(entity);
		return dispatcher != null && dispatcher.hasCapability(capability, null);
	}

	public static <T> T get(Entity entity, Capability<T> capability)
	{
		CapabilityDispatcher dispatcher = dispatcher(entity);
		return dispatcher == null ? null : dispatcher.getCapability(capability, null);
	}

	// ---------- TileEntity ----------

	/**
	 * 给 TileEntity 挂能力。必须在 TE 构造后、且在其 writeToNBT 之前调用；
	 * 并在 TE 的 writeToNBT/readFromNBT 里分别调用
	 * {@link #saveTileCaps}/{@link #loadTileCaps}。
	 */
	public static CapabilityDispatcher attachTo(TileEntity tile)
	{
		CapabilityDispatcher existing = TILE_CAPS.get(tile);
		if (existing != null)
			return existing;

		CapabilityDispatcher dispatcher = new CapabilityDispatcher();
		TILE_CAPS.put(tile, dispatcher);
		return dispatcher;
	}

	public static CapabilityDispatcher tileDispatcher(TileEntity tile)
	{
		return TILE_CAPS.get(tile);
	}

	public static void saveTileCaps(TileEntity tile, net.minecraft.nbt.NBTTagCompound tag)
	{
		CapabilityDispatcher dispatcher = TILE_CAPS.get(tile);
		if (dispatcher != null)
			tag.setTag("BackportCapabilities", dispatcher.serializeNBT());
	}

	public static void loadTileCaps(TileEntity tile, net.minecraft.nbt.NBTTagCompound tag)
	{
		CapabilityDispatcher dispatcher = TILE_CAPS.get(tile);
		if (dispatcher != null && tag.hasKey("BackportCapabilities"))
		{
			net.minecraft.nbt.NBTBase data = tag.getTag("BackportCapabilities");
			if (data instanceof net.minecraft.nbt.NBTTagCompound)
				dispatcher.deserializeNBT((net.minecraft.nbt.NBTTagCompound) data);
		}
	}

	// ---------- @CapabilityInject 填充 ----------

	/**
	 * 手工版"注入"：扫描 holder 类中所有 {@code @CapabilityInject} 静态字段，
	 * 按字段泛型参数（{@code Capability<IPosition>} 的 IPosition）查找已注册能力并赋值。
	 *
	 * <p>1.8 由 FML 在类加载时自动完成；1.7.10 需要在 preInit 显式调用一次。
	 */
	public static void inject(Class<?> holder)
	{
		if (holder == null)
			return;

		for (Field field : holder.getDeclaredFields())
		{
			if (!field.isAnnotationPresent(CapabilityInject.class))
				continue;
			if (!Modifier.isStatic(field.getModifiers()))
				continue;

			Class<?> capabilityType = resolveCapabilityType(field);
			if (capabilityType == null)
				continue;

			Capability<?> capability = CapabilityManager.INSTANCE.get(capabilityType);
			if (capability == null)
				continue;

			try
			{
				field.setAccessible(true);
				field.set(null, capability);
			}
			catch (Exception e)
			{
				throw new IllegalStateException("Failed to inject capability into " + holder.getName() + "." + field.getName(), e);
			}
		}
	}

	private static Class<?> resolveCapabilityType(Field field)
	{
		Type generic = field.getGenericType();
		if (generic instanceof ParameterizedType)
		{
			Type[] args = ((ParameterizedType) generic).getActualTypeArguments();
			if (args.length == 1 && args[0] instanceof Class)
				return (Class<?>) args[0];
		}
		return null;
	}
}
