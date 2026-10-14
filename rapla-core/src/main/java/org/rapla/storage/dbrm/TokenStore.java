package org.rapla.storage.dbrm;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Client-side persistent storage for the OAuth refresh token plus a few
 * non-secret client preferences, so re-launching the rapla client doesn't
 * require the user to log in again (until the refresh token expires) and
 * can default the login dialog to the language + sign-in method used last.
 *
 * <p>The contract is intentionally "never throws": persistence is a
 * best-effort optimisation. If anything goes wrong (read-only home,
 * sandbox restriction, corrupted store, missing optional library), the
 * implementation logs at debug/warn and the caller behaves as if nothing
 * were cached. The user then sees a normal login flow — never an error
 * caused by storage.
 *
 * <p>On disk it is a flat JSON object: the refresh token under
 * {@code refreshToken}, preferences under their own keys
 * ({@link #KEY_LANGUAGE}, {@link #KEY_LOGIN_METHOD}).
 * {@link #tryClear()} removes only the token — the preferences survive
 * logout so the next login dialog still defaults sensibly (PRD 029 Phase 5).
 *
 * <p>Backend today: {@link FileTokenStore} (dotfile in {@code ~/.rapla/},
 * the token kept per server origin). The factory in {@link TokenStores} creates it at startup.
 */
public interface TokenStore
{
    /** Preference key: the language code selected at the last successful login
     *  (empty/absent = "default"). */
    String KEY_LANGUAGE = "language";

    /** Preference key: the sign-in method used at the last successful login —
     *  {@code "password"} or {@code "sso"} (PRD 072 Phase 5: rapla is the single
     *  SSO entry; the per-provider id is gone). */
    String KEY_LOGIN_METHOD = "loginMethod";

    /** Preference key: {@code "login"} after an explicit logout — the next start sends {@code prompt=login} even across
     *  a JVM exit; cleared by the next successful login (PRD 131 D6). */
    String KEY_NEXT_LOGIN_PROMPT = "nextLoginPrompt";

    /** Returns the cached refresh token, or empty on any failure. Never throws. */
    Optional<String> read();

    /** Persists the refresh token, preserving any stored preferences.
     *  Logs warning on failure; never throws. */
    void tryWrite(String token);

    /** Deletes the cached refresh token (preferences are kept).
     *  Logs warning on failure; never throws. */
    void tryClear();

    /** Returns a stored client preference, or empty on any failure / absence.
     *  Never throws. */
    Optional<String> readPref(String key);

    /** Persists a client preference alongside the token (empty value clears it).
     *  Logs warning on failure; never throws. */
    void tryWritePref(String key, String value);

    /** Why the backend does not persist (memory only or write failed), with the path it attempted;
     *  empty while storing works. */
    default Optional<Failure> failure() { return Optional.empty(); }

    record Failure(Path path, String reason) {}
}
