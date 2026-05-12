package org.rapla.storage.dbrm;

import org.rapla.logger.Logger;

import java.util.Optional;

/**
 * Factory for the right {@link TokenStore} backend at runtime:
 *
 * <ol>
 *   <li>JNLP {@code PersistenceService} (when launched under OpenWebStart
 *       or IcedTea-Web — sandbox-scoped to the rapla codebase URL)</li>
 *   <li>{@link FileTokenStore} ({@code ~/.rapla/tokens.json}, 0600) —
 *       the universal fallback</li>
 *   <li>{@link #noOp()} — silent black hole if both fail (e.g. read-only
 *       home plus no JNLP, like inside a tight container with no writable
 *       paths). The OAuth flow runs every launch as if persistence didn't
 *       exist; the app still works.</li>
 * </ol>
 */
public final class TokenStores
{
    private TokenStores() {}

    public static TokenStore create(Logger logger)
    {
        Optional<TokenStore> jnlp = JnlpTokenStore.tryCreate(logger);
        if (jnlp.isPresent())
        {
            if (logger != null) logger.info("token store: JNLP PersistenceService");
            return jnlp.get();
        }
        try
        {
            FileTokenStore file = new FileTokenStore(logger);
            if (logger != null) logger.info("token store: file (~/.rapla/tokens.json)");
            return file;
        }
        catch (Throwable t)
        {
            if (logger != null) logger.warn("token store: no backend available; persistence disabled");
            return noOp();
        }
    }

    public static TokenStore noOp()
    {
        return new TokenStore()
        {
            @Override public Optional<String> read() { return Optional.empty(); }
            @Override public void tryWrite(String token) { /* no-op */ }
            @Override public void tryClear() { /* no-op */ }
        };
    }
}
