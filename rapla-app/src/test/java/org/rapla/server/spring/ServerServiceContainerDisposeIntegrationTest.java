package org.rapla.server.spring;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rapla.storage.CachableStorageOperator;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression: the {@code serverServiceContainer} bean's destroy method must run on
 * Spring context close, so {@code ServerServiceImpl.dispose() -> stop() ->
 * operator.disconnect()} fires. For HSQLDB that {@code disconnect()} runs
 * {@code SHUTDOWN COMPACT}; if it never runs the {@code .script} grows unbounded and
 * {@code modified=yes} is left in the properties file.
 *
 * <p>The bean implements {@code ServerServiceContainer extends Disposable}, whose method
 * is {@code dispose()} — a name Spring does NOT auto-infer as a destroy method (it only
 * recognises {@code close}/{@code shutdown}/{@code DisposableBean}/{@code AutoCloseable}).
 * The fix declares {@code @Bean(destroyMethod = "dispose")} on the factory in
 * {@link ServerServiceConfig}. Before the fix the operator is still connected after
 * {@code context.close()}; after it, {@code disconnect()} has run.
 *
 * <p>Boots a fully self-owned context via {@link SpringApplicationBuilder} (so the test
 * owns the lifecycle and can call {@code close()} and observe the aftermath without
 * fighting the {@code @SpringBootTest} context-cache / {@code @DirtiesContext} listeners).
 * Uses the same embedded HSQLDB + testdefault.xml seed as
 * {@link DbDatasourceBootIntegrationTest} so the live store is the real {@code DBOperator}.
 */
@Tag("e2e")
@Tag("db")
class ServerServiceContainerDisposeIntegrationTest
{
    @TempDir
    static Path tempDir;
    static Path dataFile;

    @BeforeAll
    static void copySeedData() throws IOException
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = ServerServiceContainerDisposeIntegrationTest.class.getResourceAsStream("/testdefault.xml"))
        {
            assertNotNull(in);
            Files.copy(in, dataFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @Test
    void disposeRunsOnContextClose()
    {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("rapla.file-datasources.raplafile", dataFile.toAbsolutePath().toString());
        props.put("rapla.db-datasources.rapladb.url", "jdbc:hsqldb:mem:rapla-dispose-test");
        props.put("rapla.db-datasources.rapladb.username", "SA");
        props.put("rapla.db-datasources.rapladb.password", "");

        // Random ephemeral port via a command-line arg (higher precedence than
        // application.yml's fixed 8051) so this test never collides with a running
        // dev server; the test owns the full context lifecycle.
        ConfigurableApplicationContext context = new SpringApplicationBuilder(RaplaSpringBootApplication.class)
                .web(WebApplicationType.SERVLET)
                .properties(props)
                .run("--server.port=0");

        CachableStorageOperator operator = context.getBean(CachableStorageOperator.class);
        assertTrue(operator.isConnected(),
                "operator must be connected while the context is up");

        context.close();

        assertFalse(operator.isConnected(),
                "context close must invoke serverServiceContainer.dispose() -> stop() -> operator.disconnect(); "
                        + "operator still connected means the @Bean destroyMethod is missing");
    }
}
