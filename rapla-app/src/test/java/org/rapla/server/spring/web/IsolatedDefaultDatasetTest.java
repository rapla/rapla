package org.rapla.server.spring.web;

import java.nio.file.Path;

import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base for full-context {@code @SpringBootTest} web-slice tests that don't need
 * a specific fixture — they just need a <em>clean, isolated</em> dataset.
 *
 * <p><b>Why this exists.</b> The production {@code application.yml} configures
 * {@code rapla.file-datasources.raplafile: data/data.xml} as a <em>relative</em>
 * path. A bare {@code @SpringBootTest} that doesn't override it resolves that
 * path against the JVM working directory — which, for {@code mvn ... test} run
 * from the reactor root, is the reactor root. So such tests silently load
 * {@code <reactor-root>/data/data.xml}: a stray, gitignored dev data file left
 * behind by a dev-server run. That file is real, stale, and was historically
 * corrupted by an old GraphqlKeyMigration (internal type keys sanitized
 * {@code rapla:anonymousEvent} → {@code rapla_anonymousEvent}), which made the
 * GraphQL schema build fail and every such test ERROR on context load.
 *
 * <p>Pointing {@code raplafile} at a fresh, non-existent file in a per-class
 * {@link TempDir} makes the {@code FileOperator} boot a pristine default system
 * (which ships the {@code admin}/empty-password user) — fully isolated from any
 * dev data on disk. Extend this instead of relying on the default config.
 */
public abstract class IsolatedDefaultDatasetTest
{
    @TempDir
    static Path raplaDataDir;

    @DynamicPropertySource
    static void isolateRaplaDataset(DynamicPropertyRegistry registry)
    {
        // Non-existent file → FileOperator creates a clean default system.
        registry.add("rapla.file-datasources.raplafile",
                () -> raplaDataDir.resolve("rapla-data.xml").toAbsolutePath().toString());
    }
}
