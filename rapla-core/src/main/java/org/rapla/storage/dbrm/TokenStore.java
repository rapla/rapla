package org.rapla.storage.dbrm;

import java.util.Optional;

/**
 * Client-side persistent storage for the OAuth refresh token, so that
 * re-launching the rapla client doesn't require the user to log in again
 * until the refresh token itself expires.
 *
 * <p>The contract is intentionally "never throws": persistence is a
 * best-effort optimisation to skip the login dialog. If anything goes
 * wrong (read-only home, sandbox restriction, corrupted store, missing
 * optional library), the implementation logs at debug/warn and the
 * caller behaves as if no token were cached. The user then sees a
 * normal login flow — never an error caused by storage.
 *
 * <p>Backends today: {@link FileTokenStore} (dotfile in
 * {@code ~/.rapla/}), {@link JnlpTokenStore} (JNLP
 * {@code PersistenceService} when launched via OpenWebStart / IcedTea-Web).
 * The factory in {@link TokenStores} picks the right one at startup.
 */
public interface TokenStore
{
    /** Returns the cached refresh token, or empty on any failure. Never throws. */
    Optional<String> read();

    /** Persists the refresh token. Logs warning on failure; never throws. */
    void tryWrite(String token);

    /** Deletes the cached token. Logs warning on failure; never throws. */
    void tryClear();
}
