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
import org.rapla.entities.Category;
import org.rapla.entities.domain.permission.PermissionExtension;
import org.rapla.entities.domain.permission.impl.RaplaDefaultPermissionImpl;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.internal.StandardFunctions;
import org.rapla.entities.extensionpoints.FunctionFactory;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.internal.DefaultScheduler;
import org.rapla.framework.internal.RaplaLocaleImpl;
import org.rapla.logger.Logger;
import org.rapla.logger.RaplaBootstrapLogger;
import org.rapla.scheduler.CommandScheduler;
import org.rapla.storage.ImportExportManager;
import org.rapla.storage.dbfile.FileOperator;
import org.rapla.storage.impl.server.ImportExportManagerImpl;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tier-2 boot test for {@link DBOperator} against a temp-file HSQLDB.
 *
 * <p>Exercises the cold-start path with the same file→db import shape used in
 * production via {@link ImportExportManagerImpl}: a {@link FileOperator} reads
 * {@code testdefault.xml}, a {@link DBOperator} on a fresh HSQLDB connects,
 * detects the empty schema, and asks the manager for its source — the file
 * operator — to seed itself. After the seeding round-trip the DB has the full
 * schema (every {@code create*} table in {@link RaplaSQL}), the cache is
 * populated, and disconnect/reconnect re-reads the existing schema without
 * re-importing. Tagged {@code db} so the default fast-lane skips it (HSQLDB
 * driver init + import is a couple of seconds).
 */
@Tag("db")
public class DbOperatorBootTest
{
    private static final String DEFAULT_FIXTURE = "/testdefault.xml";

    @TempDir
    Path tempDir;

    private Logger logger;
    private JDBCDataSource dataSource;
    private FileOperator fileOperator;
    private DBOperator operator;

    @BeforeEach
    void setUp() throws Exception
    {
        logger = RaplaBootstrapLogger.createRaplaLogger();

        Path xmlFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = getClass().getResourceAsStream(DEFAULT_FIXTURE))
        {
            if (in == null) throw new IllegalStateException(DEFAULT_FIXTURE + " not on classpath");
            Files.copy(in, xmlFile, StandardCopyOption.REPLACE_EXISTING);
        }

        dataSource = new JDBCDataSource();
        // Per-test unique URL so concurrent test methods can't collide.
        dataSource.setUrl("jdbc:hsqldb:" + tempDir.resolve("rapla-db-" + UUID.randomUUID()).toAbsolutePath()
                + ";shutdown=true");
        dataSource.setUser("sa");
        dataSource.setPassword("");

        AbstractBundleManager bundleManager = new ServerBundleManager();
        RaplaResources i18n = new RaplaResources(bundleManager);
        RaplaLocale raplaLocale = new RaplaLocaleImpl(bundleManager);
        CommandScheduler scheduler = new DefaultScheduler(logger);

        Set<PermissionExtension> permissionExtensions = new LinkedHashSet<>();
        permissionExtensions.add(new RaplaDefaultPermissionImpl());

        Map<String, FunctionFactory> functionFactoryMap = new LinkedHashMap<>();
        functionFactoryMap.put(StandardFunctions.NAMESPACE, new StandardFunctions(raplaLocale));

        fileOperator = new FileOperator(logger, i18n, raplaLocale, scheduler,
                functionFactoryMap, xmlFile.toAbsolutePath().toString(),
                permissionExtensions);

        operator = new DBOperator(logger, i18n, raplaLocale, scheduler, functionFactoryMap,
                /* importExportSupplier set below */ () -> null, dataSource, permissionExtensions);

        // file → db: file is the source the DB seeds itself from on first connect.
        ImportExportManagerImpl manager = new ImportExportManagerImpl(logger, fileOperator, operator);
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
    void connectImportsFromFileSourceAndBootstrapsSchema() throws Exception
    {
        operator.connect();

        assertTrue(operator.isConnected(), "operator should be connected after connect()");

        // After import, the DB has every dynamic type from testdefault.xml — a
        // strict superset of the system-internal types alone.
        Collection<DynamicType> dynamicTypes = operator.getDynamicTypes();
        assertNotNull(dynamicTypes);
        assertFalse(dynamicTypes.isEmpty(), "imported dynamic types should be present after connect");

        Category superCategory = operator.getSuperCategory();
        assertNotNull(superCategory, "super-category must exist after connect");
        assertTrue(superCategory.getCategories().length > 0, "categories should have been imported");
    }

    @Test
    void disconnectThenReconnectReadsExistingSchemaWithoutReimporting() throws Exception
    {
        operator.connect();
        int dynamicTypeCountAfterImport = operator.getDynamicTypes().size();
        operator.disconnect();
        assertFalse(operator.isConnected(), "operator should report disconnected after disconnect()");

        // Reconnect on the same datasource — schema already exists, upgrade path
        // is a no-op (no second import). Same dynamic-type count expected.
        operator.connect();
        assertTrue(operator.isConnected(), "operator should reconnect cleanly on existing schema");
        assertTrue(operator.getDynamicTypes().size() == dynamicTypeCountAfterImport,
                "reconnect must not re-import; dynamic-type count should match the first connect");
    }
}
