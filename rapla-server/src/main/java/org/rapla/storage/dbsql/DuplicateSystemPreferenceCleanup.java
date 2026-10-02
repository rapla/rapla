package org.rapla.storage.dbsql;

import org.rapla.entities.Entity;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.configuration.internal.PreferencesImpl;
import org.rapla.entities.RaplaObject;
import org.rapla.framework.RaplaException;
import org.rapla.framework.TypedComponentRole;
import org.rapla.storage.impl.AbstractCachableOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * WP17/F4 — until F1 every save of a system preference appended a row (the patch delete compared USER_ID = NULL),
 * so old databases hold several rows per system role. This one-shot rewrites each such role with its loaded value
 * (the newest row since F3) through the normal patch path, which now deletes every row of the role first.
 */
final class DuplicateSystemPreferenceCleanup
{
    private static final Logger LOGGER = LoggerFactory.getLogger(DuplicateSystemPreferenceCleanup.class);
    static final TypedComponentRole<String> MARKER_KEY = new TypedComponentRole<>("org.rapla.server.duplicate-system-preference-cleanup.applied");

    private DuplicateSystemPreferenceCleanup() {}

    static boolean markerSet(AbstractCachableOperator op) throws RaplaException
    {
        Preferences sysPrefs = op.getPreferences(null, false);
        return sysPrefs != null && sysPrefs.getEntryAsString(MARKER_KEY, null) != null;
    }

    static Map<String, Integer> findDuplicates(Connection connection) throws SQLException
    {
        Map<String, Integer> duplicates = new TreeMap<>();
        try (PreparedStatement stmt = connection.prepareStatement("SELECT ROLE, COUNT(*) FROM PREFERENCE WHERE USER_ID IS NULL GROUP BY ROLE HAVING COUNT(*) > 1");
             ResultSet rs = stmt.executeQuery())
        {
            while (rs.next())
            {
                duplicates.put(rs.getString(1), rs.getInt(2));
            }
        }
        return duplicates;
    }

    static void runUnderLock(AbstractCachableOperator op, Map<String, Integer> duplicates) throws RaplaException
    {
        PreferencesImpl sysPrefs = (PreferencesImpl) op.editObject((Entity) op.getPreferences(null, true), null);
        for (String role : duplicates.keySet())
        {
            Object value = sysPrefs.getEntry(role);
            if (value instanceof RaplaObject)
            {
                sysPrefs.putEntryPrivate(role, (RaplaObject) value);
            }
            else
            {
                sysPrefs.putEntryPrivate(role, (String) value);
            }
        }
        sysPrefs.putEntry(MARKER_KEY, Instant.now().toString());
        op.storeAndRemove(List.of(sysPrefs), Collections.emptyList(), null);
        int rows = duplicates.values().stream().mapToInt(Integer::intValue).sum();
        LOGGER.info("WP17 — rewrote {} system preference role(s) stored in {} row(s), one row each now: {}", duplicates.size(), rows, duplicates.keySet());
    }
}
