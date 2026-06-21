package org.rapla.server;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

/**
 * The fixed API-key scope vocabulary and its capability rules (PRD 076).
 *
 * <p>Two axes (D2/D3):
 * <ul>
 *   <li><b>Data</b> — {@link #READ}, {@link #WRITE_EVENTS}, {@link #WRITE_RESOURCES},
 *       {@link #WRITE_ALL}: what the key may read / mutate. {@code write_*} implies read.</li>
 *   <li><b>Management</b> — {@link #ROTATE_SELF}: may the key rotate itself. Orthogonal to the
 *       data axis; grants no data-write power.</li>
 * </ul>
 *
 * <p>Two defaults that must not be confused (the source of the only backward-compat hazard):
 * <ul>
 *   <li>{@link #normaliseForNewKey} — a NEWLY created key with no scopes requested defaults to
 *       least-privilege {@code {read}} (D5).</li>
 *   <li>{@link #resolveStored} — an EXISTING stored key entry with NO {@code scopes} field
 *       (written before this PRD) resolves to {@code {write_all}} — behaviour-identical to
 *       today's full-power keys (D8). A missing field must NEVER be read as {@code {read}}.</li>
 * </ul>
 */
public final class ApiKeyScopes
{
    public static final String READ = "read";
    public static final String WRITE_EVENTS = "write_events";
    public static final String WRITE_RESOURCES = "write_resources";
    public static final String WRITE_ALL = "write_all";
    public static final String ROTATE_SELF = "rotate_self";

    public static final Set<String> VOCABULARY =
            Set.of(READ, WRITE_EVENTS, WRITE_RESOURCES, WRITE_ALL, ROTATE_SELF);

    /** Least-privilege default for a brand-new key (D5). */
    public static final Set<String> DEFAULT_NEW_KEY = Set.of(READ);

    /** Backward-compat default for a stored entry with no {@code scopes} field (D8). */
    public static final Set<String> LEGACY_FULL = Set.of(WRITE_ALL);

    private ApiKeyScopes()
    {
    }

    /**
     * Validates + normalises the scopes requested when CREATING a key. {@code null}/empty ⇒
     * {@link #DEFAULT_NEW_KEY} (least privilege). Any token outside {@link #VOCABULARY} throws
     * {@link IllegalArgumentException} (mapped to HTTP 400 by {@code RaplaExceptionHandler}).
     */
    public static Set<String> normaliseForNewKey(Collection<String> requested)
    {
        if (requested == null || requested.isEmpty())
        {
            return DEFAULT_NEW_KEY;
        }
        // TreeSet ⇒ deterministic (alphabetical) iteration order, so the serialised scopes array
        // is stable across JVM runs (Set.copyOf's order is salted/randomised — caused test flake).
        Set<String> out = new TreeSet<>();
        for (String s : requested)
        {
            String scope = s == null ? null : s.trim();
            if (scope == null || scope.isEmpty() || !VOCABULARY.contains(scope))
            {
                throw new IllegalArgumentException("unknown api-key scope: " + s);
            }
            out.add(scope);
        }
        return Collections.unmodifiableSet(out);
    }

    /**
     * Resolves the scope set of a STORED key entry. A {@code null}/empty stored set means the
     * entry predates this PRD ⇒ {@link #LEGACY_FULL} (D8, NOT {@code read}). Otherwise the
     * stored set is taken verbatim (already validated at create time).
     */
    public static Set<String> resolveStored(Collection<String> stored)
    {
        if (stored == null || stored.isEmpty())
        {
            return LEGACY_FULL;
        }
        return Collections.unmodifiableSet(new TreeSet<>(stored));
    }

    public static boolean canWriteEvents(Collection<String> scopes)
    {
        return scopes != null && (scopes.contains(WRITE_EVENTS) || scopes.contains(WRITE_ALL));
    }

    public static boolean canWriteResources(Collection<String> scopes)
    {
        return scopes != null && (scopes.contains(WRITE_RESOURCES) || scopes.contains(WRITE_ALL));
    }

    public static boolean canRotateSelf(Collection<String> scopes)
    {
        return scopes != null && scopes.contains(ROTATE_SELF);
    }
}
