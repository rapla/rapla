package org.rapla.server.spring;

import com.nimbusds.jose.JOSEException;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.TypedComponentRole;
import org.rapla.storage.RaplaSecurityException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Single-source-of-truth for rapla's refresh-token lifecycle. Used by
 * {@code /api/auth/login} (legacy direct-password issuance), Spring AS's
 * {@code /oauth2/token} grants (code + refresh + future password), and
 * {@code /api/auth/logout} / {@code /oauth2/revoke} (revocation). One JWT
 * format ({@code typ=refresh}), one storage slot per user, one revocation
 * model.
 *
 * <h2>Design (PRD 031, refined PRD 041)</h2>
 * <ul>
 *   <li><b>Single token per user.</b> One entry in {@link #SESSION}
 *       preferences key. Issuance writes the full token; later issuances
 *       check first — if the stored token is still valid (signature OK, not
 *       expired), <b>return THAT instead of minting a new one</b>. Result:
 *       multiple devices logging in for the same user end up sharing the
 *       same refresh token (no last-login-wins kick).</li>
 *   <li><b>Restart-safe.</b> Validation is stateless: JWT signature against
 *       the persistent RSA key from {@code RaplaKeyStorage} +
 *       {@code typ=refresh} claim + exact match against the prefs entry.
 *       No in-memory authorization state.</li>
 *   <li><b>Never rotate at refresh time.</b> {@code /oauth2/token grant_type=refresh_token}
 *       returns the SAME refresh token until it expires (30 d). At expiry
 *       the user re-Authorizes — predictable, all devices at once.
 *       Avoids the multi-tab "tab A wins rotation, tab B's stale token
 *       fails" problem.</li>
 *   <li><b>Logout = clear the entry.</b> Next refresh attempt by any
 *       device fails with {@code session token mismatch}.</li>
 * </ul>
 *
 * <h2>Why store the full JWT, not a hash?</h2>
 * The data file already contains the persistent RSA private key
 * ({@code RaplaKeyStorage}). An attacker with file access can sign
 * arbitrary tokens for any user — game-over regardless of whether refresh
 * tokens are stored as JWTs or hashes. Storing the full JWT lets us
 * <b>return the existing token on subsequent logins</b> (multi-tab share)
 * with no marginal security loss vs the previous hash-only design.
 *
 * <h2>Trade-offs vs mainstream OAuth per-session model</h2>
 * <ul>
 *   <li>+ constant-size storage (no list growth, no cleanup job)</li>
 *   <li>+ multi-device works without per-session tracking</li>
 *   <li>+ multi-tab works without rotation conflicts (full-token reuse)</li>
 *   <li>− no per-device revocation (logout kicks all devices)</li>
 *   <li>− no theft detection via rotation conflict</li>
 *   <li>For per-device revocation + theft detection, deploy against
 *       Keycloak (PRD 031: IdP swap is an env-var override).</li>
 * </ul>
 */
@Service
public class RefreshSessionService
{
    /**
     * User-preferences key holding {@code {token: "JWT", issuedAt: "..."}}
     * for the user's current refresh token. Single slot — overwritten on
     * fresh issuance, cleared on logout.
     */
    public static final TypedComponentRole<String> SESSION =
            new TypedComponentRole<>("org.rapla.auth.session");

    public static final long ACCESS_TOKEN_TTL_SECONDS = 3600;
    public static final long REFRESH_TOKEN_TTL_SECONDS = 30L * 24 * 3600;

    private final JwtConfig.JwtIssuer jwtIssuer;
    private final JwtDecoder jwtDecoder;
    private final RaplaFacade facade;

    public RefreshSessionService(JwtConfig.JwtIssuer jwtIssuer, JwtDecoder jwtDecoder, RaplaFacade facade)
    {
        this.jwtIssuer = jwtIssuer;
        this.jwtDecoder = jwtDecoder;
        this.facade = facade;
    }

    /**
     * Mints an access token, returns (or mints) a refresh token. If the user
     * has a stored refresh token that's still signature-valid and unexpired,
     * <b>returns THAT token</b> instead of minting a new one — so multi-device
     * users end up sharing the same refresh token. Otherwise mints+stores a
     * fresh one. Used at login (any path).
     */
    public IssuedTokens issueAndPersist(User user) throws RaplaException, JOSEException
    {
        String accessToken = jwtIssuer.issueAccessToken(user.getId(), ACCESS_TOKEN_TTL_SECONDS);
        String refreshToken = obtainOrMintRefreshToken(user);
        return new IssuedTokens(accessToken, refreshToken, ACCESS_TOKEN_TTL_SECONDS);
    }

    /**
     * Issues a fresh refresh JWT for the user (if no valid stored one) and
     * persists it. If a valid stored refresh JWT already exists for the user,
     * returns THAT instead of minting a new one. Used by Spring AS's custom
     * token generator on the {@code authorization_code} grant.
     */
    public String issueAndPersistRefreshToken(User user) throws RaplaException, JOSEException
    {
        return obtainOrMintRefreshToken(user);
    }

    /**
     * Returns the user's stored refresh token if it's still signature-valid
     * and unexpired; otherwise mints a new one and stores it.
     */
    private String obtainOrMintRefreshToken(User user) throws RaplaException, JOSEException
    {
        Preferences prefs = facade.getPreferences(user);
        String stored = readStoredToken(prefs);
        if (stored != null && isStillValid(stored))
        {
            return stored;
        }
        String fresh = jwtIssuer.issueRefreshToken(user.getId(), REFRESH_TOKEN_TTL_SECONDS);
        persistSession(user, fresh);
        return fresh;
    }

    private boolean isStillValid(String refreshToken)
    {
        try
        {
            Jwt parsed = jwtDecoder.decode(refreshToken);
            if (!"refresh".equals(parsed.getClaimAsString("typ"))) return false;
            Instant exp = parsed.getExpiresAt();
            return exp != null && exp.isAfter(Instant.now());
        }
        catch (Exception e)
        {
            return false;
        }
    }

    /**
     * Validates a presented refresh token: JWT signature + {@code typ=refresh}
     * claim + exact match against the user's stored token. Throws
     * {@link RaplaSecurityException} on any failure. Returns the resolved
     * {@link User} and the parsed JWT.
     */
    public ValidatedRefresh validate(String presentedRefreshToken) throws RaplaException
    {
        Jwt parsed;
        try
        {
            parsed = jwtDecoder.decode(presentedRefreshToken);
        }
        catch (Exception e)
        {
            throw new RaplaSecurityException("invalid refresh token");
        }
        if (!"refresh".equals(parsed.getClaimAsString("typ")))
        {
            throw new RaplaSecurityException("not a refresh token");
        }
        String userId = parsed.getSubject();
        User user = facade.getOperator().tryResolve(userId, User.class);
        if (user == null)
        {
            throw new RaplaSecurityException("user not found");
        }
        Preferences prefs = facade.getPreferences(user);
        String storedToken = readStoredToken(prefs);
        if (storedToken == null || !storedToken.equals(presentedRefreshToken))
        {
            throw new RaplaSecurityException("session token mismatch — refresh token revoked or rotated");
        }
        return new ValidatedRefresh(user, parsed);
    }

    /**
     * Issues a fresh access JWT (1h TTL by default). Used by Spring AS's
     * custom refresh-token authentication provider after validating the
     * presented refresh token.
     */
    public String issueAccessToken(User user) throws JOSEException
    {
        return jwtIssuer.issueAccessToken(user.getId(), ACCESS_TOKEN_TTL_SECONDS);
    }

    /**
     * Clears the user's session entry — invalidates every refresh token in
     * circulation for this user (single-token-per-user model). Called from
     * logout endpoints.
     */
    public void clearSession(User user) throws RaplaException
    {
        Preferences prefs = facade.getPreferences(user);
        Preferences edit = facade.edit(prefs);
        edit.putEntry(SESSION, null);
        facade.store(edit);
    }

    /**
     * Overwrites the user's session entry with the newly-issued refresh JWT.
     * Public so test fixtures + Spring AS issuance hooks can call it.
     */
    public void persistSession(User user, String refreshToken) throws RaplaException
    {
        // JSON-escape the JWT (it has '.' separators but no quotes/backslashes —
        // standard base64url alphabet — so simple concatenation is safe).
        String json = "{\"token\":\"" + refreshToken + "\",\"issuedAt\":\"" + Instant.now() + "\"}";
        Preferences prefs = facade.getPreferences(user);
        Preferences edit = facade.edit(prefs);
        edit.putEntry(SESSION, json);
        facade.store(edit);
    }

    /** Reads the {@code token} field from the stored session JSON; returns null if no session. */
    private static String readStoredToken(Preferences prefs)
    {
        String stored = prefs.getEntryAsString(SESSION, null);
        if (stored == null || stored.isEmpty()) return null;
        int idx = stored.indexOf("\"token\"");
        if (idx < 0) return null;
        int colon = stored.indexOf(':', idx);
        if (colon < 0) return null;
        int q1 = stored.indexOf('"', colon + 1);
        if (q1 < 0) return null;
        int q2 = stored.indexOf('"', q1 + 1);
        if (q2 < 0) return null;
        return stored.substring(q1 + 1, q2);
    }

    /** Result of {@link #issueAndPersist}. */
    public record IssuedTokens(String accessToken, String refreshToken, long expiresIn) {}

    /** Result of {@link #validate} — the resolved user + the parsed JWT. */
    public record ValidatedRefresh(User user, Jwt parsedJwt) {}
}
