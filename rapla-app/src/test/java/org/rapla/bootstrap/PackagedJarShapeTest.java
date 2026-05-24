package org.rapla.bootstrap;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * PRD 045 Phase 4 — fat JAR shape contract.
 *
 * Asserts that the packaged rapla-app fat JAR uses Spring Boot's
 * PropertiesLauncher and ships a bundled loader.properties pointing at the
 * external lib/ and plugins/ directories. Without these two pieces, drop-in
 * JDBC drivers (lib/) and drop-in plugins (plugins/) are not discovered at
 * launch — PRD 045 §4 and §"Phase 4" rest on this contract.
 *
 * Tagged e2e because it requires `mvn package` to have produced the fat JAR;
 * skips with a clear message if the artifact isn't there.
 */
@Tag("e2e")
class PackagedJarShapeTest
{
    private static final String FAT_JAR = "target/rapla-2.1-SNAPSHOT.jar";

    @Test
    void manifestDeclaresPropertiesLauncher() throws IOException
    {
        Path jar = locateFatJar();
        try (JarFile jf = new JarFile(jar.toFile()))
        {
            Manifest manifest = jf.getManifest();
            assertNotNull(manifest, "Fat JAR has no MANIFEST.MF");
            String mainClass = manifest.getMainAttributes().getValue("Main-Class");
            assertEquals(
                    "org.springframework.boot.loader.launch.PropertiesLauncher",
                    mainClass,
                    "Fat JAR Main-Class must be PropertiesLauncher (not JarLauncher) "
                            + "so external lib/ and plugins/ directories are picked up via loader.path. "
                            + "Switch spring-boot-maven-plugin to <layout>ZIP</layout>.");
        }
    }

    @Test
    void loaderPropertiesShipsLibAndPluginsPaths() throws IOException
    {
        Path jar = locateFatJar();
        try (JarFile jf = new JarFile(jar.toFile()))
        {
            // PropertiesLauncher searches both / and /BOOT-INF/classes/ — either is fine.
            // spring-boot-maven-plugin's repackage moves src/main/resources/ content into
            // BOOT-INF/classes/, which is where PropertiesLauncher finds it.
            var entry = jf.getEntry("BOOT-INF/classes/loader.properties");
            if (entry == null)
            {
                entry = jf.getEntry("loader.properties");
            }
            assertNotNull(entry,
                    "Fat JAR is missing loader.properties — add "
                            + "rapla-app/src/main/resources/loader.properties with "
                            + "loader.path=lib/,plugins/");
            Properties props = new Properties();
            try (InputStream in = jf.getInputStream(entry))
            {
                props.load(in);
            }
            String loaderPath = props.getProperty("loader.path");
            assertNotNull(loaderPath, "loader.properties is missing loader.path");
            assertTrue(loaderPath.contains("lib/"),
                    "loader.path must include lib/ for JDBC drivers (PRD 045 §3). Got: " + loaderPath);
            assertTrue(loaderPath.contains("plugins/"),
                    "loader.path must include plugins/ for drop-in plugin jars (PRD 045 §4). Got: " + loaderPath);
        }
    }

    private static Path locateFatJar()
    {
        Path jar = Paths.get(FAT_JAR);
        if (!Files.exists(jar))
        {
            jar = Paths.get("rapla-app").resolve(FAT_JAR);
        }
        assumeTrue(Files.exists(jar),
                "Fat JAR not built — run `mvn -pl rapla-app -am package -DskipTests` first.");
        return jar;
    }
}
