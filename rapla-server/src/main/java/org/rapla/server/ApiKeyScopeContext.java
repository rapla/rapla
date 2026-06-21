package org.rapla.server;

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
