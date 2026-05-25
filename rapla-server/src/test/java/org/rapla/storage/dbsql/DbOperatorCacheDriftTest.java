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
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.domain.permission.PermissionExtension;
import org.rapla.entities.domain.permission.impl.RaplaDefaultPermissionImpl;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.entities.dynamictype.internal.StandardFunctions;
import org.rapla.entities.extensionpoints.FunctionFactory;
import org.rapla.facade.RaplaFacade;
import org.rapla.facade.internal.FacadeImpl;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.internal.DefaultScheduler;
import org.rapla.framework.internal.RaplaLocaleImpl;
import org.rapla.scheduler.CommandScheduler;
import org.rapla.storage.RaplaNewVersionException;
import org.rapla.storage.dbfile.FileOperator;
import org.rapla.storage.impl.server.ImportExportManagerImpl;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tier-2 regression for PRD 054 — post-save cache drift in {@link DBOperator}.
 *
 * <p>After a successful {@code dispatch(...)} commit, the in-memory
 * {@code LocalCache} must reflect the new entity state (notably
 * {@code lastChanged}) <em>before</em> dispatch returns. If it doesn't:
 * <ul>
 *   <li>A second save of the same entity (no reconnect) tries to UPDATE/DELETE
 *       with a stale {@code LAST_CHANGED} predicate, matches zero rows, then
 *       the INSERT half of the delete+insert trips a PK violation.</li>
 *   <li>A newly created reservation isn't visible to the same operator until
 *       a reconnect/restart.</li>
 * </ul>
 *
 * <p>Tagged {@code db} so the default fast-lane skips it; see
 * {@code rapla-bom/pom.xml} for {@code test.excludedGroups}.
 */
@Tag("db")
class DbOperatorCacheDriftTest
{
    private static final String DEFAULT_FIXTURE = "/testdefault.xml";
    private static final Locale LOCALE = Locale.ENGLISH;

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

    /**
     * PRD 054 symptom #1 — two consecutive edits of the same user must both
     * succeed without a reconnect. Today the second {@code facade.store(...)}
     * trips a PK violation in HSQLDB because the cache still holds the
     * pre-first-save {@code lastChanged}.
     */
    @Test
    @DisplayName("two consecutive user saves succeed without reconnect")
    void twoConsecutiveUserSavesSucceed() throws Exception
    {
        User admin = getAdmin();
        LocalDateTime baselineLastChanged = admin.getLastChanged();

        User edit1 = facade.edit(admin);
        edit1.setEmail("first-change@example.invalid");
        facade.store(edit1);

        User afterFirst = facade.getUser(admin.getUsername());
        assertEquals("first-change@example.invalid", afterFirst.getEmail(),
                "cache must reflect first save's email immediately");
        assertNotEquals(baselineLastChanged, afterFirst.getLastChanged(),
                "cache must reflect first save's bumped lastChanged immediately");

        User edit2 = facade.edit(afterFirst);
        edit2.setEmail("second-change@example.invalid");
        facade.store(edit2);

        User afterSecond = facade.getUser(admin.getUsername());
        assertEquals("second-change@example.invalid", afterSecond.getEmail());
        assertTrue(afterSecond.getLastChanged().isAfter(afterFirst.getLastChanged())
                        || afterSecond.getLastChanged().equals(afterFirst.getLastChanged()),
                "second save's lastChanged must be >= first save's");
    }

    /**
     * PRD 054 symptom #2 — a reservation that was just saved must be visible
     * to the same operator's facade immediately, without a server restart.
     */
    @Test
    @DisplayName("a stored reservation is visible to the same operator without restart")
    void storedReservationVisibleImmediately() throws Exception
    {
        User admin = getAdmin();
        DynamicType eventType = facade.getDynamicTypes(
                DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION)[0];
        Allocatable room = findFirstByTypeKey("room");
        assertNotNull(room, "fixture must contain a room");

        Reservation r = facade.newReservation(eventType.newClassification(), admin);
        Appointment a = facade.newAppointmentWithUser(
                LocalDateTime.of(2026, 6, 1, 10, 0),
                LocalDateTime.of(2026, 6, 1, 11, 0), admin);
        r.addAppointment(a);
        r.addAllocatable(room);
        facade.store(r);

        Reservation refetched = (Reservation) facade.tryResolve(r.getReference());
        assertNotNull(refetched, "newly-stored reservation must be visible to the same operator");
        assertEquals(r.getReference(), refetched.getReference());
    }

