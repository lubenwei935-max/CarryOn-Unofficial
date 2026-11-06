package pkg;

/**
 * 模拟「BackportAPI 塞进同一个包里的类」。
 *
 * <p>真实场景对应：{@code net.minecraftforge.common.config.ConfigManager}、
 * {@code net.minecraft.util.EnumHand} —— 同一个包名，但所在的 jar 没有签名。
 */
public final class Unsigned
{
    private Unsigned()
    {
    }

    public static String tag()
    {
        return "unsigned";
    }
}
