package org.rapla.storage.impl.server;

import org.rapla.entities.configuration.Preferences;
import org.rapla.framework.TypedComponentRole;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * PRD 090 — system-preference state for the additive-permission migration
 * (OQ3 resolved 2026-06-28: the worklist lives in the server system preference).
 *
 * <ul>
 *   <li>{@link #MARKER_KEY} — written once by the one-shot so later boots skip it.</li>
 *   <li>{@link #WORKLIST_KEY} — the frozen set of allocatable ids that had a real
 *       escalation at flip time; written once, never recomputed.</li>
 *   <li>{@link #ACK_KEY} — allocatable ids an admin has acknowledged; grows as the
 *       worklist is drained.</li>
 * </ul>
 *
 * Ids are stored comma-joined in a single string entry (rapla ids contain no
 * commas). Display data (principals, levels, text) is never stored — it is
 * recomputed from live permissions (AGENTS.md §17).
 */
public final class AdditiveMigrationState
{
    public static final TypedComponentRole<String> MARKER_KEY =
            new TypedComponentRole<>("org.rapla.server.additive-permission-migration.applied");
    public static final TypedComponentRole<String> WORKLIST_KEY =
            new TypedComponentRole<>("org.rapla.server.additive-permission-migration.worklist");
    public static final TypedComponentRole<String> ACK_KEY =
            new TypedComponentRole<>("org.rapla.server.additive-permission-migration.acknowledged");

    private AdditiveMigrationState() {}

    public static boolean markerSet(Preferences sysPrefs)
    {
        return sysPrefs != null && sysPrefs.getEntryAsString(MARKER_KEY, null) != null;
    }

    /** The frozen worklist allocatable ids (insertion order preserved). */
    public static Set<String> readWorklist(Preferences sysPrefs)
    {
        return parse(sysPrefs == null ? null : sysPrefs.getEntryAsString(WORKLIST_KEY, null));
    }

    /** The acknowledged allocatable ids. */
    public static Set<String> readAcknowledged(Preferences sysPrefs)
    {
        return parse(sysPrefs == null ? null : sysPrefs.getEntryAsString(ACK_KEY, null));
    }

    public static String join(Set<String> ids)
    {
        return String.join(",", ids);
    }

    private static Set<String> parse(String joined)
    {
        Set<String> result = new LinkedHashSet<>();
        if (joined == null) return result;
        for (String id : joined.split(","))
        {
            String trimmed = id.trim();
            if (!trimmed.isEmpty()) result.add(trimmed);
        }
        return result;
    }
}
