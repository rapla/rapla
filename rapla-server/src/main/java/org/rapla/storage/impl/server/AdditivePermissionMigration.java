package org.rapla.storage.impl.server;

import org.rapla.entities.Entity;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.configuration.internal.PreferencesImpl;
import org.rapla.entities.domain.Allocatable;
import org.rapla.framework.RaplaException;
import org.rapla.storage.impl.AbstractCachableOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * PRD 090 — one-shot startup migration to the purely additive permission model.
 *
 * <p>The live resolver is already additive (the flip is the code deploy, not this
 * one-shot — D3 Option A). This pass only computes, once, the <b>frozen worklist</b>
 * of allocatable ids whose effective access rose at the flip (some principal gained
 * access because a soft deny stopped biting), so an admin can review them. New
 * soft-denies created <em>after</em> the flip are never added (the set is frozen),
 * because under additive nobody can be capped, so a post-flip "soft deny" never
 * removed access from anyone.
 *
 * <p>Marker-guarded and lock-held exactly like {@link GraphqlKeyMigration}. The
 * precedence-vs-additive comparison lives entirely in {@link SoftDenyAnalyzer}.
 */
final class AdditivePermissionMigration
{
    private static final Logger LOGGER = LoggerFactory.getLogger(AdditivePermissionMigration.class);

    private AdditivePermissionMigration() {}

    static boolean markerSet(AbstractCachableOperator op) throws RaplaException
    {
        return AdditiveMigrationState.markerSet(op.getPreferences(null, false));
    }

    /**
     * Compute + freeze the worklist under the caller's write lock. Returns a short
     * summary for logging.
     */
    static String runUnderLock(AbstractCachableOperator op,
                               Collection<Allocatable> allocatables,
                               Collection<User> users) throws RaplaException
    {
        LocalDateTime today = op.getCurrentTimestamp();
        Set<String> worklist = new LinkedHashSet<>();
        for (Allocatable a : allocatables)
        {
            if (!SoftDenyAnalyzer.mightHaveSoftDeny(a)) continue;
            if (!SoftDenyAnalyzer.findEscalations(a, users, today).isEmpty())
            {
                worklist.add(a.getId());
            }
        }

        Collection<Entity> toStore = new ArrayList<>();
        PreferencesImpl editable = editableSystemPrefs(op, toStore);
        editable.putEntry(AdditiveMigrationState.WORKLIST_KEY, AdditiveMigrationState.join(worklist));
        editable.putEntry(AdditiveMigrationState.MARKER_KEY, Instant.now().toString());
        op.storeAndRemove(toStore, Collections.emptyList(), null);

        // The count the operator logs at INFO — resources where a silent deny/cap
        // was removing read (or more) access from at least one user.
        LOGGER.info("PRD 090 — additive permission flip: {} resource(s) had a silent deny/cap "
                + "removed (worklist frozen for admin review)", worklist.size());
        return worklist.size() + " resource(s) in the additive migration worklist";
    }

    private static PreferencesImpl editableSystemPrefs(AbstractCachableOperator op, Collection<Entity> toStore) throws RaplaException
    {
        Preferences sysPrefs = op.getPreferences(null, true); // create if missing
        PreferencesImpl editable = (PreferencesImpl) op.editObject((Entity) sysPrefs, null);
        toStore.add(editable);
        return editable;
    }
}
