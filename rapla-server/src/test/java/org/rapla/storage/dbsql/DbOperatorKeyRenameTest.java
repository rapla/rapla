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
import org.rapla.entities.User;
import org.rapla.entities.configuration.CalendarModelConfiguration;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.configuration.internal.CalendarModelConfigurationImpl;
import org.rapla.entities.domain.permission.PermissionExtension;
import org.rapla.entities.domain.permission.impl.RaplaDefaultPermissionImpl;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.ClassificationFilter;
import org.rapla.entities.dynamictype.ClassificationFilterRule;
import org.rapla.entities.dynamictype.ConstraintIds;
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
 * PRD 110 — a key rename must leave no stale key path in the database. The JDBC store
 * persists one row per entity, and DynamicType definitions / preferences reference
 * categories and types BY KEY PATH ({@code category[key='department']},
 * {@code dynamictype="room"}). Renaming the key rewrites only the edited entity unless
 * the dispatch re-stores every referencing entity too — the stale rows then surface
 * on the next boot (constraint silently dropped, filter rule dropped, or boot abort).
 * The XML file store cannot show this: it rewrites the whole file from the cache.
 */
@Tag("db")
class DbOperatorKeyRenameTest
{
    private static final String DEFAULT_FIXTURE = "/testdefault.xml";

    @TempDir Path tempDir;

    private JDBCDataSource dataSource;
    private FileOperator fileOperator;
    private DBOperator operator;
    private RaplaFacade facade;
    private RaplaResources i18n;
    private RaplaLocale raplaLocale;
    private CommandScheduler scheduler;
    private Map<String, FunctionFactory> functionFactoryMap;
    private Set<PermissionExtension> permissionExtensions;

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
        dataSource.setUrl("jdbc:hsqldb:" + tempDir.resolve("rapla-db-" + UUID.randomUUID()).toAbsolutePath() + ";shutdown=true");
        dataSource.setUser("sa");
        dataSource.setPassword("");

        AbstractBundleManager bundleManager = new ServerBundleManager();
        i18n = new RaplaResources(bundleManager);
        raplaLocale = new RaplaLocaleImpl(bundleManager);
        scheduler = new DefaultScheduler();
        permissionExtensions = new LinkedHashSet<>();
        permissionExtensions.add(new RaplaDefaultPermissionImpl());
        functionFactoryMap = new LinkedHashMap<>();
        functionFactoryMap.put(StandardFunctions.NAMESPACE, new StandardFunctions(raplaLocale));

        fileOperator = new FileOperator(i18n, raplaLocale, scheduler, functionFactoryMap, xmlFile.toAbsolutePath().toString(), permissionExtensions);
        connectFresh(true);
    }

    /** Builds a new DBOperator on the same database — the equivalent of a server restart. */
    private void connectFresh(boolean withImport) throws Exception
    {
        operator = new DBOperator(i18n, raplaLocale, scheduler, functionFactoryMap, () -> null, dataSource, permissionExtensions);
        if (withImport)
        {
            ImportExportManagerImpl manager = new ImportExportManagerImpl(fileOperator, operator);
            operator.importExportManager = () -> manager;
        }
        operator.connect();
        FacadeImpl impl = new FacadeImpl(i18n, scheduler);
        impl.setOperator(operator);
        facade = impl;
    }

    private void restart() throws Exception
    {
        operator.disconnect();
        connectFresh(false);
    }

    @AfterEach
    void tearDown()
    {
        if (operator != null && operator.isConnected()) { try { operator.disconnect(); } catch (Exception ignored) {} }
        if (fileOperator != null && fileOperator.isConnected()) { try { fileOperator.disconnect(); } catch (Exception ignored) {} }
    }

    @Test
    void categoryKeyRenameSurvivesRestart_constraintAndFilterRule() throws Exception
    {
        DynamicType room = facade.getDynamicType("room");
        Category department = facade.getSuperCategory().getCategory("department");
        assertNotNull(department, "fixture: department root category");
        Category testDepartment = department.getCategory("testdepartment");
        assertNotNull(testDepartment, "fixture: department/testdepartment");
        String departmentId = department.getId();

        // A preference filter rule "room.belongsto is testdepartment" — persisted as a key path
        // relative to the attribute's root category (department).
        ClassificationFilter filter = room.newClassificationFilter();
        filter.addRule("belongsto", new Object[][] { { "is", testDepartment } });
        storeCalendarFilter(filter);

        // Rename BOTH the root and the leaf key through the application.
        Category editableDept = facade.edit(department);
        editableDept.setKey("department_renamed");
        facade.store(editableDept);
        Category editableLeaf = facade.edit(facade.getSuperCategory().getCategory("department_renamed").getCategory("testdepartment"));
        editableLeaf.setKey("testdepartment_renamed");
        facade.store(editableLeaf);

        restart();

        Attribute belongsto = facade.getDynamicType("room").getAttribute("belongsto");
        Object root = belongsto.getConstraint(ConstraintIds.KEY_ROOT_CATEGORY);
        assertTrue(root instanceof Category, "root-category constraint must survive the restart, got " + root);
        assertEquals(departmentId, ((Category) root).getId(), "constraint must still point at the (renamed) department root");

        ClassificationFilterRule rule = singleRule();
        assertEquals("belongsto", rule.getAttribute().getKey());
        Object[] values = rule.getValues();
        assertEquals(1, values.length, "filter rule value must survive the restart");
        assertTrue(values[0] instanceof Category, "rule value must resolve to a category, got " + values[0]);
        assertEquals("testdepartment_renamed", ((Category) values[0]).getKey());
    }

    @Test
    void dynamicTypeKeyRenameSurvivesRestart_filterRule() throws Exception
    {
        DynamicType room = facade.getDynamicType("room");
        ClassificationFilter filter = room.newClassificationFilter();
        filter.addRule("name", new Object[][] { { "contains", "seminar" } });
        storeCalendarFilter(filter);

        DynamicType editable = facade.edit(room);
        editable.setKey("room_renamed");
        facade.store(editable);

        restart();   // must not throw "Dynamic type with name 'room' not found"

        ClassificationFilterRule rule = singleRule();
        assertEquals("room_renamed", rule.getAttribute().getDynamicType().getKey(), "filter must follow the renamed type");
        assertEquals("name", rule.getAttribute().getKey());
    }

    private void storeCalendarFilter(ClassificationFilter filter) throws Exception
    {
        CalendarModelConfigurationImpl config = new CalendarModelConfigurationImpl(
                null, null, false, new ClassificationFilter[] { filter },
                false, false, null, null, null, null, "week", new HashMap<>());
        Preferences prefs = facade.edit(facade.getPreferences(getAdmin()));
        prefs.putEntry(CalendarModelConfiguration.CONFIG_ENTRY, config);
        facade.store(prefs);
    }

    private ClassificationFilterRule singleRule() throws Exception
    {
        Preferences prefs = facade.getPreferences(getAdmin());
        CalendarModelConfiguration cfg = prefs.getEntry(CalendarModelConfiguration.CONFIG_ENTRY);
        assertNotNull(cfg, "calendar config entry must be present after restart");
        ClassificationFilter[] filters = cfg.getFilter();
        assertEquals(1, filters.length, "exactly one filter expected");
        Iterator<? extends ClassificationFilterRule> rules = filters[0].ruleIterator();
        assertTrue(rules.hasNext(), "filter rule must survive the restart (dropped = stale key path in the database)");
        return rules.next();
    }

    private User getAdmin() throws Exception
    {
        for (User u : facade.getUsers()) if (u.isAdmin()) return u;
        throw new IllegalStateException("fixture has no admin user");
    }
}
