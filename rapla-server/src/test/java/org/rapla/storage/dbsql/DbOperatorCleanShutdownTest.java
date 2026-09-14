package org.rapla.storage.dbsql;

import org.hsqldb.jdbc.JDBCDataSource;
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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tier-2 test that {@link DBOperator#disconnect()} cleanly shuts an embedded HSQLDB
 * down (runs {@code SHUTDOWN}, leaving {@code modified=no} in the properties file).
 *
 * <p>Regression for the dead {@code hsqldb} flag: the field was declared {@code false}
 * and never assigned, so the {@code SHUTDOWN} branch in {@code disconnect()} never ran
 * and the database was left dirty ({@code modified=yes}) on every stop — forcing a
 * {@code .log} replay / recovery pass on the next boot. {@code loadData()} now sets the
 * flag from the JDBC product name.
 *
 * <p>The datasource URL deliberately omits {@code ;shutdown=true} so HSQLDB does NOT
 * auto-close when the last connection drops — the only thing that can produce
 * {@code modified=no} is our explicit {@code SHUTDOWN}. Before the fix this test fails
 * with {@code modified=yes}.
 */
@Tag("db")
public class DbOperatorCleanShutdownTest
{
    private static final String DEFAULT_FIXTURE = "/testdefault.xml";

    @TempDir
    Path tempDir;

    private Path dbBase;
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

        dbBase = tempDir.resolve("rapla-db-" + UUID.randomUUID()).toAbsolutePath();
        JDBCDataSource dataSource = new JDBCDataSource();
        // NOTE: no ";shutdown=true" — so only our explicit SHUTDOWN can clean-close the DB.
        dataSource.setUrl("jdbc:hsqldb:" + dbBase);
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

    @Test
    void disconnectCleanlyShutsDownHsqldb() throws Exception
    {
        operator.connect();
        assertTrue(operator.isConnected(), "operator should be connected after connect()");

        operator.disconnect();
        assertFalse(operator.isConnected(), "operator should report disconnected after disconnect()");

        Path props = Path.of(dbBase + ".properties");
        assertTrue(Files.exists(props), "HSQLDB properties file must exist after shutdown");
        List<String> lines = Files.readAllLines(props);
        assertTrue(lines.stream().anyMatch(l -> l.trim().equals("modified=no")),
                "disconnect() must run SHUTDOWN so the DB is cleanly closed (modified=no); "
                        + "modified=yes means the hsqldb flag never drove the SHUTDOWN branch. Props: " + lines);
    }
}