    /**
     * PRD 054 — two consecutive moves of the same appointment must both
     * succeed without a reconnect. The user reported (2026-05-25) that the
     * first move was silent (post-save cache write-through OK) but the
     * second move tripped {@link RaplaNewVersionException} — the conditional
     * DELETE matched zero rows because the cache still held the pre-first-move
     * {@code lastChanged} for the reservation/appointment. Reproduces the
     * events-specific drift path.
     */
    @Test
    @DisplayName("two consecutive event moves succeed without reconnect")
    void twoConsecutiveEventMovesSucceed() throws Exception
    {
        User admin = getAdmin();
        DynamicType eventType = facade.getDynamicTypes(
                DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION)[0];
        Allocatable room = findFirstByTypeKey("room");
        assertNotNull(room, "fixture must contain a room");

        Reservation initial = facade.newReservation(eventType.newClassification(), admin);
        Appointment a0 = facade.newAppointmentWithUser(
                LocalDateTime.of(2026, 6, 1, 10, 0),
                LocalDateTime.of(2026, 6, 1, 11, 0), admin);
        initial.addAppointment(a0);
        initial.addAllocatable(room);
        facade.store(initial);

        Reservation afterFirstStore = (Reservation) facade.tryResolve(initial.getReference());
        assertNotNull(afterFirstStore, "stored reservation must be in cache");

        // First move: 10:00 → 14:00.
        Reservation move1 = facade.edit(afterFirstStore);
        move1.getAppointments()[0].move(
                LocalDateTime.of(2026, 6, 1, 14, 0),
                LocalDateTime.of(2026, 6, 1, 15, 0));
        facade.store(move1);

        Reservation afterMove1 = (Reservation) facade.tryResolve(initial.getReference());
        assertEquals(LocalDateTime.of(2026, 6, 1, 14, 0),
                afterMove1.getAppointments()[0].getStart(),
                "cache must reflect first move immediately (no reconnect)");

        // Second move: 14:00 → 16:00. This is where the user hit
        // RaplaNewVersionException pre-fix-for-events.
        Reservation move2 = facade.edit(afterMove1);
        move2.getAppointments()[0].move(
                LocalDateTime.of(2026, 6, 1, 16, 0),
                LocalDateTime.of(2026, 6, 1, 17, 0));
        facade.store(move2);

        Reservation afterMove2 = (Reservation) facade.tryResolve(initial.getReference());
        assertEquals(LocalDateTime.of(2026, 6, 1, 16, 0),
                afterMove2.getAppointments()[0].getStart(),
                "cache must reflect second move immediately");
    }

    /**
     * PRD 054 (2026-05-25 user report) — simulates a stale client moving twice
     * without re-fetching. After the first dispatch updates the server cache
     * to lastChanged=T1, a second dispatch carrying the *same* entity-instance
     * (still holding T0 from its original edit) hits
     * {@code LocalAbstractCachableOperator.checkVersions}: cache T1 > incoming
     * T0 ⇒ {@link RaplaNewVersionException}. Distinct from the conditional-
     * DELETE guard — the throw site lives in {@code check()}, not in
     * {@code EntityStorage.deleteEntities}.
     */
    @Test
    @DisplayName("stale client moves an event twice without re-fetching → checkVersions throws")
    void staleClientMovesTwiceTripsCheckVersions() throws Exception
    {
        User admin = getAdmin();
        DynamicType eventType = facade.getDynamicTypes(
                DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION)[0];
        Allocatable room = findFirstByTypeKey("room");

        Reservation initial = facade.newReservation(eventType.newClassification(), admin);
        Appointment a0 = facade.newAppointmentWithUser(
                LocalDateTime.of(2026, 6, 1, 10, 0),
                LocalDateTime.of(2026, 6, 1, 11, 0), admin);
        initial.addAppointment(a0);
        initial.addAllocatable(room);
        facade.store(initial);

        // The "client" holds a single edit handle (clone of cache, T0).
        // It performs move 1, dispatches — server bumps cache to T1.
        Reservation staleClientCopy = facade.edit(
                (Reservation) facade.tryResolve(initial.getReference()));
        LocalDateTime preMove1LastChanged = staleClientCopy.getLastChanged();
        staleClientCopy.getAppointments()[0].move(
                LocalDateTime.of(2026, 6, 1, 14, 0),
                LocalDateTime.of(2026, 6, 1, 15, 0));
        facade.store(staleClientCopy);

        // Simulate a wire-fresh client copy that never received the post-save
        // refresh: the in-process facade mutates the entity's lastChanged
        // during dbStore (a real wire client would have serialized JSON before
        // that). Manually roll back lastChanged to mimic the wire-fresh state
        // — this is what a Swing/SPA client would actually send if its local
        // cache was never refreshed.
        ((org.rapla.entities.internal.ModifiableTimestamp) staleClientCopy)
                .setLastChanged(preMove1LastChanged);

        staleClientCopy.getAppointments()[0].move(
                LocalDateTime.of(2026, 6, 1, 16, 0),
                LocalDateTime.of(2026, 6, 1, 17, 0));
        Throwable thrown = assertThrows(Exception.class, () -> facade.store(staleClientCopy));
        Throwable cursor = thrown;
        boolean found = false;
        while (cursor != null)
        {
            if (cursor instanceof RaplaNewVersionException)
            {
                found = true;
                break;
            }
            cursor = cursor.getCause();
        }
        assertTrue(found,
                "expected RaplaNewVersionException in the cause chain (checkVersions path), got: " + thrown);
    }

