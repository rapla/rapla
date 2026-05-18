package org.rapla.server.spring;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rapla.entities.User;
import org.rapla.facade.RaplaFacade;
import org.rapla.server.internal.ServerStorageSelector;
import org.rapla.storage.dbsql.DBOperator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PRD 045 Phase 3 — verifies a database-backed deployment boots.
 *
 * <p>Configures {@code rapla.db-datasources.rapladb} (an embedded HSQLDB), so
 * {@code ServerCoreConfig.raplaDataSource} must build a {@link javax.sql.DataSource}
 * from it (PRD 048) and {@code ServerStorageSelector} must select
 * the {@link DBOperator} as the live store. The XML file datasource is repointed
 * at a temp seed — it is the {@code ImportExportManager} source the empty
 * database is bootstrapped from on first connect.
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@Tag("e2e")
@Tag("db")
class DbDatasourceBootIntegrationTest
{
    @TempDir
    static Path tempDir;
    static Path dataFile;

    @BeforeAll
    static void copySeedData() throws IOException
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = DbDatasourceBootIntegrationTest.class.getResourceAsStream("/testdefault.xml"))
        {
            assertNotNull(in);
            Files.copy(in, dataFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry)
    {
        // File datasource = the bootstrap import seed for the empty database.
        registry.add("rapla.file-datasources.raplafile", () -> dataFile.toAbsolutePath().toString());
        // Database datasource = the live store. Embedded HSQLDB, driver class
        // auto-derived from the jdbc:hsqldb: URL scheme.
        registry.add("rapla.db-datasources.rapladb.url", () -> "jdbc:hsqldb:mem:rapla-phase3-test");
        registry.add("rapla.db-datasources.rapladb.username", () -> "SA");
        registry.add("rapla.db-datasources.rapladb.password", () -> "");
    }

    @Autowired
    ApplicationContext context;

    @Autowired
    ServerStorageSelector serverStorageSelector;

    @Autowired
    RaplaFacade raplaFacade;

    @Test
    void contextBootsWithDbDatasource()
    {
        assertNotNull(context);
    }

    @Test
    void liveOperatorIsTheDbOperator()
    {
        assertTrue(serverStorageSelector.get() instanceof DBOperator,
                "with rapla.db-datasources.rapladb configured the live store must be the DBOperator");
    }

    @Test
    void schemaCreatedAndBootstrapImported() throws Exception
    {
        User[] users = raplaFacade.getUsers();
        assertTrue(users.length > 0, "schema must be created and the seed imported");
        assertTrue(Arrays.stream(users).anyMatch(User::isAdmin), "the bootstrap admin must be present");
    }
}
