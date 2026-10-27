/*
 * Pemeriksa sintaks Kotlin cepat — TANPA Android SDK, TANPA Gradle.
 *
 * Kenapa ada: `compileDebugKotlin` butuh JDK 21 + SDK Android + NDK + CMake
 * dan makan belasan menit. Padahal sebagian besar regresi yang dikirim kontributor
 * itu salah ketik / kurung tidak seimbang. Tool ini memakai parser PSI resmi
 * Kotlin (kotlin-compiler 2.2.21) untuk mem-parsing setiap file .kt dan
 * melaporkan PsiErrorElement. Ini PEMERIKSAAN SINTAKS, bukan type-check —
 * tetap jalankan build APK asli sebelum rilis.
 *
 * Dipakai oleh ci/kotlin_syntax_check.sh.
 */
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiRecursiveElementVisitor;
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles;
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment;
import org.jetbrains.kotlin.config.CommonConfigurationKeys;
import org.jetbrains.kotlin.config.CompilerConfiguration;
import org.jetbrains.kotlin.psi.KtFile;
import org.jetbrains.kotlin.psi.KtPsiFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class KotlinSyntaxCheck {
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("usage: KotlinSyntaxCheck <file.kt> [file.kt ...]");
            System.exit(2);
        }
        CompilerConfiguration cfg = new CompilerConfiguration();
        cfg.put(CommonConfigurationKeys.MODULE_NAME, "syntax-check");
        KotlinCoreEnvironment env = KotlinCoreEnvironment.createForProduction(
            Disposer.newDisposable(), cfg, EnvironmentConfigFiles.JVM_CONFIG_FILES);
        KtPsiFactory factory = new KtPsiFactory(env.getProject(), false);
        int bad = 0;
        for (String path : args) {
            String text = new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
            KtFile file = factory.createFile(Paths.get(path).getFileName().toString(), text);
            final List<String> errs = new ArrayList<>();
            file.accept(new PsiRecursiveElementVisitor() {
                @Override
                public void visitErrorElement(PsiErrorElement e) {
                    errs.add(String.valueOf(e.getErrorDescription())
                        + " @" + e.getTextRange().getStartOffset());
                }
            });
            if (errs.isEmpty()) {
                System.out.println("OK   " + path);
            } else {
                bad++;
                System.out.println("FAIL " + path);
                int n = 0;
                for (String s : errs) {
                    System.out.println("     " + s);
                    if (++n >= 8) break;
                }
            }
        }
        System.out.println("TOTAL=" + args.length + " FAIL=" + bad);
        System.exit(bad == 0 ? 0 : 1);
    }
}
