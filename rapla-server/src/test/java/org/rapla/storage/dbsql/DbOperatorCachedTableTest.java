package org.rapla.storage.dbsql;

import org.hsqldb.jdbc.JDBCDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rapla.RaplaResources;
import org.rapla.components.i18n.internal.AbstractBundleManager;
import org.rapla.components.i18n.server.ServerBundleManager;
import org.rapla.entities.domain.permission.PermissionExtension;
import org.rapla.entities.domain.permission.impl.RaplaDefaultPermissionImpl;
import org.rapla.entities.dynamictype.internal.StandardFunctions;
import org.rapla.entities.extensionpoints.FunctionFactory;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.internal.DefaultScheduler;
import org.rapla.framework.internal.RaplaLocaleImpl;
import org.rapla.scheduler.CommandScheduler;
import org.rapla.storage.dbfile.FileOperator;
import org.rapla.storage.impl.server.ImportExportManagerImpl;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tier-2 boot test asserting that a freshly created HSQLDB database uses
 * {@code CACHED} tables, not {@code MEMORY} tables.
 *
 * <p>HSQLDB defaults {@code CREATE TABLE} to a MEMORY table, which serialises
 * every row as a SQL {@code INSERT} in the {@code .script} file and re-parses
 * the whole file into RAM on every boot — the dominant startup cost on large
 * datasets (a 414 MB / 3M-row dhbw script took ~31 s just to open the
 * connection). CACHED tables keep rows in the binary {@code .data} file and
 * load lazily, so boot no longer scales with script size. This test locks in
 * that new databases are created CACHED.
 */
@Tag("db")
public class DbOperatorCachedTableTest
{
    private static final String DEFAULT_FIXTURE = "/testdefault.xml";

    @TempDir
    Path tempDir;

    private JDBCDataSource dataSource;
    private FileOperator fileOperator;
    private DBOperator operator;

    @BeforeEach
    void setUp() throws Exception
    {
        Path xmlFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = getClass().getResourceAsStream(DEFAULT_FIXTURE))
        {
            if (in == null) throw new IllegalStateException(DEFAULT_FIXTURE + " not on classpath");
            Files.copy(in, xmlFile, StandardCopyOption.REPLACE_EXISTING);
        }

        dataSource = new JDBCDataSource();
        dataSource.setUrl("jdbc:hsqldb:" + tempDir.resolve("rapla-db-" + UUID.randomUUID()).toAbsolutePath()
                + ";shutdown=true");
        dataSource.setUser("sa");
        dataSource.setPassword("");

        AbstractBundleManager bundleManager = new ServerBundleManager();
        RaplaResources i18n = new RaplaResources(bundleManager);
        RaplaLocale raplaLocale = new RaplaLocaleImpl(bundleManager);
        CommandScheduler scheduler = new DefaultScheduler();

        Set<PermissionExtension> permissionExtensions = new LinkedHashSet<>();
        permissionExtensions.add(new RaplaDefaultPermissionImpl());

        Map<String, FunctionFactory> functionFactoryMap = new LinkedHashMap<>();
        functionFactoryMap.put(StandardFunctions.NAMESPACE, new StandardFunctions(raplaLocale));

        fileOperator = new FileOperator(i18n, raplaLocale, scheduler,
                functionFactoryMap, xmlFile.toAbsolutePath().toString(),
                permissionExtensions);

        operator = new DBOperator(i18n, raplaLocale, scheduler, functionFactoryMap,
                () -> null, dataSource, permissionExtensions);

        ImportExportManagerImpl manager = new ImportExportManagerImpl(fileOperator, operator);
        operator.importExportManager = () -> manager;
    }

    @AfterEach
    void tearDown() throws Exception
    {
        if (operator != null && operator.isConnected())
        {
            try { operator.disconnect(); } catch (Exception ignored) {}
        }
        if (fileOperator != null && fileOperator.isConnected())
        {
            try { fileOperator.disconnect(); } catch (Exception ignored) {}
        }
    }

    @Test
    void newHsqldbTablesAreCreatedAsCached() throws Exception
    {
        operator.connect();
        assertTrue(operator.isConnected(), "operator should be connected after connect()");

        try (Connection c = dataSource.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT TABLE_NAME, HSQLDB_TYPE FROM INFORMATION_SCHEMA.SYSTEM_TABLES "
                             + "WHERE TABLE_SCHEM = 'PUBLIC'"))
        {
            int count = 0;
            while (rs.next())
            {
                String name = rs.getString("TABLE_NAME");
                String type = rs.getString("HSQLDB_TYPE");
                count++;
                assertEquals("CACHED", type,
                        "rapla table " + name + " should be created as a CACHED table (fast boot); was " + type);
            }
            assertTrue(count > 0, "expected rapla tables to have been created on the fresh HSQLDB");
        }
    }
}
