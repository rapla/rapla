package org.rapla.server.internal;

import org.rapla.framework.RaplaException;
import org.rapla.storage.CachableStorageOperator;

/**
 * PRD 048: a client-triggered logical restart. Reloads all data from the
 * store, clears and rebuilds the caches and re-arms the operator's scheduled
 * tasks &mdash; <em>without</em> restarting the JVM or the Spring context.
 *
 * <p>Reloads only the pod that served the request; other pods re-sync via the
 * update history regardless. Replaces the dead {@code ShutdownService} stub
 * whose {@code shutdown(true)} only ever threw {@code "Restart not
 * implemented"}.
 */
public class ReloadService
{
    private final CachableStorageOperator operator;

    public ReloadService(CachableStorageOperator operator)
    {
        this.operator = operator;
    }

    public void reload() throws RaplaException
    {
        operator.reload();
    }
}
