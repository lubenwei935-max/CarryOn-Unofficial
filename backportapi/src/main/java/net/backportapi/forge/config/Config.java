package net.backportapi.forge.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import net.minecraftforge.common.config.Configuration;

/**
 * 1.8+ @Config 注解的 1.7.10 回填。
 *
 * <p>1.8 由 FML 在加载时扫描注解并自动生成/读取配置文件。
 * 1.7.10 只有命令式的 {@link Configuration}，所以这里提供注解 +
 * {@link ConfigManager#sync(String, Class)} 的等价实现。
 *
 * <pre>
 * public class Configs {
 *     public static class Settings {
 *         &#64;Comment("最大拾取距离")
 *         &#64;RangeDouble(min = 0)
 *         public double maxDistance = 2.5;
 *     }
 * }
 * // preInit 里：
 * ConfigManager.sync("carryon", Configs.class);
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Config
{
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.FIELD)
	@interface Comment
	{
		String[] value();
	}

	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.FIELD)
	@interface RangeInt
	{
		int min() default Integer.MIN_VALUE;

		int max() default Integer.MAX_VALUE;
	}

	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.FIELD)
	@interface RangeDouble
	{
		double min() default -Double.MAX_VALUE;

		double max() default Double.MAX_VALUE;
	}

	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.FIELD)
	@interface LangKey
	{
		String value();
	}
}
