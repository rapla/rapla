package org.rapla.server;

import org.rapla.storage.RaplaSecurityException;

import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

/**
 * Bridges the current request's API-key scope set to the storage layer (PRD 076 Phase 2).
 *
 * <p>The operator write chokepoint ({@code LocalAbstractCachableOperator.check}) is the single
 * place both REST and GraphQL mutations converge (D6), but it is storage-layer and must stay
 * Spring-free. So instead of reading {@code SecurityContextHolder} directly it consults a
 * {@link Supplier} registered at startup by the Spring tier ({@code ApiKeyScopeContextInitializer}),
 * which returns the scopes of the authenticated api-key principal — or {@code null} when the
 * caller is NOT a scoped api-key (interactive session, internal/background thread). {@code null}
 * ⇒ no restriction.
 *
 * <p>{@link #callUnrestricted} suspends enforcement for a privileged management write that the
 * key is allowed to make regardless of its data scopes — specifically a {@code rotate_self} key
 * persisting its successor key (which lands in the user's Preferences and would otherwise be
 * blocked as a non-event/non-resource write). The suppress flag is thread-local and restored in
 * a {@code finally}.
 */
public final class ApiKeyScopeContext
{
    private static volatile Supplier<Set<String>> source = () -> null;
    private static final ThreadLocal<Boolean> suppressed = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private ApiKeyScopeContext()
    {
    }

    /** Registers the scope source (Spring tier). {@code null} resets to "always unrestricted". */
    public static void setSource(Supplier<Set<String>> newSource)
    {
        source = (newSource == null) ? () -> null : newSource;
    }

    /**
     * The current request's api-key scopes, or {@code null} when the caller is not a scoped
     * api-key (⇒ unrestricted) or enforcement is suspended via {@link #callUnrestricted}.
     */
    public static Set<String> current()
    {
        if (Boolean.TRUE.equals(suppressed.get()))
        {
            return null;
        }
        return source.get();
    }

    /**
     * Explicit scope gate for a privileged operation that <b>bypasses the storage write
     * chokepoint</b> ({@code LocalAbstractCachableOperator.guardApiKeyScopes}) and therefore is
     * NOT covered by the automatic per-entity enforcement — e.g. bulk import/export/restore via
     * {@code ImportExportManager.saveData}, raw JDBC, or file writes that emit no
     * {@code UpdateEvent}. Such call sites must invoke this by hand. A non-api-key caller
     * ({@link #current()} {@code == null}) passes unchanged; a scoped api-key must hold
     * {@code write_all}, since these operations touch arbitrary entity types wholesale.
     *
     * @param operation short description for the error message (e.g. {@code "archiver restore"})
     */
    public static void requireWriteAllForBulk(String operation) throws RaplaSecurityException
    {
        Set<String> scopes = current();
        if (scopes != null && !scopes.contains(ApiKeyScopes.WRITE_ALL))
        {
            throw new RaplaSecurityException("api key scope does not permit " + operation);
        }
    }

    /**
     * Rejects api-key principals outright — for reads that surface server-side secrets or admin
     * config (SMTP/LDAP/Exchange credentials, plugin config) that only an <b>interactive user
     * session</b> may see. No api-key data scope is enough; the material is simply off-limits to
     * api-keys regardless of scope. A non-api-key caller ({@link #current()} {@code == null} —
     * interactive session or internal thread) passes unchanged.
     *
     * @param operation short description for the error message (e.g. {@code "mail config"})
     */
    public static void requireInteractiveSession(String operation) throws RaplaSecurityException
    {
        if (current() != null)
        {
            throw new RaplaSecurityException("api keys may not read " + operation);
        }
    }

    /** Runs {@code action} with scope enforcement suspended on this thread (privileged write). */
    public static <T> T callUnrestricted(Callable<T> action) throws Exception
    {
        Boolean prev = suppressed.get();
        suppressed.set(Boolean.TRUE);
        try
        {
            return action.call();
        }
        finally
        {
            suppressed.set(prev);
        }
    }
}
