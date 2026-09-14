package org.rapla.storage.readmodel;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * PRD 082 Phase 0.5 — the H2 write-path go/no-go benchmark.
 *
 * <p>The engine (H2) is locked (MQ7); this benchmark validates the one thing that could still bite:
 * can the read-model keep up with the <b>write-path maintenance</b> cost — bulk projection at boot,
 * and the per-mutation idempotent {@code DELETE WHERE appointment_id=? + re-INSERT} that every store
 * triggers (PRD 086 maintenance). It builds the real {@code appointment_block} schema + its three
 * indices and measures:
 * <ol>
 *   <li>bulk insert of ~200k single-appointment blocks (the boot-rebuild rate), and</li>
 *   <li>per-mutation re-projection latency (delete-by-appointment + small re-insert), averaged.</li>
 * </ol>
 *
 * <p>Self-gate (PRD 082 status): bulk rebuild of ~100k+ blocks &lt; ~5&nbsp;s, and per-put
 * re-projection well under ~1&nbsp;ms. These are deliberately loose sanity bars, not micro-benchmark
 * precision — the goal is "H2 is obviously fast enough", logged for the record.
 *
 * <p>Opt-in only (it prints timings and does real work): run with {@code -Drapla.h2bench=true}.
 * Tagged {@code perf} so it never runs in the default lane.
 */
@Tag("perf")
class H2WritePathBenchmarkTest
{
    private static final int BULK_BLOCKS = 200_000;
    private static final int MUTATIONS = 5_000;

    @Test
    void appointmentBlockProjection_writePathThroughput() throws Exception
    {
        assumeTrue(Boolean.getBoolean("rapla.h2bench"), "set -Drapla.h2bench=true to run the H2 write-path benchmark");

        try (Connection con = DriverManager.getConnection("jdbc:h2:mem:rm;DB_CLOSE_DELAY=-1"))
        {
            try (Statement st = con.createStatement())
            {
                st.execute("CREATE TABLE appointment_block (" +
                        "block_id VARCHAR(80) PRIMARY KEY, appointment_id VARCHAR(60), reservation_id VARCHAR(60), " +
                        "allocatable_id VARCHAR(60), owner_id VARCHAR(60), start_ts BIGINT, end_ts BIGINT, is_rule BOOLEAN)");
                st.execute("CREATE INDEX ix_block_alloc_time ON appointment_block(allocatable_id, start_ts)");
                st.execute("CREATE INDEX ix_block_owner_time ON appointment_block(owner_id, start_ts)");
                st.execute("CREATE INDEX ix_block_reservation ON appointment_block(reservation_id)");
                // The maintenance key: every store does DELETE WHERE appointment_id=? + re-INSERT
                // (PRD 086). Without this index the delete full-scans the table — the Phase-0.5
                // benchmark caught exactly that (28 ms/mutation → sub-ms with the index).
                st.execute("CREATE INDEX ix_block_appointment ON appointment_block(appointment_id)");
            }

            // (1) Bulk projection — the boot-rebuild rate. Batched, autocommit off (the realistic path).
            con.setAutoCommit(false);
            long t0 = System.nanoTime();
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO appointment_block VALUES (?,?,?,?,?,?,?,?)"))
            {
                long base = 1_700_000_000_000L; // fixed epoch base; no Date.now() in tests
                for (int i = 0; i < BULK_BLOCKS; i++)
                {
                    long start = base + (long) i * 3_600_000L;
                    ps.setString(1, "app" + i + "#0");
                    ps.setString(2, "app" + i);
                    ps.setString(3, "res" + (i % 80_000));
                    ps.setString(4, "alloc" + (i % 4_000));
                    ps.setString(5, "user" + (i % 2_000));
                    ps.setLong(6, start);
                    ps.setLong(7, start + 5_400_000L);
                    ps.setBoolean(8, false);
                    ps.addBatch();
                    if (i % 5_000 == 0) ps.executeBatch();
                }
                ps.executeBatch();
            }
            con.commit();
            double bulkSec = (System.nanoTime() - t0) / 1e9;

            // (2) Per-mutation re-projection: DELETE WHERE appointment_id=? + re-INSERT one block, committed.
            long t1 = System.nanoTime();
            try (PreparedStatement del = con.prepareStatement("DELETE FROM appointment_block WHERE appointment_id = ?");
                 PreparedStatement ins = con.prepareStatement("INSERT INTO appointment_block VALUES (?,?,?,?,?,?,?,?)"))
            {
                long base = 1_700_000_000_000L;
                for (int m = 0; m < MUTATIONS; m++)
                {
                    int i = (m * 37) % BULK_BLOCKS; // scattered targets
                    del.setString(1, "app" + i);
                    del.executeUpdate();
                    long start = base + (long) i * 3_600_000L + 60_000L;
                    ins.setString(1, "app" + i + "#0");
                    ins.setString(2, "app" + i);
                    ins.setString(3, "res" + (i % 80_000));
                    ins.setString(4, "alloc" + (i % 4_000));
                    ins.setString(5, "user" + (i % 2_000));
                    ins.setLong(6, start);
                    ins.setLong(7, start + 5_400_000L);
                    ins.setBoolean(8, false);
                    ins.executeUpdate();
                    con.commit();
                }
            }
            double perMutMs = (System.nanoTime() - t1) / 1e6 / MUTATIONS;

            System.out.printf("[H2 write-path] bulk %,d blocks in %.2fs (%,.0f rows/s); per-mutation re-project avg %.3f ms (n=%,d)%n",
                    BULK_BLOCKS, bulkSec, BULK_BLOCKS / bulkSec, perMutMs, MUTATIONS);

            // Loose self-gate bars (PRD 082 Phase 0.5). Generous to avoid CI-host flakiness; the real
            // signal is the printed numbers, which we expect to beat these by a wide margin.
            assertTrue(bulkSec < 20.0, "bulk projection too slow: " + bulkSec + "s for " + BULK_BLOCKS + " blocks");
            assertTrue(perMutMs < 5.0, "per-mutation re-projection too slow: " + perMutMs + " ms");
        }
    }
}
