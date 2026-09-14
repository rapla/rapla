package org.rapla.server;

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
 * Arch invariant for PRD 005 D3 + PRD 030 Phase 6: {@code rapla-server}
 * MUST NOT depend on anything that lives only in {@code rapla-client}.
 *
 * <p>The back-edge was eliminated when {@code RaplaBuilder}, the layout
 * strategies, the {@code AllocationConflictModel}, the recurrence
 * validator, etc. were carved into {@code rapla-core} (PRD 023).
 * This test pins the result: no Swing imports, no facade-client imports,
 * no rapla-client-only packages reachable from rapla-server's main source.
 *
 * <p>Note the {@code org.rapla.client.edit.*} prefix is <b>not</b>
 * forbidden — those packages live in {@code rapla-core} despite the
 * historical {@code .client.} package name. The same applies to
 * {@code org.rapla.client.menu.*}, {@code org.rapla.client.sidebar.*},
 * and others that were carved out. The forbidden list below targets
 * packages that physically live in {@code rapla-client/src/main/java}
 * and have no rapla-core counterpart.
 */
class NoRaplaClientImportInServerTest
{
    /** Source roots under rapla-server that must stay free of rapla-client deps. */
    private static final List<String> GUARDED_DIRECTORIES = List.of(
            "src/main/java"
    );

    /** Import-prefixes that resolve only to rapla-client.
     *  Note: {@code org.rapla.facade.client.} is intentionally absent —
     *  {@code ClientFacade} lives in rapla-core despite the package name. */
    private static final List<String> FORBIDDEN_IMPORT_PREFIXES = List.of(
            "javax.swing.",
            "java.awt.",
            "org.rapla.client.swing.",
            "org.rapla.client.dialog.swing.",
            "org.rapla.client.internal.check.swing.",
            "org.rapla.client.internal.edit.swing.",
            "org.rapla.client.menu.swing.",
            "org.rapla.client.spring."
    );

    private static final Pattern IMPORT_LINE = Pattern.compile("^\\s*import\\s+([\\w.]+);");

    @Test
    void noRaplaClientImportsInServerMain() throws IOException
    {
        List<String> violations = new ArrayList<>();
        Path moduleRoot = locateModuleRoot();
        for (String relDir : GUARDED_DIRECTORIES)
        {
            Path dir = moduleRoot.resolve(relDir);
            if (!Files.exists(dir)) continue;
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
            fail("PRD 005 D3 / PRD 030 Phase 6 arch invariant violated —"
                    + " rapla-server must not import from rapla-client packages:\n  "
                    + String.join("\n  ", violations)
                    + "\n\nIf you genuinely need server-side access to a class"
                    + " currently in rapla-client, carve it out into rapla-core"
                    + " following the PRD 023 pattern.");
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

    private static Path locateModuleRoot()
    {
        Path cwd = Paths.get("").toAbsolutePath();
        for (Path candidate : new Path[] { cwd, cwd.getParent() })
        {
            if (candidate == null) continue;
            if (Files.exists(candidate.resolve("src/main/java/org/rapla/server"))) return candidate;
        }
        return cwd.resolve("rapla-server");
    }
}
