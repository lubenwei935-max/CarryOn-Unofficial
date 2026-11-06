package net.backportapi.forge.config;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

/**
 * 1.8+ ConfigManager 的 1.7.10 回填。
 *
 * <p>用反射把 {@code @Config} 标注的静态字段与 {@link Configuration} 双向同步。
 * 约定与 1.8 一致：<b>嵌套静态类的类名即分类名</b>，字段名即键名。
 */
public final class ConfigManager
{
	private static final Map<String, Configuration> CONFIGS = new HashMap<String, Configuration>();
	private static File configDirectory;

	private ConfigManager()
	{
	}

	public static void setConfigDirectory(File directory)
	{
		configDirectory = directory;
	}

	public static File getConfigDirectory()
	{
		return configDirectory;
	}

	public static Configuration getConfig(String modid)
	{
		Configuration config = CONFIGS.get(modid);
		if (config == null)
		{
			File file = new File(configDirectory == null ? new File("config") : configDirectory, modid + ".cfg");
			config = new Configuration(file);
			CONFIGS.put(modid, config);
		}
		return config;
	}

	/**
	 * 读取（不存在则写入默认值）并回填到字段。
	 */
	public static void sync(String modid, Class<?> configClass)
	{
		Configuration config = getConfig(modid);
		config.load();

		for (Class<?> holder : collectClasses(configClass))
		{
			String category = holder == configClass ? "general" : holder.getSimpleName();

			for (Field field : holder.getDeclaredFields())
			{
				// 1.8 的写法是字段上直接标 @Config；但实际代码里几乎没人写这个裸注解，
				// 只写 @Config.Comment / @Config.RangeInt / @Config.RangeDouble。
				// 只认 @Config 会让所有配置项<b>静默失效</b>（读不到、也写不进文件），
				// 所以这里放宽：带了任意一个子注解就算配置项。
				if (!isConfigField(field))
					continue;
				if (!Modifier.isStatic(field.getModifiers()))
					continue;

				try
				{
					field.setAccessible(true);
					syncField(config, category, field);
				}
				catch (Exception e)
				{
					throw new IllegalStateException("Failed to sync config field " + holder.getName() + "." + field.getName(), e);
				}
			}
		}

		// 无条件保存：Forge 1.7.10 的 Configuration.get() 在<b>新增</b>属性时并不会把
		// hasChanged 置真（只有 setValue 才会），所以"只有变化才保存"会导致
		// 新加的配置项永远写不进文件——用户改不动、重启后也没了。
		try
		{
			config.save();
		}
		catch (Throwable t)
		{
			System.err.println("[BackportAPI] Failed to save config '" + modid + "': " + t);
		}
	}

	private static boolean isConfigField(Field field)
	{
		return field.isAnnotationPresent(Config.class)
				|| field.isAnnotationPresent(Config.Comment.class)
				|| field.isAnnotationPresent(Config.RangeInt.class)
				|| field.isAnnotationPresent(Config.RangeDouble.class)
				|| field.isAnnotationPresent(Config.LangKey.class);
	}

	private static List<Class<?>> collectClasses(Class<?> root)
	{
		List<Class<?>> list = new ArrayList<Class<?>>();
		list.add(root);
		for (Class<?> nested : root.getDeclaredClasses())
		{
			if (Modifier.isStatic(nested.getModifiers()) && Modifier.isPublic(nested.getModifiers()))
				list.add(nested);
		}
		return list;
	}

	private static void syncField(Configuration config, String category, Field field) throws IllegalAccessException
	{
		Class<?> type = field.getType();
		String key = field.getName();
		String comment = buildComment(field);

		if (type == boolean.class)
		{
			boolean value = config.getBoolean(key, category, field.getBoolean(null), comment);
			field.setBoolean(null, value);
		}
		else if (type == int.class)
		{
			int min = Integer.MIN_VALUE;
			int max = Integer.MAX_VALUE;
			Config.RangeInt range = field.getAnnotation(Config.RangeInt.class);
			if (range != null)
			{
				min = range.min();
				max = range.max();
			}
			int def = field.getInt(null);
			Property prop = config.get(category, key, String.valueOf(def));
			prop.comment = comment;
			int value = clamp(parseInt(prop.getString(), def), min, max);
			field.setInt(null, value);
			prop.set(String.valueOf(value));
		}
		else if (type == double.class)
		{
			double min = -Double.MAX_VALUE;
			double max = Double.MAX_VALUE;
			Config.RangeDouble range = field.getAnnotation(Config.RangeDouble.class);
			if (range != null)
			{
				min = range.min();
				max = range.max();
			}
			double def = field.getDouble(null);
			Property prop = config.get(category, key, String.valueOf(def));
			prop.comment = comment;
			double value = clamp(parseDouble(prop.getString(), def), min, max);
			field.setDouble(null, value);
			prop.set(String.valueOf(value));
		}
		else if (type == float.class)
		{
			float def = field.getFloat(null);
			Property prop = config.get(category, key, String.valueOf(def));
			prop.comment = comment;
			float value = (float) parseDouble(prop.getString(), def);
			field.setFloat(null, value);
			prop.set(String.valueOf(value));
		}
		else if (type == String.class)
		{
			String def = (String) field.get(null);
			String value = config.getString(key, category, def, comment);
			field.set(null, value);
		}
		else if (type == String[].class)
		{
			String[] def = (String[]) field.get(null);
			String[] value = config.getStringList(key, category, def, comment);
			field.set(null, value);
		}
		else
		{
			throw new IllegalStateException("Unsupported @Config type: " + type.getName());
		}
	}

	private static String buildComment(Field field)
	{
		Config.Comment comment = field.getAnnotation(Config.Comment.class);
		if (comment == null)
			return null;

		StringBuilder sb = new StringBuilder();
		String[] lines = comment.value();
		for (int i = 0; i < lines.length; i++)
		{
			if (i > 0)
				sb.append('\n');
			sb.append(lines[i]);
		}
		return sb.toString();
	}

	private static int parseInt(String raw, int fallback)
	{
		try
		{
			return Integer.parseInt(raw.trim());
		}
		catch (Exception e)
		{
			return fallback;
		}
	}

	private static double parseDouble(String raw, double fallback)
	{
		try
		{
			return Double.parseDouble(raw.trim());
		}
		catch (Exception e)
		{
			return fallback;
		}
	}

	private static int clamp(int value, int min, int max)
	{
		return value < min ? min : (value > max ? max : value);
	}

	private static double clamp(double value, double min, double max)
	{
		return value < min ? min : (value > max ? max : value);
	}
}