    /**
     * PRD 054 — same as {@link #twoConsecutiveEventMovesSucceed}, but moves an
     * EXISTING reservation from {@code testdefault.xml} (mirrors the real-world
     * scenario: a fixture event the user moves twice in the UI). The pre-existing
     * reservation has a {@code last-changed} from 2003 — exercises the case
     * where the cache's initial lastChanged came from {@code loadAll} (XML
     * read), not from a prior dispatch's refresh.
     */
    @Test
    @DisplayName("two consecutive moves of a fixture reservation succeed")
    void twoConsecutiveMovesOfFixtureReservationSucceed() throws Exception
    {
        Reservation fixture = null;
        for (Reservation r : waitFor(facade.getReservations(getAdmin(),
                LocalDateTime.of(2000, 1, 1, 0, 0),
                LocalDateTime.of(2030, 1, 1, 0, 0),
                null)))
        {
            if (r.getAppointments().length > 0)
            {
                fixture = r;
                break;
            }
        }
        assertNotNull(fixture, "fixture must contain a reservation with appointments");

        // First move: shift the first appointment by 2 hours.
        Reservation move1 = facade.edit(fixture);
        Appointment a1 = move1.getAppointments()[0];
        LocalDateTime origStart = a1.getStart();
        a1.move(origStart.plusHours(2), a1.getEnd().plusHours(2));
        facade.store(move1);

        Reservation afterMove1 = (Reservation) facade.tryResolve(fixture.getReference());
        assertEquals(origStart.plusHours(2),
                afterMove1.getAppointments()[0].getStart(),
                "cache must reflect first move immediately");

        // Second move: shift by another 2 hours. This is where the user hit
        // RaplaNewVersionException.
        Reservation move2 = facade.edit(afterMove1);
        Appointment a2 = move2.getAppointments()[0];
        a2.move(origStart.plusHours(4), a2.getEnd().plusHours(2));
        facade.store(move2);

        Reservation afterMove2 = (Reservation) facade.tryResolve(fixture.getReference());
        assertEquals(origStart.plusHours(4),
                afterMove2.getAppointments()[0].getStart(),
                "cache must reflect second move immediately");
    }

    /**
     * PRD 054 — defensive guard in {@link EntityStorage#deleteEntities}: when the
     * conditional DELETE matches 0 rows for an entity {@code cache.has(id)}
     * agrees exists, the storage layer must raise
     * {@link RaplaNewVersionException} (which surfaces as 409 Conflict). Before
     * the guard, the follow-up INSERT tripped a PK constraint with an opaque
     * message ("Während der Speicherung in der Datenbank").
     *
     * <p>Provoke the situation by side-channeling a {@code LAST_CHANGED} bump
     * via raw JDBC behind rapla's back, so the in-process cache stays at the
     * pre-bump timestamp and the next {@code facade.store(...)} hits the guard.
     */
    @Test
    @DisplayName("conditional-delete miss raises RaplaNewVersionException, not opaque PK violation")
    void conditionalDeleteMissRaisesNewVersionException() throws Exception
    {
        User admin = getAdmin();
        User editable = facade.edit(admin);
        editable.setEmail("about-to-collide@example.invalid");

        // Side-channel update: bump LAST_CHANGED on the row directly, without
        // going through dispatch. The cache still holds the pre-bump timestamp.
        try (java.sql.Connection c = dataSource.getConnection();
             java.sql.PreparedStatement ps = c.prepareStatement(
                     "UPDATE RAPLA_USER SET LAST_CHANGED = ? WHERE ID = ?"))
        {
            ps.setTimestamp(1, java.sql.Timestamp.valueOf(
                    LocalDateTime.now().plusYears(1)));
            ps.setString(2, admin.getId());
            assertEquals(1, ps.executeUpdate(),
                    "side-channel UPDATE must affect exactly the admin row");
            c.commit();
        }

        // Now the cache's lastChanged disagrees with the DB. The conditional
        // DELETE in dbStore() will match 0 rows; the guard must surface that
        // as RaplaNewVersionException instead of the original PK violation.
        Throwable thrown = assertThrows(Exception.class,
                () -> facade.store(editable));
        Throwable cursor = thrown;
        boolean found = false;
        while (cursor != null)
        {
            if (cursor instanceof RaplaNewVersionException)
            {
                found = true;
                break;
            }
            cursor = cursor.getCause();
        }
        assertTrue(found,
                "expected RaplaNewVersionException in the cause chain, got: " + thrown);
    }

