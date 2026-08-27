package org.rapla.plugin.externaleventimport.server;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.rapla.test.util.FacadeTestSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Spike behind the DERIVED-binding decision (dhbwrapla PRD 004 OQ1): is deriving
 *  open-vs-bound at read time affordable at staging-store scale?
 *
 *  <p>{@code tryResolveExternalId} resolves against {@code externalIds}, an in-memory
 *  {@code TwoWayMap} (two HashMaps) built once in {@code initIndizes} — no query, no I/O. This
 *  test pins that property: if anyone turns it into a scan or a DB call, the numbers collapse
 *  and the no-write-coupling design has to be revisited. */
class DerivedBindingLookupSpikeTest extends FacadeTestSupport
{
    private static final Logger LOGGER = LoggerFactory.getLogger(DerivedBindingLookupSpikeTest.class);

    private static final int STAGING_SCALE = 30_000;

    @Test
    void derivingTheBindingForAWholeStagingStoreStaysInTheMillisecondRange()
    {
        final List<String> externalIds = new ArrayList<>(STAGING_SCALE);
        for (int i = 0; i < STAGING_SCALE; i++)
        {
            externalIds.add("Lehrveranstaltung:spike-" + i);
        }

        for (String warmup : externalIds.subList(0, 1000))
        {
            operator.tryResolveExternalId(warmup);
        }

        final long start = System.nanoTime();
        int bound = 0;
        for (String externalId : externalIds)
        {
            if (operator.tryResolveExternalId(externalId) != null)
            {
                bound++;
            }
        }
        final long millis = (System.nanoTime() - start) / 1_000_000L;

        LOGGER.info("Derived binding for " + STAGING_SCALE + " staged items took " + millis + " ms (" + bound
                + " bound)");
        assertThat(bound).isZero();
        assertThat(millis).isLessThan(500L);
    }
}
