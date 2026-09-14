package org.rapla.storage.dbrm;

import java.util.Arrays;

/**
 * In-process parameter object for {@link OAuth2PasswordLogin#login}. Carries
 * the password as a mutable {@code char[]} so callers can zero it after the
 * seam returns — avoiding the long-lived immutable {@code String} exposure
 * {@code new String(char[])} would have created on the heap.
 *
 * <p>PRD 029 Phase 5 (2026-05-25): dropped the {@code connectAs} field. The
 * OAuth2 password grant has no {@code connect_as} parameter; the modern
 * impersonation path is {@code POST /api/auth/impersonate} via the
 * dual-slot model. The pre-Phase-5 seam always threw on a non-null
 * {@code connectAs}, so the field was dead.
 *
 * <p>Not a wire DTO: the legacy custom-rapla JSON shape was deleted with
 * {@code /api/auth/login} (PRD 041); the OAuth2 password-grant body is
 * built manually in {@code OAuth2PasswordLogin.login()}.
 */
public class LoginCredentials {
    private String username;
    private char[] password;
    public LoginCredentials()
    {
    }
    public LoginCredentials(String username, char[] password) {
        super();
        this.username = username;
        this.password = password;
    }

    public String getUsername() {
        return username;
    }
    public char[] getPassword() {
        return password;
    }

    /** Zeroes the password in place. Callers should invoke this once the
     *  HTTP login round-trip has completed so the credentials don't linger
     *  on the heap waiting for GC. Idempotent / null-safe. */
    public void clearPassword() {
        if (password != null) Arrays.fill(password, '\0');
    }
}
