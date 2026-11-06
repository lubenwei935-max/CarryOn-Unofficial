import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.CodeSource;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;

/**
 * 最小复现：把「无签名的类」定义进「已有签名类的包」。
 *
 * <p>用法：
 * <pre>
 *   java -cp build/repro Repro build                  # 先加载 pkg.Signed，再加载 pkg.Unsigned
 *   java -cp build/repro Repro build pkg.Unsigned     # 反序，先加载无签名类
 * </pre>
 *
 * <p>退出码：0 = 复现成功（抛出了预期的 SecurityException）；1 = 没有复现。
 * 这样脚本里可以直接判 `if Repro; then ...`。
 */
public final class Repro
{
    public static void main(String[] args) throws Exception
    {
        File dir = new File(args.length > 0 ? args[0] : ".");
        String first = args.length > 1 ? args[1] : "pkg.Signed";
        String second = "pkg.Signed".equals(first) ? "pkg.Unsigned" : "pkg.Signed";

        URL signedJar = new File(dir, "signed.jar").toURI().toURL();
        URL unsignedJar = new File(dir, "unsigned.jar").toURI().toURL();

        System.out.println("java.version      = " + System.getProperty("java.version"));
        System.out.println("java.home         = " + System.getProperty("java.home"));
        System.out.println("signed.jar        = " + signedJar);
        System.out.println("unsigned.jar      = " + unsignedJar);
        System.out.println("load order        = " + first + " -> " + second);
        System.out.println();

        // parent = null：父加载器里没有 pkg.*，保证两个类都从这两个 jar 里出。
        URLClassLoader cl = new URLClassLoader(new URL[]{signedJar, unsignedJar}, null);
        try
        {
            Class<?> c1 = Class.forName(first, true, cl);
            System.out.println("[1] loaded  " + c1.getName() + "   signers=" + signers(c1));
            System.out.println("    codeSource=" + codeSource(c1));
            System.out.println();

            Class<?> c2 = Class.forName(second, true, cl);
            System.out.println("[2] loaded  " + c2.getName() + "   signers=" + signers(c2));
            System.out.println("    codeSource=" + codeSource(c2));
            System.out.println();
            System.out.println("REPRO-FAIL: 第二次 defineClass 没有抛异常，签名冲突未能复现");
            System.exit(1);
        }
        catch (Throwable t)
        {
            System.out.println("[2] " + t.getClass().getName() + ": " + t.getMessage());
            System.out.println();
            t.printStackTrace(System.out);

            boolean expected = t instanceof SecurityException
                    && t.getMessage() != null
                    && t.getMessage().contains("signer information does not match");
            System.out.println();
            System.out.println(expected ? "REPRO-OK" : "REPRO-FAIL: 异常类型/文本与崩溃日志不一致");
            System.exit(expected ? 0 : 1);
        }
    }

    private static String signers(Class<?> c)
    {
        try
        {
            // JDK 8 的签名是 Object[]（JDK 9+ 才是 Certificate[]），两边都要能编，故用 Object[]。
            Object[] certs = c.getSigners();
            if (certs == null || certs.length == 0)
            {
                return "<none>";
            }
            StringBuilder sb = new StringBuilder("[").append(certs.length).append("] ");
            for (Object item : certs)
            {
                if (!(item instanceof Certificate))
                {
                    sb.append(item).append(' ');
                    continue;
                }
                Certificate cert = (Certificate) item;
                sb.append(cert.getType()).append(':');
                if (cert instanceof X509Certificate)
                {
                    sb.append(((X509Certificate) cert).getSubjectDN().getName());
                }
                else
                {
                    sb.append(cert.toString());
                }
                sb.append(' ');
            }
            return sb.toString().trim();
        }
        catch (Exception e)
        {
            return "<error: " + e + ">";
        }
    }

    private static String codeSource(Class<?> c)
    {
        CodeSource cs = c.getProtectionDomain().getCodeSource();
        return cs == null ? "<null>" : String.valueOf(cs.getLocation());
    }
}
