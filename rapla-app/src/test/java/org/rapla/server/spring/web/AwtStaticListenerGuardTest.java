package org.rapla.server.spring.web;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * PRD 052 Phase 2 — guards against introducing new AWT/Swing static-singleton
 * listener registrations. The close+recreate context model assumes every
 * per-session listener dies with the context; AWT/Swing JVM-statics survive
 * the JVM lifetime, so registrations on them accumulate per logout iteration
 * unless explicitly cleaned up.
 *
 * <p>The original {@code EventQueue.push(...)} that motivated the PRD was
 * extracted to per-site {@link org.rapla.client.swing.SwingSafe#invokeLater}
 * wrappers (Phase 1a). After that change there are <b>zero</b> AWT-static
 * registrations in the client tier. This test fails if any of the patterns
 * below appear outside an explicit allow-list.
 *
 * <p>To legitimately introduce one: document the rationale in PRD 052 (how
 * does it get cleaned up across {@code ctx.close()}?) and add the FQCN to
 * {@link #ALLOWLIST}.
 */
class AwtStaticListenerGuardTest
{
    /** Empty by design — every entry needs PRD 052 sign-off. */
    private static final Set<String> ALLOWLIST = Set.of();

    private static final List<Pattern> BANNED = List.of(
            Pattern.compile("Toolkit\\s*\\.\\s*getDefaultToolkit\\s*\\(\\s*\\)\\s*\\.\\s*getSystemEventQueue\\s*\\(\\s*\\)\\s*\\.\\s*push"),
            Pattern.compile("UIManager\\s*\\.\\s*addPropertyChangeListener"),
            Pattern.compile("KeyboardFocusManager\\s*\\.\\s*getCurrentKeyboardFocusManager\\s*\\(\\s*\\)\\s*\\.\\s*addPropertyChangeListener"),
            Pattern.compile("KeyboardFocusManager\\s*\\.\\s*setCurrentKeyboardFocusManager"),
            Pattern.compile("Toolkit\\s*\\.\\s*getDefaultToolkit\\s*\\(\\s*\\)\\s*\\.\\s*addAWTEventListener")
    );

    @Test
    void noAwtStaticListenerRegistrationsInClientTier() throws IOException
    {
        Path root = Path.of(System.getProperty("user.dir")).getParent().resolve("rapla-client/src/main/java");
        assertTrue(Files.isDirectory(root), "rapla-client source tree not found at " + root);

        List<String> offenders = new ArrayList<>();
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException
            {
                if (!file.toString().endsWith(".java")) return FileVisitResult.CONTINUE;
                String fqcn = relativeFqcn(root, file);
                if (ALLOWLIST.contains(fqcn)) return FileVisitResult.CONTINUE;

                String content = Files.readString(file);
                for (Pattern pattern : BANNED)
                {
                    if (pattern.matcher(content).find())
                    {
                        offenders.add(fqcn + " matches /" + pattern.pattern() + "/");
                    }
                }
                return FileVisitResult.CONTINUE;
            }
        });

        if (!offenders.isEmpty())
        {
            throw new AssertionError(
                    "PRD 052 — banned AWT-static listener registrations found in rapla-client. " +
                    "Either route via SwingSafe.invokeLater (per Phase 1a) or document the cleanup " +
                    "pattern in PRD 052 and add the class FQCN to ALLOWLIST in " +
                    AwtStaticListenerGuardTest.class.getName() + ". Offenders:\n  " +
                    String.join("\n  ", offenders));
        }
    }

    private static String relativeFqcn(Path root, Path file)
    {
        Path rel = root.relativize(file);
        String path = rel.toString();
        if (path.endsWith(".java")) path = path.substring(0, path.length() - ".java".length());
        return path.replace('/', '.').replace('\\', '.');
    }
}
