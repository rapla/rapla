package org.rapla.server.spring.web;

import com.nimbusds.jose.JOSEException;
import jakarta.servlet.http.HttpServletRequest;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.TypedComponentRole;
import org.rapla.storage.RaplaSecurityException;
import org.rapla.logger.Logger;
import org.rapla.server.internal.RaplaAuthentificationService;
import org.rapla.server.spring.JwtConfig;
import org.rapla.storage.dbrm.LoginCredentials;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;

/**
 * Rapla's session-bearing auth endpoints. PRD 031 design (single token per
 * user, rotate when stale):
 *
 * <ul>
 *   <li>{@code /auth/login} mints access + refresh, stores
 *       {@code sha256(refresh)} in the user's preferences under
 *       {@link #SESSION}. Overwrites any previous session for the user —
 *       a fresh login replaces an existing session (multi-device users
 *       end up sharing the most recent refresh token).</li>
 *   <li>{@code /auth/refresh} decodes the refresh JWT, verifies its
 *       SHA-256 matches the stored hash, mints a fresh access token,
 *       and ONLY rotates the refresh token when it's within the
 *       renewal window ({@link #REFRESH_RENEWAL_THRESHOLD_SECONDS}).
 *       This bounds the server-side write frequency to ~1 per user per
 *       week even for active users.</li>
 *   <li>{@code /auth/logout} clears the session entry. Requires a
 *       valid Bearer (access or refresh) so we know whose entry to clear.</li>
 * </ul>
 *
 * <p>Trade-off (vs. mainstream OAuth per-session model):
 * <ul>
 *   <li>+ constant-size storage per user (no list growth, no cleanup job)</li>
 *   <li>+ multi-device works (devices share the same refresh token; both refresh against the same hash)</li>
 *   <li>- no per-device revocation (revoking the session logs the user out everywhere)</li>
 *   <li>- no theft detection via rotation conflict</li>
 *   <li>This is the right trade-off for rapla's user-level audit needs.
 *       Per-device control comes for free when migrating to Keycloak,
 *       which has its own per-session storage.</li>
 * </ul>
 */
@RestController
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix = "rapla.file-datasources", name = "raplafile")
@RequestMapping(value = "/auth", produces = "application/json")
public class AuthController
{
    static final TypedComponentRole<String> SESSION = new TypedComponentRole<>("org.rapla.auth.session");

    private static final long ACCESS_TOKEN_TTL_SECONDS = 3600;
    private static final long REFRESH_TOKEN_TTL_SECONDS = 30L * 24 * 3600;
    /** Rotate the refresh JWT when its remaining lifetime drops below this. 7 days. */
    private static final long REFRESH_RENEWAL_THRESHOLD_SECONDS = 7L * 24 * 3600;

    private final RaplaAuthentificationService authService;
    private final JwtConfig.JwtIssuer jwtIssuer;
    private final org.springframework.security.oauth2.jwt.JwtDecoder jwtDecoder;
    private final RaplaFacade facade;
    private final Logger logger;

    public AuthController(RaplaAuthentificationService authService,
                          JwtConfig.JwtIssuer jwtIssuer,
                          org.springframework.security.oauth2.jwt.JwtDecoder jwtDecoder,
                          RaplaFacade facade,
                          Logger logger)
    {
        this.authService = authService;
        this.jwtIssuer = jwtIssuer;
        this.jwtDecoder = jwtDecoder;
        this.facade = facade;
        this.logger = logger;
    }

