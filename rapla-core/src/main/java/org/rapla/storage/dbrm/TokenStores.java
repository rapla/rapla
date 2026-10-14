package org.rapla.storage.dbrm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Factory for the right {@link TokenStore} backend at runtime:
 *
 * <ol>
 *   <li>{@link FileTokenStore} ({@code ~/.rapla/tokens.json}, owner-only,
 *       one entry per server origin)</li>
 *   <li>{@link #noOp()} — silent black hole if that fails (e.g. read-only
 *       home, like inside a tight container with no writable paths). The OAuth
 *       flow runs every launch as if persistence didn't exist; the app still
 *       works.</li>
 * </ol>
 */
public final class TokenStores
{
    private static final Logger LOGGER = LoggerFactory.getLogger(TokenStores.class);

    private TokenStores() {}

    /** @param serverUrl the URL of the server the client talks to, read on every call (its origin keys the stored token) */
    public static TokenStore create(Supplier<String> serverUrl)
    {
        try
        {
            FileTokenStore file = new FileTokenStore(serverUrl);
            LOGGER.info("token store: file ({})", file.path());
            return new ConsentingTokenStore(file, "file");
        }
        catch (Throwable t)
        {
            LOGGER.warn("token store: no backend available; persistence disabled");
            return new ConsentingTokenStore(noOp(), "none");
        }
    }

    public static TokenStore noOp()
    {
        return new TokenStore()
        {
            @Override public Optional<String> read() { return Optional.empty(); }
            @Override public void tryWrite(String token) { /* no-op */ }
            @Override public void tryClear() { /* no-op */ }
            @Override public Optional<String> readPref(String key) { return Optional.empty(); }
            @Override public void tryWritePref(String key, String value) { /* no-op */ }
        };
    }
}
