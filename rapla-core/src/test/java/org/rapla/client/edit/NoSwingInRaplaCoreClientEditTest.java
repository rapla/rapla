package org.rapla.client.edit;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Arch invariant for PRD 023: the pure-logic packages in rapla-core
 * MUST NOT import Swing, AWT, or anything from {@code rapla-client/.../swing/...}.
 * This is what makes the carve-outs reusable from the server REST layer
 * (PRD 024) and from a future Angular client.
 * <p>
 * Grep-based instead of ArchUnit to avoid pulling another dependency
 * into rapla-core's test classpath. Walks the source tree, scans
 * import lines, fails the test the moment a forbidden import sneaks in.
 * <p>
 * If you really need a non-headless type in one of these packages (you
 * almost certainly don't), discuss it first — the right move is to
 * move the offending class up into rapla-client.
 */
class NoSwingInRaplaCoreClientEditTest
{
    /** Source root packages that must stay headless. Add new ones as the
     *  carve-out grows. */
    private static final List<String> GUARDED_DIRECTORIES = List.of(
            "src/main/java/org/rapla/client/edit",
            "src/main/java/org/rapla/plugin/calendarview",
            "src/main/java/org/rapla/plugin/reservationedit"
    );

    /** Imports we never want to see in those directories. */
    private static final List<String> FORBIDDEN_IMPORT_PREFIXES = List.of(
            "javax.swing.",
            "java.awt.",
            "org.rapla.client.swing.",
            "org.rapla.facade.client.",
            "org.rapla.facade.server."
    );

    private static final Pattern IMPORT_LINE = Pattern.compile("^\\s*import\\s+([\\w.]+);");

    @Test
    void noForbiddenImportsInPureLogicPackages() throws IOException
    {
        List<String> violations = new ArrayList<>();
        Path moduleRoot = locateModuleRoot();
        for (String relDir : GUARDED_DIRECTORIES)
        {
            Path dir = moduleRoot.resolve(relDir);
            if (!Files.exists(dir)) continue;       // package may not exist yet — fine
            try (Stream<Path> walk = Files.walk(dir))
            {
                walk.filter(p -> p.toString().endsWith(".java")).forEach(file -> {
                    try { scanFile(file, violations); }
                    catch (IOException e) { throw new RuntimeException(e); }
                });
            }
        }
        if (!violations.isEmpty())
        {
            fail("PRD 023 arch invariant violated — forbidden imports found in pure-logic packages:\n  "
                    + String.join("\n  ", violations)
                    + "\n\nIf you genuinely need a Swing/AWT/facade-client type here, move the class"
                    + " up into rapla-client. The rapla-core pure-logic packages must stay headless"
                    + " so they are reusable from the server REST layer and a future Angular client.");
        }
    }

    private static void scanFile(Path file, List<String> violations) throws IOException
    {
        List<String> lines = Files.readAllLines(file);
        for (int i = 0; i < lines.size(); i++)
        {
            String line = lines.get(i);
            var m = IMPORT_LINE.matcher(line);
            if (!m.find()) continue;
            String imp = m.group(1);
            for (String forbidden : FORBIDDEN_IMPORT_PREFIXES)
            {
                if (imp.startsWith(forbidden))
                {
                    violations.add(file.getFileName() + ":" + (i + 1) + "  imports " + imp);
                    break;
                }
            }
        }
    }

    /** Resolve the rapla-core module root from the running test's CWD.
     *  Surefire runs with CWD = the module dir; sanity-check by looking for {@code pom.xml}. */
    private static Path locateModuleRoot()
    {
        Path cwd = Paths.get("").toAbsolutePath();
        // Try CWD, then walk up to find a directory that contains src/main/java/org/rapla/client/edit.
        for (Path candidate : new Path[] { cwd, cwd.getParent() })
        {
            if (candidate == null) continue;
            if (Files.exists(candidate.resolve("src/main/java/org/rapla/client/edit"))) return candidate;
        }
        // Fallback: assume rapla-core under cwd
        return cwd.resolve("rapla-core");
    }
}
