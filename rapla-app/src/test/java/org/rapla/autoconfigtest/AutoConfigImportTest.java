package org.rapla.autoconfigtest;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rapla.RaplaResources;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaLocale;
import org.rapla.scheduler.CommandScheduler;
import org.rapla.server.spring.RaplaServerAutoConfiguration;
import org.rapla.server.spring.RaplaServerProperties;
import org.rapla.server.spring.RaplaSpringBootApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.graphql.execution.GraphQlSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the plugin deployment shape: a jar in {@code ./plugins/} contributes an
 * {@code @AutoConfiguration} from a package OTHER than {@code org.rapla.server.spring}, listed in
 * its {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports},
 * and it runs inside the stock {@link RaplaSpringBootApplication}. dhbwrapla is exactly such a
 * plugin — there is no custom {@code @SpringBootApplication} any more (PRD 045 §4).
 *
 * <p>Both halves go through the real imports-file discovery:
 * <ul>
 *   <li>{@link RaplaServerAutoConfiguration} must be registered as an auto-configuration (bean
 *       definition named by its fully qualified class name) and not merely picked up by the stock
 *       application's component scan of {@code org.rapla.server.spring} (bean name
 *       {@code raplaServerAutoConfiguration}) — which is what happens if rapla-server's imports
 *       entry is lost.</li>
 *   <li>{@link PluginAutoConfiguration} stands in for the plugin and is listed in this module's
 *       test imports file. It is guarded by a property only this test sets, so other contexts on
 *       the test classpath never load it.</li>
 * </ul>
 * The plugin must also share the context with the rapla-app GraphQL layer, which only the stock
 * application wires.
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class, properties = "rapla.test.autoconfig-plugin=true")
@Tag("e2e")
class AutoConfigImportTest
{
    record PluginBean(RaplaFacade facade) {}

    @AutoConfiguration(after = RaplaServerAutoConfiguration.class)
    @ConditionalOnProperty(name = "rapla.test.autoconfig-plugin", havingValue = "true")
    public static class PluginAutoConfiguration
    {
        @Bean
        PluginBean pluginBean(RaplaFacade facade)
        {
            return new PluginBean(facade);
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
    @Autowired
    GraphQlSource graphQlSource;
    @Autowired
    PluginBean pluginBean;

    @Test
    void raplaServerAutoConfigArrivesViaImportsFileNotComponentScan()
    {
        assertTrue(context.containsBeanDefinition(RaplaServerAutoConfiguration.class.getName()),
                "RaplaServerAutoConfiguration must be registered as an auto-configuration (imports file)");
        assertFalse(context.containsBeanDefinition("raplaServerAutoConfiguration"),
                "RaplaServerAutoConfiguration must not be a component-scanned @Configuration");
        assertNotNull(properties);
        assertNotNull(raplaResources);
        assertNotNull(raplaLocale);
        assertNotNull(commandScheduler);
    }

    @Test
    void pluginAutoConfigFromForeignPackageRunsInsideStockApplication()
    {
        assertTrue(context.containsBeanDefinition(PluginAutoConfiguration.class.getName()),
                "the plugin auto-configuration must be discovered through the imports file");
        assertNotNull(graphQlSource.schema());
        assertSame(raplaFacade, pluginBean.facade());
    }
}
