package org.rapla.server.spring;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.RememberMeServices;
import org.springframework.security.web.authentication.logout.CompositeLogoutHandler;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Implements OIDC {@code prompt=login} re-authentication for rapla's own
 * Authorization Server session. Spring Authorization Server validates the
 * {@code prompt} parameter but only acts on {@code prompt=none} (error
 * responses); an authenticated browser session hitting
 * {@code GET /oauth2/authorize?prompt=login} would silently get a code for the
 * old user — which broke the Swing SSO logout ("sign out, next SSO login is
 * still the old user").
 *
 * <p>Behaviour: any {@code GET /oauth2/authorize} whose {@code prompt} values
 * contain {@code login} is (a) logged out — HTTP session invalidated, security
 * context cleared, remember-me cookie + persistent token removed — when a
 * non-anonymous authentication is present, and (b) redirected to the same
 * authorize URL with the {@code login} prompt value removed. The stripped
 * re-entry request is then unauthenticated, so the standard entry point sends
 * the browser to the {@code /login} chooser, and the request the cache saves
 * for post-login replay no longer carries {@code prompt=login} — one-shot by
 * construction, no logout/login loop.
 */
public final class PromptLoginReauthenticationFilter extends OncePerRequestFilter
{
    private static final Logger LOGGER = LoggerFactory.getLogger(PromptLoginReauthenticationFilter.class);
    private static final String AUTHORIZE_PATH = "/oauth2/authorize";
    private static final String PROMPT_PARAM = "prompt";
    private static final String LOGIN_PROMPT = "login";

    private final LogoutHandler logoutHandler;

    public PromptLoginReauthenticationFilter(RememberMeServices rememberMeServices)
    {
        this.logoutHandler = rememberMeServices instanceof LogoutHandler rememberMeLogout
                ? new CompositeLogoutHandler(new SecurityContextLogoutHandler(), rememberMeLogout)
                : new SecurityContextLogoutHandler();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request)
    {
        if (!"GET".equals(request.getMethod())
                || !request.getRequestURI().equals(request.getContextPath() + AUTHORIZE_PATH))
        {
            return true;
        }
        return !promptValues(request).contains(LOGIN_PROMPT);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException
    {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken))
        {
            LOGGER.info("prompt=login on authenticated authorize request — terminating session of '{}' for re-authentication",
                    authentication.getName());
            logoutHandler.logout(request, response, authentication);
        }
        response.sendRedirect(urlWithoutLoginPrompt(request));
    }

    private static List<String> promptValues(HttpServletRequest request)
    {
        String prompt = request.getParameter(PROMPT_PARAM);
        if (prompt == null || prompt.isEmpty()) return List.of();
        return Arrays.asList(prompt.trim().split("\\s+"));
    }

    private static String urlWithoutLoginPrompt(HttpServletRequest request)
    {
        StringJoiner query = new StringJoiner("&");
        for (Map.Entry<String, String[]> param : request.getParameterMap().entrySet())
        {
            for (String value : param.getValue())
            {
                if (PROMPT_PARAM.equals(param.getKey()))
                {
                    String remaining = String.join(" ", Arrays.stream(value.trim().split("\\s+"))
                            .filter(v -> !LOGIN_PROMPT.equals(v))
                            .toList());
                    if (remaining.isEmpty()) continue;
                    value = remaining;
                }
                query.add(URLEncoder.encode(param.getKey(), StandardCharsets.UTF_8)
                        + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8));
            }
        }
        String queryString = query.toString();
        return request.getRequestURI() + (queryString.isEmpty() ? "" : "?" + queryString);
    }
}
