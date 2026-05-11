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
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.permission.PermissionExtension;
import org.rapla.entities.domain.permission.impl.RaplaDefaultPermissionImpl;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.internal.StandardFunctions;
import org.rapla.entities.extensionpoints.FunctionFactory;
import org.rapla.facade.RaplaFacade;
import org.rapla.facade.internal.FacadeImpl;
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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tier-2 round-trip tests for {@link DBOperator} — exercises the live UPDATE /
 * INSERT / DELETE paths of {@link RaplaSQL} (the boot test only hits the bulk
 * file→db importer). Each test mutates state via a {@link FacadeImpl} on top
 * of a DB-backed operator, then disconnects and reconnects to verify the
 * change actually round-tripped through HSQLDB. Tagged {@code db} for the
 * default fast-lane skip.
 */
@Tag("db")
public class DbOperatorRoundTripTest
{
    private static final String DEFAULT_FIXTURE = "/testdefault.xml";
    private static final Locale LOCALE = Locale.ENGLISH;

    @TempDir
    Path tempDir;

    private Logger logger;
    private RaplaResources i18n;
    private CommandScheduler scheduler;
    private FileOperator fileOperator;
    private DBOperator operator;
    private RaplaFacade facade;

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

        JDBCDataSource dataSource = new JDBCDataSource();
        dataSource.setUrl("jdbc:hsqldb:" + tempDir.resolve("rapla-db-" + UUID.randomUUID()).toAbsolutePath()
                + ";shutdown=true");
        dataSource.setUser("sa");
        dataSource.setPassword("");

        AbstractBundleManager bundleManager = new ServerBundleManager();
        i18n = new RaplaResources(bundleManager);
        RaplaLocale raplaLocale = new RaplaLocaleImpl(bundleManager);
        scheduler = new DefaultScheduler(logger);

        Set<PermissionExtension> permissionExtensions = new LinkedHashSet<>();
        permissionExtensions.add(new RaplaDefaultPermissionImpl());

        Map<String, FunctionFactory> functionFactoryMap = new LinkedHashMap<>();
        functionFactoryMap.put(StandardFunctions.NAMESPACE, new StandardFunctions(raplaLocale));

        fileOperator = new FileOperator(logger, i18n, raplaLocale, scheduler,
                functionFactoryMap, xmlFile.toAbsolutePath().toString(), permissionExtensions);

        operator = new DBOperator(logger, i18n, raplaLocale, scheduler, functionFactoryMap,
                () -> null, dataSource, permissionExtensions);

        ImportExportManagerImpl manager = new ImportExportManagerImpl(logger, fileOperator, operator);
        operator.importExportManager = () -> manager;

        operator.connect();

        FacadeImpl impl = new FacadeImpl(i18n, scheduler, logger);
        impl.setOperator(operator);
        facade = impl;
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

    /** Disconnect and reconnect the DB operator, then rebuild the facade on top. */
    private void reconnectFacade() throws Exception
    {
        operator.disconnect();
        operator.connect();
        FacadeImpl impl = new FacadeImpl(i18n, scheduler, logger);
        impl.setOperator(operator);
        facade = impl;
    }

    @Test
    void editAttributeRoundTripsThroughDb() throws Exception
    {
        Allocatable original = findFirstByTypeKey("room");
        assertNotNull(original, "fixture must contain at least one room");
        String originalName = original.getName(LOCALE);

        Allocatable editable = facade.edit(original);
        editable.getClassification().setValue("name", "ROOM-DB-EDITED");
        facade.store(editable);

        reconnectFacade();

        Allocatable refetched = (Allocatable) facade.tryResolve(original.getReference());
        assertNotNull(refetched, "edited allocatable must still resolve after DB reconnect");
        assertEquals("ROOM-DB-EDITED", refetched.getName(LOCALE),
                "name change must persist through DB write + cache reload");
        assertNotEquals(originalName, refetched.getName(LOCALE));
    }

    @Test
    void newAllocatableInsertedRoundTripsThroughDb() throws Exception
    {
        DynamicType roomType = facade.getDynamicType("room");
        assertNotNull(roomType, "fixture must define a 'room' dynamic type");

        Allocatable created = facade.newAllocatable(roomType.newClassification(), getAdmin());
        created.getClassification().setValue("name", "ROOM-INSERTED-VIA-DB");
        facade.store(created);

        reconnectFacade();

        Allocatable refetched = (Allocatable) facade.tryResolve(created.getReference());
        assertNotNull(refetched, "newly-inserted allocatable must resolve after DB reconnect");
        assertEquals("ROOM-INSERTED-VIA-DB", refetched.getName(LOCALE));
    }

    @Test
    void removedAllocatableRoundTripsThroughDb() throws Exception
    {
        // Insert first so we can safely remove without touching fixture entities
        // that other tests in this run (or referential integrity rules) depend on.
        DynamicType roomType = facade.getDynamicType("room");
        Allocatable created = facade.newAllocatable(roomType.newClassification(), getAdmin());
        created.getClassification().setValue("name", "ROOM-TO-REMOVE");
        facade.store(created);

        // Confirm visible before remove
        reconnectFacade();
        assertNotNull(facade.tryResolve(created.getReference()),
                "precondition: insert must round-trip before testing remove");

        // Remove via the writable copy
        Allocatable refetched = (Allocatable) facade.tryResolve(created.getReference());
        Allocatable editable = facade.edit(refetched);
        facade.remove(editable);

        reconnectFacade();
        assertNull(facade.tryResolve(created.getReference()),
                "removed allocatable must be gone after DB reconnect");
    }

    // ---------- helpers ----------

    private Allocatable findFirstByTypeKey(String typeKey) throws Exception
    {
        for (Allocatable a : facade.getAllocatables())
        {
            DynamicType t = a.getClassification().getType();
            if (t != null && typeKey.equals(t.getKey())) return a;
        }
        return null;
    }

    private User getAdmin() throws Exception
    {
        for (User u : facade.getUsers())
        {
            if (u.isAdmin()) return u;
        }
        throw new IllegalStateException("fixture must include an admin user");
    }
}
