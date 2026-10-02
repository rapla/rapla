package org.rapla.storage.impl.server;

import org.rapla.entities.Entity;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.configuration.internal.PreferencesImpl;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.framework.RaplaException;
import org.rapla.framework.TypedComponentRole;
import org.rapla.storage.impl.AbstractCachableOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

/**
 * PRD 090 Phase 6 — one-shot: the load-time normalizer (ADR 0003) drops non-load-bearing DENIED rows only
 * in memory; storing the affected entities once removes them from the store as well. A failed store leaves
 * the marker unset, so the next start retries; the in-memory normalization keeps working either way.
 */
final class RedundantDenyCleanup
{
    private static final Logger LOGGER = LoggerFactory.getLogger(RedundantDenyCleanup.class);
    static final TypedComponentRole<String> MARKER_KEY = new TypedComponentRole<>("org.rapla.server.redundant-deny-cleanup.applied");

    private RedundantDenyCleanup() {}

    static boolean markerSet(AbstractCachableOperator op) throws RaplaException
    {
        Preferences sysPrefs = op.getPreferences(null, false);
        return sysPrefs != null && sysPrefs.getEntryAsString(MARKER_KEY, null) != null;
    }

    static void runUnderLock(AbstractCachableOperator op, Collection<ReferenceInfo<Entity>> entities, int rows) throws RaplaException
    {
        Collection<Entity> toStore = new ArrayList<>();
        for (ReferenceInfo<Entity> ref : entities)
        {
            Entity entity = op.tryResolve(ref);
            if (entity != null)
            {
                toStore.add(op.editObject(entity, null));
            }
        }
        PreferencesImpl sysPrefs = (PreferencesImpl) op.editObject((Entity) op.getPreferences(null, true), null);
        sysPrefs.putEntry(MARKER_KEY, Instant.now().toString());
        toStore.add(sysPrefs);
        try
        {
            op.storeAndRemove(toStore, Collections.emptyList(), null);
        }
        catch (RaplaException e)
        {
            LOGGER.error("PRD 090 Phase 6 — could not persist the removal of {} redundant DENIED row(s) in {} entit(ies); retrying on next start", rows, toStore.size() - 1, e);
            return;
        }
        LOGGER.info("PRD 090 Phase 6 — persisted the removal of {} redundant DENIED row(s) by storing {} entit(ies)", rows, toStore.size() - 1);
    }
}