    @PostMapping("/login")
    public TokenResponse login(@RequestBody LoginCredentials credentials, HttpServletRequest request)
            throws RaplaException, JOSEException
    {
        User user = authService.getUserFromCredentials(credentials);
        return mintAndPersist(user);
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@RequestBody RefreshRequest body) throws RaplaException, JOSEException
    {
        org.springframework.security.oauth2.jwt.Jwt parsed;
        try
        {
            parsed = jwtDecoder.decode(body.refreshToken);
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

        // Verify the presented token's hash matches the stored session.
        // Single-token-per-user model: any other refresh token (e.g. a previously
        // rotated one, or one for a session that was logged out) won't match.
        Preferences prefs = facade.getPreferences(user);
        String storedHash = readStoredHash(prefs);
        String presentedHash = sha256Hex(body.refreshToken);
        if (storedHash == null || !storedHash.equals(presentedHash))
        {
            throw new RaplaSecurityException("session token mismatch — refresh token revoked or rotated");
        }

        String accessToken = jwtIssuer.issueAccessToken(userId, ACCESS_TOKEN_TTL_SECONDS);

        // Rotate refresh only if approaching expiry. ~1 write per user per week
        // for active users; zero writes for most refresh calls.
        Instant exp = parsed.getExpiresAt();
        long remainingSeconds = exp == null ? 0 : (exp.getEpochSecond() - Instant.now().getEpochSecond());
        if (remainingSeconds < REFRESH_RENEWAL_THRESHOLD_SECONDS)
        {
            String newRefresh = jwtIssuer.issueRefreshToken(userId, REFRESH_TOKEN_TTL_SECONDS);
            persistSession(user, newRefresh);
            return new TokenResponse(accessToken, newRefresh, ACCESS_TOKEN_TTL_SECONDS);
        }
        // Otherwise hand back the same refresh token — no server-side write.
        return new TokenResponse(accessToken, body.refreshToken, ACCESS_TOKEN_TTL_SECONDS);
    }

    @PostMapping("/logout")
    public void logout(@RequestHeader(value = "Authorization", required = false) String authHeader)
            throws RaplaException
    {
        if (authHeader == null || !authHeader.startsWith("Bearer "))
        {
            throw new IllegalArgumentException("Bearer token required for logout");
        }
        String token = authHeader.substring("Bearer ".length()).trim();
        org.springframework.security.oauth2.jwt.Jwt jwt;
        try
        {
            jwt = jwtDecoder.decode(token);
        }
        catch (Exception e)
        {
            // Invalid token → nothing to clear. Idempotent.
            if (logger != null) logger.debug("logout with invalid bearer: " + e.getMessage());
            return;
        }
        String userId = jwt.getSubject();
        if (userId == null) return;
        User user = facade.getOperator().tryResolve(userId, User.class);
        if (user == null) return;
        clearSession(user);
    }

    private TokenResponse mintAndPersist(User user) throws RaplaException, JOSEException
    {
        String accessToken = jwtIssuer.issueAccessToken(user.getId(), ACCESS_TOKEN_TTL_SECONDS);
        String refreshToken = jwtIssuer.issueRefreshToken(user.getId(), REFRESH_TOKEN_TTL_SECONDS);
        persistSession(user, refreshToken);
        return new TokenResponse(accessToken, refreshToken, ACCESS_TOKEN_TTL_SECONDS);
    }

    /** Overwrites the user's session entry with the hash of the newly-issued refresh token. */
    private void persistSession(User user, String refreshToken) throws RaplaException
    {
        String hash = sha256Hex(refreshToken);
        String json = "{\"hash\":\"" + hash + "\",\"issuedAt\":\"" + Instant.now() + "\"}";
        Preferences prefs = facade.getPreferences(user);
        Preferences edit = facade.edit(prefs);
        edit.putEntry(SESSION, json);
        facade.store(edit);
    }

    private void clearSession(User user) throws RaplaException
    {
        Preferences prefs = facade.getPreferences(user);
        Preferences edit = facade.edit(prefs);
        edit.putEntry(SESSION, null);
        facade.store(edit);
    }

    /** Reads the {@code hash} field from the stored session JSON; returns null if no session. */
    private static String readStoredHash(Preferences prefs)
    {
        String stored = prefs.getEntryAsString(SESSION, null);
        if (stored == null || stored.isEmpty()) return null;
        int idx = stored.indexOf("\"hash\"");
        if (idx < 0) return null;
        int colon = stored.indexOf(':', idx);
        if (colon < 0) return null;
        int q1 = stored.indexOf('"', colon + 1);
        if (q1 < 0) return null;
        int q2 = stored.indexOf('"', q1 + 1);
        if (q2 < 0) return null;
        return stored.substring(q1 + 1, q2);
    }

    private static String sha256Hex(String input)
    {
        try
        {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public static class RefreshRequest
    {
        public String refreshToken;

        public String getRefreshToken() { return refreshToken; }
        public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
    }

    public static class TokenResponse
    {
        public final String accessToken;
        public final String refreshToken;
        public final long expiresIn;

        public TokenResponse(String accessToken, String refreshToken, long expiresIn)
        {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.expiresIn = expiresIn;
        }

        public String getAccessToken() { return accessToken; }
        public String getRefreshToken() { return refreshToken; }
        public long getExpiresIn() { return expiresIn; }
    }
}
