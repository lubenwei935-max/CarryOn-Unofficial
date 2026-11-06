package pkg;

/**
 * 模拟「被签名的 jar 里的类」。
 *
 * <p>真实场景对应：{@code net.minecraftforge.common.config.Configuration}（Forge 签）、
 * {@code net.minecraft.util.EnumFacing}（Mojang 签）。它们的包已经被 JVM 记为「有签名」。
 */
public final class Signed
{
    private Signed()
    {
    }

    public static String tag()
    {
        return "signed";
    }
}
