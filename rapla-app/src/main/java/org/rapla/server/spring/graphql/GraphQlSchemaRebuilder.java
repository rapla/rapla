package org.rapla.server.spring.graphql;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * PRD 035 Cut C — polls {@link HotSwappableGraphQlSource#rebuild()} on a
 * scheduled cadence to pick up DynamicType admin changes without restart.
 *
 * <p>Why polling and not a listener: rapla's storage layer doesn't expose a
 * server-side {@code StorageUpdateListener} (only the client-side
 * {@code RemoteOperator} does — see {@code AbstractCachableOperator.update}
 * at line 784, which is the in-memory cache update chokepoint but fires no
 * external events). Wiring a real listener would mean either extending
 * {@code AbstractCachableOperator} or adding a new SPI to
 * {@code StorageOperator} — both bigger than the testbed scope. Polling
 * + hash-short-circuit gets us within the ~10s of latency rapla already
 * has for every other DynamicType read path across pods (the existing
 * UpdateEvent polling interval), with O(1) work when nothing changes.
 *
 * <p>The 10s cadence matches rapla's existing multi-pod cache-coherence
 * poll, so single-pod admin changes propagate to the schema in under a
 * second (one poll tick), and multi-pod scenarios see the same ~10s skew
 * they already do for DynamicType reads.
 *
 * <p>If/when a real server-side listener SPI is added (its own PRD —
 * structurally the listener path is the right answer; this is the v1
 * implementation), this component can be deleted in favor of a callback.
 */
@Component
public class GraphQlSchemaRebuilder
{
    private static final Logger LOGGER = LoggerFactory.getLogger(GraphQlSchemaRebuilder.class);

    /** Match rapla's existing cross-pod UpdateEvent poll interval (10s). */
    static final long POLL_INTERVAL_MS = 10_000L;

    private final HotSwappableGraphQlSource source;

    public GraphQlSchemaRebuilder(HotSwappableGraphQlSource source)
    {
        this.source = source;
    }

    @Scheduled(fixedDelay = POLL_INTERVAL_MS, initialDelay = POLL_INTERVAL_MS)
    public void checkAndRebuild()
    {
        try
        {
            boolean rebuilt = source.rebuild();
            if (rebuilt) LOGGER.debug("GraphQL schema picked up DynamicType changes");
        }
        catch (Exception e)
        {
            LOGGER.warn("GraphQL schema rebuild failed; keeping previous schema", e);
        }
    }
}
