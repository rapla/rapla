package org.rapla.server.spring;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * PRD 072 Phase 2 — mirrors the {@code /oauth2/token} JSON token response into
 * the browser credential cookies. After a successful password / refresh /
 * authorization_code grant, the response body carries {@code access_token} and
 * (for the password / code grants) {@code refresh_token}; this filter copies
 * them into the {@code access_token} (path {@code /}) and {@code refresh_token}
 * (path {@code /api/auth/session}) HttpOnly cookies so the SPA + explorer
 * browser surfaces get the cookie credential without any client change.
 *
 * <p>The JSON body is left intact (Swing / API clients keep reading the tokens
 * from the body) — the cookies are purely additive. Only 2xx JSON responses are
 * touched. The cookies are session-scoped unless the request carries the stay-signed-in choice
 * (PRD 131 D2).
 */
public class TokenResponseCookieFilter extends OncePerRequestFilter
{
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final CookieAuthSupport cookies;
    private final long refreshTtlSeconds;

    public TokenResponseCookieFilter(CookieAuthSupport cookies, long refreshTtlSeconds)
    {
        this.cookies = cookies;
        this.refreshTtlSeconds = refreshTtlSeconds;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException
    {
        ContentCachingResponseWrapper wrapper = new ContentCachingResponseWrapper(response);
        try
        {
            chain.doFilter(request, wrapper);
            maybeSetCookies(wrapper, CookieAuthSupport.isRemembered(request));
        }
        finally
        {
            wrapper.copyBodyToResponse();
        }
    }

    private void maybeSetCookies(ContentCachingResponseWrapper wrapper, boolean remembered)
    {
        int status = wrapper.getStatus();
        if (status < 200 || status >= 300)
        {
            return;
        }
        String contentType = wrapper.getContentType();
        if (contentType == null || !contentType.contains("json"))
        {
            return;
        }
        byte[] body = wrapper.getContentAsByteArray();
        if (body.length == 0)
        {
            return;
        }
        try
        {
            JsonNode tree = MAPPER.readTree(new String(body, StandardCharsets.UTF_8));
            JsonNode access = tree.get("access_token");
            if (access != null && access.isString())
            {
                long expiresIn = tree.has("expires_in") && tree.get("expires_in").isNumber()
                        ? tree.get("expires_in").asLong()
                        : RefreshSessionService.ACCESS_TOKEN_TTL_SECONDS;
                cookies.setAccessTokenCookie(wrapper, access.asString(), expiresIn, remembered);
            }
            JsonNode refresh = tree.get("refresh_token");
            if (refresh != null && refresh.isString())
            {
                cookies.setRefreshTokenCookie(wrapper, refresh.asString(), refreshTtlSeconds, remembered);
            }
        }
        catch (RuntimeException ignored)
        {
            // Not a token JSON body — leave the response untouched.
        }
    }
}