    /**
     * PRD 054 (2026-05-25 user report) — after a successful store, the
     * server's response delta must include the just-saved reservation so the
     * client's local cache updates. Symptom: edit/move appeared to succeed on
     * the wire but the Swing client kept showing the pre-move state until a
     * full server restart.
     *
     * <p>Root cause was in {@link org.rapla.storage.dbsql.RaplaSQL.HistoryStorage#load}:
     * the CHANGES.CHANGED_AT column is written via {@code Timestamp.valueOf(LDT)}
     * but was read back via {@code LocalDateTime.ofInstant(Instant.ofEpochMilli(getTime()), UTC)}.
     * Those two paths are NOT inverses when the JVM zone ≠ UTC — read gave
     * LDT − offset, putting every fresh in-memory history entry's timestamp
     * ~2 h in the past. {@code addToDeleteUpdate(historyEntry)} then filed
     * the entry under that past timestamp in {@code deleteUpdateSet}, and
     * {@code getEntities(user, lastSynced)} (driving the response delta)
     * skipped it. Fix: {@code rs.getTimestamp(...).toLocalDateTime()} — the
     * actual inverse of {@code Timestamp.valueOf}.
     *
     * <p>This test stores a reservation, then asks the operator for the
     * post-store update result with {@code since = pre-store timestamp},
     * and asserts the reservation appears as a Change/Add. Without the fix
     * the result is empty.
     */
    @Test
    @DisplayName("getUpdateResult since pre-store includes the just-stored reservation")
    void postStoreResponseDeltaCarriesTheChange() throws Exception
    {
        User admin = getAdmin();
        DynamicType eventType = facade.getDynamicTypes(
                DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION)[0];
        Allocatable room = findFirstByTypeKey("room");
        assertNotNull(room);

        // Snapshot lastRefreshed BEFORE storing — this is what a client would
        // have most recently received as "lastValidated" from any prior call.
        LocalDateTime since = operator.getLastRefreshed();

        Reservation r = facade.newReservation(eventType.newClassification(), admin);
        Appointment a = facade.newAppointmentWithUser(
                LocalDateTime.of(2026, 7, 1, 9, 0),
                LocalDateTime.of(2026, 7, 1, 10, 0), admin);
        r.addAppointment(a);
        r.addAllocatable(room);
        facade.store(r);

        org.rapla.storage.UpdateResult result = operator.getUpdateResult(since, admin);

        boolean found = false;
        for (org.rapla.storage.UpdateOperation op : result.getOperations())
        {
            if (op.getReference().equals(r.getReference()))
            {
                found = true;
                break;
            }
        }
        assertTrue(found,
                "post-store UpdateResult (since=" + since + ") must contain the just-stored reservation "
                        + r.getReference() + ". Empty/missing means HistoryStorage.load read CHANGED_AT "
                        + "into the in-memory history with the wrong LDT (off by JVM-local-UTC offset), "
                        + "so deleteUpdateSet entries land in the past and getEntities(...) skips them.");
    }

    // ---------- helpers ----------

    private static <T> T waitFor(org.rapla.scheduler.Promise<T> promise) throws Exception
    {
        final java.util.concurrent.atomic.AtomicReference<T> result = new java.util.concurrent.atomic.AtomicReference<>();
        final java.util.concurrent.atomic.AtomicReference<Throwable> err = new java.util.concurrent.atomic.AtomicReference<>();
        final java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
        promise.thenAccept(value -> { result.set(value); done.countDown(); })
                .exceptionally(throwable -> { err.set(throwable); done.countDown(); });
        if (!done.await(5, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("Promise timeout");
        if (err.get() != null) throw new RuntimeException(err.get());
        return result.get();
    }

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
