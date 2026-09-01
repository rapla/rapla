package org.rapla.storage.dbsql;

import org.hsqldb.jdbc.JDBCDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rapla.RaplaResources;
import org.rapla.components.i18n.internal.AbstractBundleManager;
import org.rapla.components.i18n.server.ServerBundleManager;
import org.rapla.entities.User;
import org.rapla.entities.domain.permission.PermissionExtension;
import org.rapla.entities.domain.permission.impl.RaplaDefaultPermissionImpl;
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
import org.rapla.storage.xml.IOContext;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tier-2 regression for PRD 108 — {@code CHANGES.CHANGED_AT} must use the same
 * wall-clock convention as every other timestamp column.
 *
 * <p>{@code HistoryStorage} bound the column via {@code Timestamp.valueOf(utcLdt)},
 * storing the UTC wall-clock verbatim, while {@code AbstractTableStorage.setTimestamp}
 * stores the true instant rendered in the JVM zone (local wall-clock) — as Rapla 2
 * did for CHANGES too. On an in-place migrated Rapla 2 DB the legacy CHANGES rows of
 * the last {@code offset} hours therefore read back as lying in the future, get
 * replayed on every refresh and win in {@code EntityHistory.getLatest} against
 * Rapla 3's own writes.
 *
 * <p>Both tests are meaningless in a UTC JVM, so the default zone is pinned to
 * Europe/Berlin for the duration of the test (the operator captures its
 * {@code datetimeCal} at construction time, so this has to happen before
 * {@link #setUp()} builds it).
 */
@Tag("db")
class HistoryTimestampConventionTest
{
    private static final String DEFAULT_FIXTURE = "/testdefault.xml";

    @TempDir
    Path tempDir;

    private TimeZone originalZone;
    private RaplaResources i18n;
    private RaplaLocale raplaLocale;
    private JDBCDataSource dataSource;
    private FileOperator fileOperator;
    private DBOperator operator;
    private RaplaFacade facade;

    @BeforeEach
    void setUp() throws Exception
    {
        originalZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"));

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
        i18n = new RaplaResources(bundleManager);
        raplaLocale = new RaplaLocaleImpl(bundleManager);
        CommandScheduler scheduler = new DefaultScheduler();

        Set<PermissionExtension> permissionExtensions = new LinkedHashSet<>();
        permissionExtensions.add(new RaplaDefaultPermissionImpl());

        Map<String, FunctionFactory> functionFactoryMap = new LinkedHashMap<>();
        functionFactoryMap.put(StandardFunctions.NAMESPACE, new StandardFunctions(raplaLocale));

        fileOperator = new FileOperator(i18n, raplaLocale, scheduler,
                functionFactoryMap, xmlFile.toAbsolutePath().toString(), permissionExtensions);

        operator = new DBOperator(i18n, raplaLocale, scheduler, functionFactoryMap,
                () -> null, dataSource, permissionExtensions);

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
        if (operator != null && operator.isConnected())
        {
            try { operator.disconnect(); } catch (Exception ignored) {}
        }
        if (fileOperator != null && fileOperator.isConnected())
        {
            try { fileOperator.disconnect(); } catch (Exception ignored) {}
        }
        if (originalZone != null)
        {
            TimeZone.setDefault(originalZone);
        }
    }

    /**
     * The convention itself: one dispatch writes {@code RAPLA_USER.LAST_CHANGED}
     * and {@code CHANGES.CHANGED_AT} from the same connection timestamp, so both
     * columns must hold the same wall-clock value. Before the fix they were the
     * local UTC offset apart (2 h in Berlin summer time).
     */
    @Test
    @DisplayName("CHANGES.CHANGED_AT holds the same wall-clock as the entity table's LAST_CHANGED")
    void changedAtUsesTheSameWallClockAsEntityTables() throws Exception
    {
        assertNonUtcZone();
        User admin = getAdmin();

        User edit = facade.edit(admin);
        edit.setEmail("convention-check@example.invalid");
        facade.store(edit);

        LocalDateTime entityStamp = readSingleTimestamp(
                "SELECT LAST_CHANGED FROM RAPLA_USER WHERE ID = ?", admin.getId());
        LocalDateTime historyStamp = readSingleTimestamp(
                "SELECT MAX(CHANGED_AT) FROM CHANGES WHERE ID = ?", admin.getId());
        assertNotNull(entityStamp);
        assertNotNull(historyStamp);

        long secondsApart = Math.abs(Duration.between(entityStamp, historyStamp).getSeconds());
        assertTrue(secondsApart < 60,
                "CHANGES.CHANGED_AT (" + historyStamp + ") and RAPLA_USER.LAST_CHANGED (" + entityStamp
                        + ") come from the same dispatch and must use the same wall-clock convention, but are "
                        + secondsApart + " s apart — that is the local UTC offset, i.e. CHANGED_AT is still "
                        + "bound verbatim as a UTC LDT while every other column stores the true instant.");
    }

    /**
     * The migration symptom: a CHANGES row written by Rapla 2 (local wall-clock)
     * must not outrank a Rapla 3 row written later.
     */
    @Test
    @DisplayName("a legacy Rapla 2 CHANGES row does not mask a newer Rapla 3 write on refresh")
    void legacyHistoryRowDoesNotMaskNewerWrite() throws Exception
    {
        assertNonUtcZone();
        User admin = getAdmin();

        User first = facade.edit(admin);
        first.setEmail("legacy@example.invalid");
        facade.store(first);
        // The entity's lastChanged is the dispatch's true instant as a UTC LDT —
        // convention-independent, unlike the raw CHANGED_AT value.
        LocalDateTime legacyInstant = facade.getUser(admin.getUsername()).getLastChanged();
        LocalDateTime legacyRowStamp = readSingleTimestamp(
                "SELECT MAX(CHANGED_AT) FROM CHANGES WHERE ID = ?", admin.getId());
        assertNotNull(legacyRowStamp);

        User second = facade.edit(facade.getUser(admin.getUsername()));
        second.setEmail("rapla3@example.invalid");
        facade.store(second);

        // Rewrite the older row the way Rapla 2 wrote it: the same instant,
        // rendered as the local wall-clock.
        long offsetSeconds = ZoneId.systemDefault().getRules().getOffset(Instant.now()).getTotalSeconds();
        LocalDateTime asRapla2Wrote = legacyInstant.plusSeconds(offsetSeconds);
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE CHANGES SET CHANGED_AT = ? WHERE ID = ? AND CHANGED_AT = ?"))
        {
            ps.setTimestamp(1, Timestamp.valueOf(asRapla2Wrote));
            ps.setString(2, admin.getId());
            ps.setTimestamp(3, Timestamp.valueOf(legacyRowStamp));
            assertEquals(1, ps.executeUpdate(), "must rewrite exactly the legacy history row");
            c.commit();
        }

        operator.refresh();

        assertEquals("rapla3@example.invalid", facade.getUser(admin.getUsername()).getEmail(),
                "the replayed legacy CHANGES row (local wall-clock " + asRapla2Wrote
                        + ") must not win against the newer Rapla 3 write");
    }

    /**
     * PRD 108 follow-up — the daily history cleanup must actually run. It builds its
     * own {@link RaplaSQL} and calls {@code history.setConnection(con, null)}, so any
     * read on that instance that needs a connection timestamp kills the job (it is
     * wrapped in a catch-all that only logs "could not clean up history"), and
     * {@code CHANGES} grows without bound.
     */
    @Test
    @DisplayName("the daily history cleanup completes with a null connection timestamp")
    void historyCleanupRunsWithNullConnectionTimestamp() throws Exception
    {
        User admin = getAdmin();
        User edit = facade.edit(admin);
        edit.setEmail("cleanup-check@example.invalid");
        facade.store(edit);
        int before = countChangesRows();
        assertTrue(before > 0, "precondition: CHANGES must hold rows to clean up");

        // Mirrors DBOperator's scheduled task (DBOperator:148-155), including the
        // cutoff shape: lastRefreshed minus the history window, i.e. in the past.
        try (Connection c = dataSource.getConnection())
        {
            RaplaSQL raplaSQL = new RaplaSQL(new IOContext().createOutputContext(
                    raplaLocale, i18n, () -> operator.getSuperCategory(), true));
            raplaSQL.cleanupHistory(c, operator.getLastRefreshed().minusDays(1));
            c.commit();
        }

        assertEquals(before, countChangesRows(),
                "with a cutoff older than every row the cleanup must complete and delete nothing");
    }

    // ---------- helpers ----------

    private void assertNonUtcZone()
    {
        long offsetSeconds = ZoneId.systemDefault().getRules().getOffset(Instant.now()).getTotalSeconds();
        assertTrue(offsetSeconds != 0, "test needs a non-UTC default zone to be meaningful");
    }

    private LocalDateTime readSingleTimestamp(String sql, String id) throws Exception
    {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql))
        {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery())
            {
                if (!rs.next()) return null;
                Timestamp ts = rs.getTimestamp(1);
                return ts == null ? null : ts.toLocalDateTime();
            }
        }
    }

    private int countChangesRows() throws Exception
    {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM CHANGES");
             ResultSet rs = ps.executeQuery())
        {
            return rs.next() ? rs.getInt(1) : 0;
        }
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
