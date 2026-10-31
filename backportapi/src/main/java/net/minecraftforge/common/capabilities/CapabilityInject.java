package net.minecraftforge.common.capabilities;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 1.8+ @CapabilityInject 的 1.7.10 回填。
 *
 * <p><b>与 1.8 的差异：</b>1.8 由 FML 在类加载时自动注入静态字段。
 * 1.7.10 没有这套 ASM 注入，因此需要显式调用
 * {@code Capabilities.inject(YourClass.class)}（在你的 preInit 里），
 * 由反射把字段填上。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface CapabilityInject
{
}
