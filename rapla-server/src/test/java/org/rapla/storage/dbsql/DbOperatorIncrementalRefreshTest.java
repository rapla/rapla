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
import org.rapla.framework.internal.DefaultScheduler;
import org.rapla.framework.internal.RaplaLocaleImpl;
import org.rapla.scheduler.CommandScheduler;
import org.rapla.storage.dbfile.FileOperator;
import org.rapla.storage.impl.server.ImportExportManagerImpl;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression test for the incremental-refresh read ordering (prod incident
 * 2026-06-10): the full load reads DynamicTypes / Categories / Users /
 * Allocatables BEFORE Preferences (storage registration order in
 * {@link RaplaSQL}), so preference XML that references a type or attribute
 * by key always resolves. The incremental path
 * ({@code DBOperator.readRefreshInfoFromDb}) parsed preference patches
 * against the live cache BEFORE this cycle's entity changes were applied.
 *
 * <p>Two-pod shape: operator A renames an attribute key that a stored
 * calendar-preference filter references; operator B (same DB, simulating a
 * second pod) pulls the change via its poll {@code refresh()}. Without the
 * ordering fix B's patch parse resolves the new key against its stale type
 * — the filter rule crashed the refresh (pre-guard) or is silently dropped
 * (guard only).
 */
@Tag("db")
public class DbOperatorIncrementalRefreshTest
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

        operatorA = new DBOperator(i18n, raplaLocale, scheduler, functionFactoryMap,
                () -> null, dataSource, permissionExtensions);
        ImportExportManagerImpl manager = new ImportExportManagerImpl(fileOperator, operatorA);
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

    @Test
    void attributeRenameKeepsPreferenceFilterRuleOnPollingPod() throws Exception
    {
        User adminA = getAdmin(facadeA);
        DynamicType roomType = facadeA.getDynamicType("room");
        assertNotNull(roomType, "fixture must define a 'room' dynamic type");

        // 1. Pod A stores a calendar preference whose filter has a rule on room.name.
        ClassificationFilter filter = roomType.newClassificationFilter();
        filter.addRule("name", new Object[][] { { "contains", "seminar" } });
        CalendarModelConfigurationImpl config = new CalendarModelConfigurationImpl(
                null, null, false, new ClassificationFilter[] { filter },
                false, false, null, null, null, null, "week", new HashMap<>());
        Preferences prefs = facadeA.edit(facadeA.getPreferences(adminA));
        prefs.putEntry(CalendarModelConfiguration.CONFIG_ENTRY, config);
        facadeA.store(prefs);

        // Pod B pulls the preference — sanity precondition.
        operatorB.refresh();
        assertEquals("name", singleRule(facadeB).getAttribute().getKey(),
                "precondition: pod B must see the stored filter rule");

        // 2. Pod A renames the attribute key. The dispatch propagates the
        // dependent filter rule to the new key and commits both in one cycle.
        DynamicType editType = facadeA.edit(roomType);
        Attribute attribute = editType.getAttribute("name");
        assertNotNull(attribute);
        attribute.setKey("roomname");
        facadeA.store(editType);

        // 3. Pod B polls. Its patch parse must see the renamed type (read
        // ordering: types before preferences, as in the full load) — the
        // rule must survive, reference the renamed attribute, and the parse
        // must not fall back to the dropped-rule error path.
        ch.qos.logback.classic.Logger readerLogger = (ch.qos.logback.classic.Logger)
                org.slf4j.LoggerFactory.getLogger("org.rapla.storage.xml.ClassificationFilterReader");
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender =
                new ch.qos.logback.core.read.ListAppender<>();
        appender.start();
        readerLogger.addAppender(appender);
        try
        {
            operatorB.refresh();
        }
        finally
        {
            readerLogger.detachAppender(appender);
        }
        assertTrue(appender.list.stream().noneMatch(e -> e.getLevel() == ch.qos.logback.classic.Level.ERROR),
                "pod B's patch parse must resolve the renamed attribute without errors "
                + "(error = read-ordering bug: preference parsed before this cycle's types): " + appender.list);
        ClassificationFilterRule rule = singleRule(facadeB);
        assertEquals("roomname", rule.getAttribute().getKey(),
                "rule on pod B must reference the renamed attribute");
    }

    private ClassificationFilterRule singleRule(RaplaFacade facade) throws Exception
    {
        Preferences prefs = facade.getPreferences(getAdmin(facade));
        CalendarModelConfiguration cfg = prefs.getEntry(CalendarModelConfiguration.CONFIG_ENTRY);
        assertNotNull(cfg, "calendar config entry must be present");
        ClassificationFilter[] filters = cfg.getFilter();
        assertEquals(1, filters.length, "exactly one filter expected");
        Iterator<? extends ClassificationFilterRule> rules = filters[0].ruleIterator();
        assertTrue(rules.hasNext(),
                "filter rule must survive the incremental refresh "
                + "(dropped = read-ordering bug: patch parsed against stale type)");
        return rules.next();
    }

    private User getAdmin(RaplaFacade facade) throws Exception
    {
        for (User u : facade.getUsers())
        {
            if (u.isAdmin()) return u;
        }
        throw new IllegalStateException("fixture must include an admin user");
    }
}
