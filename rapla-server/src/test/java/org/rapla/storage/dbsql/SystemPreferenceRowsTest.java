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
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.domain.permission.PermissionExtension;
import org.rapla.entities.domain.permission.impl.RaplaDefaultPermissionImpl;
import org.rapla.entities.dynamictype.internal.StandardFunctions;
import org.rapla.entities.extensionpoints.FunctionFactory;
import org.rapla.facade.RaplaFacade;
import org.rapla.facade.internal.FacadeImpl;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.TypedComponentRole;
import org.rapla.framework.internal.DefaultScheduler;
import org.rapla.framework.internal.RaplaLocaleImpl;
import org.rapla.scheduler.CommandScheduler;
import org.rapla.storage.UpdateEvent;
import org.rapla.storage.dbfile.FileOperator;
import org.rapla.storage.impl.server.ImportExportManagerImpl;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * WP16/WP17 — system preferences (USER_ID NULL) must be replaced, not appended: the patch delete compared
 * {@code USER_ID = NULL} (never true) and the entity delete compared the VARCHAR column with the number 0
 * (MariaDB casts every non-numeric id to 0 and deletes all user preferences; HSQLDB refuses the cast).
 */
@Tag("db")
class SystemPreferenceRowsTest
{
    private static final TypedComponentRole<String> ROLE = new TypedComponentRole<>("org.rapla.test.wp17");

    @TempDir
    Path tempDir;

    private JDBCDataSource dataSource;
    private FileOperator fileOperator;
    private DBOperator operator;
    private RaplaFacade facade;

    @BeforeEach
    void setUp() throws Exception
    {
        Path xmlFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = getClass().getResourceAsStream("/testdefault.xml"))
        {
            Files.copy(in, xmlFile, StandardCopyOption.REPLACE_EXISTING);
        }
        dataSource = new JDBCDataSource();
        dataSource.setUrl("jdbc:hsqldb:" + tempDir.resolve("rapla-db-" + UUID.randomUUID()).toAbsolutePath() + ";shutdown=true");
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

        fileOperator = new FileOperator(i18n, raplaLocale, scheduler, functionFactoryMap, xmlFile.toAbsolutePath().toString(), permissionExtensions);
        operator = new DBOperator(i18n, raplaLocale, scheduler, functionFactoryMap, () -> null, dataSource, permissionExtensions);
        ImportExportManagerImpl manager = new ImportExportManagerImpl(fileOperator, operator);
        operator.importExportManager = () -> manager;
        operator.connect();

