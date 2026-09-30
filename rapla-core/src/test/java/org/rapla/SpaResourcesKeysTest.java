package org.rapla;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * PRD 124 — the SPA looks texts up by Swing key at runtime, so a typo only shows as the raw
 * key. Every key the SPA uses ({@code 'key' | t}, {@code .t('key')}) must exist in an English
 * base bundle, and every SpaResources key must have a German text.
 */
class SpaResourcesKeysTest
{
    private static final Pattern USED = Pattern.compile("'([\\w.]+)'\\s*\\|\\s*t\\b|(?:\\.|\\b)(?:t|tr)\\(\\s*'([\\w.]+)'");
    private static final Path ROOT = repoRoot();
    private static final Path RESOURCES = ROOT == null ? null : ROOT.resolve("rapla-core/src/main/resources");

    @Test
    void everyKeyTheSpaUsesExists() throws IOException
    {
        assumeTrue(ROOT != null, "rapla-angular not in this checkout");
        Path spa = ROOT.resolve("rapla-angular/src/app");
        Set<String> known = new TreeSet<>();
        try (Stream<Path> files = Files.walk(RESOURCES))
        {
            for (Path p : files.filter(f -> f.getFileName().toString().matches(".*Resources\\.properties")).toList())
            {
                known.addAll(load(p).stringPropertyNames());
            }
        }
        Set<String> missing = new TreeSet<>();
        try (Stream<Path> files = Files.walk(spa))
        {
            for (Path p : files.filter(f -> f.toString().matches(".*\\.(ts|html)") && !f.toString().endsWith(".spec.ts")).toList())
            {
                Matcher m = USED.matcher(Files.readString(p));
                while (m.find())
                {
                    String key = m.group(1) != null ? m.group(1) : m.group(2);
                    if (!known.contains(key))
                    {
                        missing.add(key + " (" + p.getFileName() + ")");
                    }
                }
            }
        }
        assertEquals(Set.of(), missing);
    }

    @Test
    void noSpaKeyShadowsAnotherBundle() throws IOException
    {
        assumeTrue(ROOT != null, "rapla-angular not in this checkout");
        Set<String> spa = load(RESOURCES.resolve("org/rapla/SpaResources.properties")).stringPropertyNames();
        Set<String> shadowed = new TreeSet<>();
        try (Stream<Path> files = Files.walk(RESOURCES))
        {
            for (Path p : files.filter(f -> f.getFileName().toString().matches(".*Resources\\.properties")
                    && !f.getFileName().toString().equals("SpaResources.properties")).toList())
            {
                for (String key : load(p).stringPropertyNames())
                {
                    if (spa.contains(key))
                    {
                        shadowed.add(key + " (" + p.getFileName() + ")");
                    }
                }
            }
        }
        assertEquals(Set.of(), shadowed);
    }

    @Test
    void everySpaKeyHasAGermanText() throws IOException
    {
        assumeTrue(ROOT != null, "rapla-angular not in this checkout");
        Path base = RESOURCES.resolve("org/rapla/SpaResources.properties");
        Set<String> missing = new TreeSet<>(load(base).stringPropertyNames());
        missing.removeAll(load(RESOURCES.resolve("org/rapla/SpaResources_de.properties")).stringPropertyNames());
        assertEquals(Set.of(), missing);
    }

    private static Properties load(Path p) throws IOException
    {
        Properties props = new Properties();
        try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8))
        {
            props.load(r);
        }
        return props;
    }

    private static Path repoRoot()
    {
        for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent())
        {
            if (Files.isDirectory(dir.resolve("rapla-angular/src/app")) && Files.isDirectory(dir.resolve("rapla-core")))
            {
                return dir;
            }
        }
        return null;
    }
}
