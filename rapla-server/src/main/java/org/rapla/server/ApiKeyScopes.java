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
 *   <li><b>Data</b> — {@link #READ} (baseline, no sensitive expansions), {@link #ACCESS_DETAILS}
 *       (sensitive identity / permission expansions), {@link #WRITE_EVENTS}, {@link #WRITE_RESOURCES},
 *       {@link #WRITE_ALL}: what the key may read / mutate. {@code write_*} implies read;
 *       {@code write_all} implies access_details.</li>
 *   <li><b>Management</b> — {@link #ROTATE_SELF}: may the key rotate itself. Orthogonal to the
 *       data axis; grants no data-write power.</li>
 * </ul>
 *
 * <p>No-scopes default is uniform: both a newly created key ({@link #normaliseForNewKey}) and a
 * stored entry with no {@code scopes} field ({@link #resolveStored}) resolve to least-privilege
 * {@link #DEFAULT} ({@code {read}}). The earlier "legacy stored entry ⇒ {@code write_all}"
 * fallback is gone, so pre-scopes keys are now read-only; the one that writes (dualis) is exempted
 * at its endpoint via {@code ApiKeyScopeContext.callUnrestricted}.
 */
public final class ApiKeyScopes
{
    public static final String READ = "read";
    /**
     * Sensitive identity / permission-structure expansions — user PII ({@code User.groups},
     * {@code email}, {@code isAdmin}, {@code authSource}) and (future) resource permission lists.
     * One generalized scope for both categories (PRD 076 Phase 5); enforced field-level at the
     * GraphQL seam via a {@code @requiresAccessDetails} directive.
     */
    public static final String ACCESS_DETAILS = "access_details";
    public static final String WRITE_EVENTS = "write_events";
    public static final String WRITE_RESOURCES = "write_resources";
    public static final String WRITE_ALL = "write_all";
    public static final String ROTATE_SELF = "rotate_self";

    public static final Set<String> VOCABULARY =
            Set.of(READ, ACCESS_DETAILS, WRITE_EVENTS, WRITE_RESOURCES, WRITE_ALL, ROTATE_SELF);

    /**
     * Least-privilege default for a key with no scopes — both a brand-new key (create) and a
     * stored entry with no {@code scopes} field (verify). One rule, no legacy special-case: empty
     * ⇒ {@code {read}}. The single pre-scopes key that writes (dualis) is exempted at its own
     * endpoint via {@code ApiKeyScopeContext.callUnrestricted}, not by a stored-default fallback.
     */
    public static final Set<String> DEFAULT = Set.of(READ);

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
            return DEFAULT;
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
        // Every created key carries read explicitly — the guaranteed floor / "stand" of a token.
        // {@code write_*} implies read at the capability level anyway; making it explicit means a
        // stored scopes array is never write-only and read is always present by construction.
        out.add(READ);
        return Collections.unmodifiableSet(out);
    }

    /**
     * Resolves the scope set of a STORED key entry. A {@code null}/empty stored set ⇒
     * {@link #DEFAULT} ({@code {read}}) — least privilege, no legacy {@code write_all} fallback.
     * Otherwise the stored set is taken verbatim (already validated at create time).
     */
    public static Set<String> resolveStored(Collection<String> stored)
    {
        if (stored == null || stored.isEmpty())
        {
            return DEFAULT;
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

    /**
     * May the key expand sensitive identity / permission details — user PII ({@code User.groups},
     * {@code email}, {@code isAdmin}, {@code authSource}) and (future) resource permission lists?
     * Gated by {@link #ACCESS_DETAILS}; {@link #WRITE_ALL} (full power) implies it. Plain
     * {@link #READ} and the data-write scopes do NOT — sensitive expansions are opt-in.
     */
    public static boolean hasAccessDetails(Collection<String> scopes)
    {
        return scopes != null && (scopes.contains(ACCESS_DETAILS) || scopes.contains(WRITE_ALL));
    }
}