        FacadeImpl impl = new FacadeImpl(i18n, scheduler);
        impl.setOperator(operator);
        facade = impl;
    }

    @AfterEach
    void tearDown()
    {
        try { operator.disconnect(); } catch (Exception ignored) {}
        try { fileOperator.disconnect(); } catch (Exception ignored) {}
    }

    @Test
    void savingASystemPreferenceTwiceKeepsOneRow() throws Exception
    {
        storeSystemEntry("first");
        storeSystemEntry("second");
        assertEquals(1, count("SELECT COUNT(*) FROM PREFERENCE WHERE USER_ID IS NULL AND ROLE = ?", ROLE.getId()));
    }

    @Test
    void aRemovedSystemPreferenceStaysRemovedAfterReconnect() throws Exception
    {
        storeSystemEntry("value");
        storeSystemEntry(null);
        operator.disconnect();
        operator.connect();
        assertNull(operator.getPreferences(null, true).getEntryAsString(ROLE, null));
    }

    @Test
    void storingSystemPreferencesAsEntityKeepsUserPreferences() throws Exception
    {
        User user = facade.getUsers()[0];
        Preferences userPrefs = facade.edit(facade.getPreferences(user));
        userPrefs.putEntry(ROLE, "user value");
        facade.store(userPrefs);
        int userRows = count("SELECT COUNT(*) FROM PREFERENCE WHERE USER_ID = ?", user.getId());

        UpdateEvent evt = new UpdateEvent();
        evt.addStore(operator.editObject(operator.getPreferences(null, true), null));
        operator.dispatch(evt);

        assertEquals(userRows, count("SELECT COUNT(*) FROM PREFERENCE WHERE USER_ID = ?", user.getId()), "user preferences survive a system preferences entity store");
    }

    @Test
    void theNewestRowOfARoleWinsOnLoad() throws Exception
    {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        insertSystemRow(ROLE.getId(), "newer", now.minusHours(1));
        insertSystemRow(ROLE.getId(), "older", now.minusDays(1));
        operator.disconnect();
        operator.connect();
        assertEquals("newer", operator.getPreferences(null, true).getEntryAsString(ROLE, null));
    }

    @Test
    void duplicateSystemRowsAreCleanedUpOnce() throws Exception
    {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        insertSystemRow(ROLE.getId(), "old", now.minusDays(2));
        insertSystemRow(ROLE.getId(), "middle", now.minusDays(1));
        insertSystemRow(ROLE.getId(), "newest", now.minusHours(1));
        insertSystemRow("org.rapla.test.single", "only", now.minusHours(1));
        operator.disconnect();
        operator.connect();

        operator.removeDuplicateSystemPreferencesIfNeeded();
        assertEquals(1, count("SELECT COUNT(*) FROM PREFERENCE WHERE USER_ID IS NULL AND ROLE = ?", ROLE.getId()));
        assertEquals(1, count("SELECT COUNT(*) FROM PREFERENCE WHERE USER_ID IS NULL AND ROLE = ?", "org.rapla.test.single"));
        assertEquals("newest", operator.getPreferences(null, true).getEntryAsString(ROLE, null));
        assertEquals(1, count("SELECT COUNT(*) FROM PREFERENCE WHERE USER_ID IS NULL AND ROLE = ?", DuplicateSystemPreferenceCleanup.MARKER_KEY.getId()));

        String rowsAfterFirstRun = rows();
        operator.removeDuplicateSystemPreferencesIfNeeded();
        assertEquals(rowsAfterFirstRun, rows(), "second run is a no-op");
    }

    private void insertSystemRow(String role, String value, LocalDateTime lastChanged) throws Exception
    {
        try (Connection c = dataSource.getConnection();
             PreparedStatement stmt = c.prepareStatement("INSERT INTO PREFERENCE (USER_ID, ROLE, STRING_VALUE, XML_VALUE, LAST_CHANGED) VALUES (NULL, ?, ?, NULL, ?)"))
        {
            stmt.setString(1, role);
            stmt.setString(2, value);
            stmt.setTimestamp(3, Timestamp.valueOf(lastChanged));
            stmt.executeUpdate();
        }
    }

    private String rows() throws Exception
    {
        StringBuilder out = new StringBuilder();
        try (Connection c = dataSource.getConnection();
             PreparedStatement stmt = c.prepareStatement("SELECT ROLE, STRING_VALUE, LAST_CHANGED FROM PREFERENCE WHERE USER_ID IS NULL ORDER BY ROLE, LAST_CHANGED");
             ResultSet rs = stmt.executeQuery())
        {
            while (rs.next()) out.append(rs.getString(1)).append('|').append(rs.getString(2)).append('|').append(rs.getTimestamp(3)).append('\n');
        }
        return out.toString();
    }

    private void storeSystemEntry(String value) throws Exception
    {
        Preferences sys = facade.edit(facade.getSystemPreferences());
        sys.putEntry(ROLE, value);
        facade.store(sys);
    }

    private int count(String sql, String param) throws Exception
    {
        try (Connection c = dataSource.getConnection(); PreparedStatement stmt = c.prepareStatement(sql))
        {
            stmt.setString(1, param);
            try (ResultSet rs = stmt.executeQuery())
            {
                rs.next();
                return rs.getInt(1);
            }
        }
    }
}
