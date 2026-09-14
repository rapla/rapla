package org.rapla.storage.dbsql;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.hsqldb.jdbc.JDBCDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rapla.RaplaResources;
import org.rapla.components.i18n.internal.AbstractBundleManager;
import org.rapla.components.i18n.server.ServerBundleManager;
import org.rapla.entities.User;
import org.rapla.entities.configuration.CalendarModelConfiguration;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.configuration.internal.CalendarModelConfigurationImpl;
import org.rapla.entities.domain.permission.PermissionExtension;
import org.rapla.entities.domain.permission.impl.RaplaDefaultPermissionImpl;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.ClassificationFilter;
import org.rapla.entities.dynamictype.ClassificationFilterRule;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.internal.StandardFunctions;
import org.rapla.entities.extensionpoints.FunctionFactory;
import org.rapla.facade.RaplaFacade;
import org.rapla.facade.internal.FacadeImpl;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.TypedComponentRole;
import org.rapla.framework.internal.DefaultScheduler;
import org.rapla.framework.internal.RaplaLocaleImpl;
import org.rapla.scheduler.CommandScheduler;
import org.rapla.storage.dbfile.FileOperator;
import org.rapla.storage.impl.server.ImportExportManagerImpl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Security audit PH2 / WP S6 — removing a preference entry must be visible to every pod on a DB-backed store. The
 * storage refresh only reads changed PREFERENCE rows, so a removal is written as a marker row (both values NULL).
 */
@Tag("db")
public class PreferenceRemovalRefreshDbTest
{
    private static final String DEFAULT_FIXTURE = "/testdefault.xml";

    @TempDir
    Path tempDir;

    private RaplaResources i18n;
    private CommandScheduler scheduler;
    private FileOperator fileOperator;
    private DBOperator operatorA;
    private DBOperator operatorB;
    private RaplaFacade facadeA;
    private RaplaFacade facadeB;
    private Object dataSourceRef;
    private java.util.function.Supplier<DBOperator> operatorFactory;

    @BeforeEach
    void setUp() throws Exception
    {
        Path xmlFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = getClass().getResourceAsStream(DEFAULT_FIXTURE))
        {
            if (in == null) throw new IllegalStateException(DEFAULT_FIXTURE + " not on classpath");
            Files.copy(in, xmlFile, StandardCopyOption.REPLACE_EXISTING);
        }

        JDBCDataSource dataSource = new JDBCDataSource();
        dataSourceRef = dataSource;
        dataSource.setUrl("jdbc:hsqldb:" + tempDir.resolve("rapla-db-" + UUID.randomUUID()).toAbsolutePath()
                + ";shutdown=true");
        dataSource.setUser("sa");
        dataSource.setPassword("");

        AbstractBundleManager bundleManager = new ServerBundleManager();
        i18n = new RaplaResources(bundleManager);
        RaplaLocale raplaLocale = new RaplaLocaleImpl(bundleManager);
        scheduler = new DefaultScheduler();

        Set<PermissionExtension> permissionExtensions = new LinkedHashSet<>();
        permissionExtensions.add(new RaplaDefaultPermissionImpl());

        Map<String, FunctionFactory> functionFactoryMap = new LinkedHashMap<>();
        functionFactoryMap.put(StandardFunctions.NAMESPACE, new StandardFunctions(raplaLocale));

        fileOperator = new FileOperator(i18n, raplaLocale, scheduler,
                functionFactoryMap, xmlFile.toAbsolutePath().toString(), permissionExtensions);

        final ImportExportManagerImpl[] managerRef = new ImportExportManagerImpl[1];
        operatorFactory = () -> {
            DBOperator op = new DBOperator(i18n, raplaLocale, scheduler, functionFactoryMap, () -> null, dataSource, permissionExtensions);
            op.importExportManager = () -> managerRef[0];
            return op;
        };
        operatorA = new DBOperator(i18n, raplaLocale, scheduler, functionFactoryMap,
                () -> null, dataSource, permissionExtensions);
        ImportExportManagerImpl manager = new ImportExportManagerImpl(fileOperator, operatorA);
        managerRef[0] = manager;
        operatorA.importExportManager = () -> manager;
        operatorA.connect();
        facadeA = newFacade(operatorA);

        // Second pod on the same store — connects after A populated the DB.
        operatorB = new DBOperator(i18n, raplaLocale, scheduler, functionFactoryMap,
                () -> null, dataSource, permissionExtensions);
        operatorB.importExportManager = () -> manager;
        operatorB.connect();
        facadeB = newFacade(operatorB);
    }

    private DBOperator newOperator()
    {
        return operatorFactory.get();
    }

    private RaplaFacade newFacade(DBOperator operator)
    {
        FacadeImpl impl = new FacadeImpl(i18n, scheduler);
        impl.setOperator(operator);
        return impl;
    }

    @AfterEach
    void tearDown()
    {
        for (DBOperator op : new DBOperator[] { operatorB, operatorA })
        {
            if (op != null && op.isConnected())
            {
                try { op.disconnect(); } catch (Exception ignored) {}
            }
        }
        if (fileOperator != null && fileOperator.isConnected())
        {
            try { fileOperator.disconnect(); } catch (Exception ignored) {}
        }
    }


    private static final TypedComponentRole<String> ROLE = new TypedComponentRole<>("org.rapla.test.preferenceRemoval");

    private JDBCDataSource dataSource()
    {
        return (JDBCDataSource) dataSourceRef;
    }

    private void put(RaplaFacade facade, User user, String value) throws Exception
    {
        Preferences edit = facade.edit(facade.getPreferences(user));
        edit.putEntry(ROLE, value);
        facade.store(edit);
    }

    private static String read(RaplaFacade facade, User user) throws Exception
    {
        return facade.getPreferences(user).getEntryAsString(ROLE, null);
    }

    /** (a) A removal on pod A reaches pod B through B's poll refresh. */
    @Test
    void removedPreferenceEntryDisappearsOnSecondPod() throws Exception
    {
        User homerA = operatorA.getUser("homer");
        put(facadeA, homerA, "session-token-1");
        operatorB.refresh();
        assertEquals("session-token-1", read(facadeB, operatorB.getUser("homer")), "precondition: B sees the stored entry");

        put(facadeA, homerA, null);
        assertNull(read(facadeA, operatorA.getUser("homer")), "the removing pod itself must not keep the entry");
        operatorB.refresh();
        assertNull(read(facadeB, operatorB.getUser("homer")), "the second pod must see the removal after its refresh");
    }

    /** (b) The removal leaves a marker row, and a fresh initial load ignores it. */
    @Test
    void initialLoadIgnoresRemovalMarker() throws Exception
    {
        User homerA = operatorA.getUser("homer");
        put(facadeA, homerA, "session-token-2");
        put(facadeA, homerA, null);

        try (java.sql.Connection c = dataSource().getConnection();
             java.sql.PreparedStatement stmt = c.prepareStatement(
                     "SELECT COUNT(*) FROM PREFERENCE WHERE ROLE = ? AND STRING_VALUE IS NULL AND XML_VALUE IS NULL"))
        {
            stmt.setString(1, ROLE.getId());
            try (java.sql.ResultSet rs = stmt.executeQuery())
            {
                rs.next();
                assertEquals(1, rs.getInt(1), "the removal must leave exactly one marker row");
            }
        }

        DBOperator operatorC = newOperator();
        try
        {
            operatorC.connect();
            assertNull(read(newFacade(operatorC), operatorC.getUser("homer")), "a marker must not surface as an entry on initial load");
        }
        finally
        {
            operatorC.disconnect();
        }
    }
}
