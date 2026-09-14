package org.rapla.autoconfigtest;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rapla.RaplaResources;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaLocale;
import org.rapla.scheduler.CommandScheduler;
import org.rapla.server.spring.RaplaServerProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Verifies the rapla-server auto-configuration works from a {@code @SpringBootApplication}
 * that lives in a package OTHER than {@code org.rapla.server.spring} — proving the autoconfig
 * is delivered via {@code META-INF/spring/AutoConfiguration.imports}, not by accidental
 * package-overlap with the main class. This is the contract dhbwrapla and any future custom
 * deployment relies on.
 */
@SpringBootTest(classes = AutoConfigImportTest.MinimalTestApp.class)
@Tag("e2e")
class AutoConfigImportTest
{
    @SpringBootApplication
    static class MinimalTestApp
    {
        public static void main(String[] args)
        {
            SpringApplication.run(MinimalTestApp.class, args);
        }
    }

    @TempDir
    static Path tempDir;
    static Path dataFile;

    @BeforeAll
    static void copyTestData() throws IOException
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = AutoConfigImportTest.class.getResourceAsStream("/testdefault.xml"))
        {
            assertNotNull(in);
            Files.copy(in, dataFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry)
    {
        registry.add("rapla.file-datasources.raplafile", () -> dataFile.toAbsolutePath().toString());
    }

    @Autowired
    ApplicationContext context;
    @Autowired
    RaplaServerProperties properties;
    @Autowired
    RaplaResources raplaResources;
    @Autowired
    RaplaLocale raplaLocale;
    @Autowired
    CommandScheduler commandScheduler;
    @Autowired
    RaplaFacade raplaFacade;

    @Test
    void autoConfigDeliversCoreBeansToForeignPackage()
    {
        assertNotNull(context);
        assertNotNull(properties);
        assertNotNull(raplaResources);
        assertNotNull(raplaLocale);
        assertNotNull(commandScheduler);
        assertNotNull(raplaFacade);
    }
}
