package org.rapla.server.spring;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;

import java.time.Duration;

/**
 * PRD 072 Phase 2 — central cookie helper for the browser credential model A.
 * Owns the two cookie names, their attributes, and the configurable
 * {@code Secure} flag (review S5 — {@code rapla.oauth.web.cookie-secure},
 * default {@code true}; a dev-over-HTTP smoke test sets it {@code false} so the
 * browser keeps the cookie). {@code HttpOnly} and {@code SameSite=Lax} are
 * always on.
 *
 * <ul>
 *   <li>{@code access_token} — the rapla access JWT. Path {@code /} so it is
 *       sent on every {@code /api/**} call.</li>
 *   <li>{@code refresh_token} — the rapla refresh JWT. Path
 *       {@code /api/auth/session} so the long-lived token is NOT sent on every
 *       API call — it reaches ONLY the two session endpoints that legitimately
 *       need it, {@code /api/auth/session/refresh} and {@code /api/auth/session/logout}
 *       (PRD 072 § "Refresh mechanism"). Logout lives there so it can read this
 *       durable credential and revoke even when the access token has expired.</li>
 * </ul>
 */
public class CookieAuthSupport
{
    public static final String ACCESS_TOKEN_COOKIE = "access_token";
    public static final String REFRESH_TOKEN_COOKIE = "refresh_token";
    public static final String ACCESS_TOKEN_PATH = "/";
    public static final String REFRESH_TOKEN_PATH = "/api/auth/session";

    private final boolean secure;

    public CookieAuthSupport(@Value("${rapla.oauth.web.cookie-secure:true}") boolean secure)
    {
        this.secure = secure;
    }

    public boolean isSecure()
    {
        return secure;
    }

    /** Reads a named cookie value off the request, or {@code null} if absent. */
    public static String readCookie(HttpServletRequest request, String name)
    {
        if (request == null || request.getCookies() == null)
        {
            return null;
        }
        for (Cookie cookie : request.getCookies())
        {
            if (name.equals(cookie.getName()))
            {
                String value = cookie.getValue();
                return (value == null || value.isEmpty()) ? null : value;
            }
        }
        return null;
    }

    public void setAccessTokenCookie(HttpServletResponse response, String accessToken, long maxAgeSeconds)
    {
        response.addHeader("Set-Cookie", buildAccessCookie(accessToken, maxAgeSeconds).toString());
    }

    public void setRefreshTokenCookie(HttpServletResponse response, String refreshToken, long maxAgeSeconds)
    {
        response.addHeader("Set-Cookie", buildRefreshCookie(refreshToken, maxAgeSeconds).toString());
    }

    public ResponseCookie buildAccessCookie(String accessToken, long maxAgeSeconds)
    {
        return ResponseCookie.from(ACCESS_TOKEN_COOKIE, accessToken)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path(ACCESS_TOKEN_PATH)
                .maxAge(Duration.ofSeconds(maxAgeSeconds))
                .build();
    }

    public ResponseCookie buildRefreshCookie(String refreshToken, long maxAgeSeconds)
    {
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE, refreshToken)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path(REFRESH_TOKEN_PATH)
                .maxAge(Duration.ofSeconds(maxAgeSeconds))
                .build();
    }

    /**
     * Sign-out: writes expiring (maxAge 0) {@code access_token} and
     * {@code refresh_token} cookies at their respective paths so the browser
     * drops both. Same name/path/attributes as the set helpers — a mismatched
     * path would leave a stale cookie alive.
     */
    public void clearAuthCookies(HttpServletResponse response)
    {
        response.addHeader("Set-Cookie", buildAccessCookie("", 0).toString());
        response.addHeader("Set-Cookie", buildRefreshCookie("", 0).toString());
    }
}
