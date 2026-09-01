package org.rapla.storage.dbsql;

import org.hsqldb.jdbc.JDBCDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PRD 108 follow-up — {@code getTimestamp(rset, column, false)} must not need a
 * connection timestamp.
 *
 * <p>{@code DBOperator}'s daily history-cleanup job deliberately runs with
 * {@code history.setConnection(con, null)} ({@code RaplaSQL:632}), so
 * {@code getConnectionTimestamp()} is null on that storage instance. The helper
 * evaluated {@code date.isAfter(currentTimestamp) && checkCurrent} — {@code isAfter}
 * runs first and dereferences the null even when the caller asked for no check,
 * so the whole cleanup died with an NPE once per boot and {@code CHANGES} grew
 * without bound.
 */
@Tag("db")
class AbstractTableStorageTimestampTest
{
    private static final String[] COLUMNS = { "ID VARCHAR(255) KEY", "CHANGED_AT TIMESTAMP" };

    @TempDir
    Path tempDir;

    private Connection con;
    private AbstractTableStorage storage;

    @BeforeEach
    void setUp() throws Exception
    {
        JDBCDataSource dataSource = new JDBCDataSource();
        dataSource.setUrl("jdbc:hsqldb:" + tempDir.resolve("ts-" + UUID.randomUUID()).toAbsolutePath()
                + ";shutdown=true");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        con = dataSource.getConnection();
        try (Statement stmt = con.createStatement())
        {
            stmt.executeUpdate("CREATE TABLE STAMPS (ID VARCHAR(255) PRIMARY KEY, CHANGED_AT TIMESTAMP)");
        }
        try (PreparedStatement stmt = con.prepareStatement("INSERT INTO STAMPS VALUES (?, ?)"))
        {
            stmt.setString(1, "row-with-stamp");
            stmt.setTimestamp(2, Timestamp.valueOf(LocalDateTime.of(2026, 8, 28, 15, 11, 6)));
            stmt.executeUpdate();
            stmt.setString(1, "row-without-stamp");
            stmt.setNull(2, java.sql.Types.TIMESTAMP);
            stmt.executeUpdate();
        }
        con.commit();

        storage = new AbstractTableStorage("STAMPS", COLUMNS, false);
        // Exactly how RaplaSQL.cleanupHistory(con, date) sets the history storage up.
        storage.setConnection(con, null);
    }

    @AfterEach
    void tearDown() throws Exception
    {
        if (con != null && !con.isClosed())
        {
            try (Statement stmt = con.createStatement()) { stmt.executeUpdate("SHUTDOWN"); }
            con.close();
        }
    }

    @Test
    @DisplayName("checkCurrent=false reads the stamp without a connection timestamp")
    void readsTimestampWithoutConnectionTimestamp() throws Exception
    {
        assertNull(storage.getConnectionTimestamp(), "precondition: the cleanup job passes a null timestamp");
        LocalDateTime read = selectStamp("row-with-stamp");
        assertNotNull(read, "getTimestamp(.., false) must return the stored value, not blow up on the missing "
                + "connection timestamp — the daily history cleanup runs exactly this way");
        assertTrue(read.getHour() >= 0);
    }

    @Test
    @DisplayName("checkCurrent=false still returns null for a NULL column")
    void returnsNullForNullColumn() throws Exception
    {
        assertNull(selectStamp("row-without-stamp"));
    }

    private LocalDateTime selectStamp(String id) throws Exception
    {
        try (PreparedStatement stmt = con.prepareStatement("SELECT CHANGED_AT FROM STAMPS WHERE ID = ?"))
        {
            stmt.setString(1, id);
            try (ResultSet rs = stmt.executeQuery())
            {
                assertTrue(rs.next());
                return storage.getTimestamp(rs, 1, false);
            }
        }
    }
}
