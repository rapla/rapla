package org.rapla.server.spring;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * PRD 131 D2 — the SSO entries of the {@code /login} form are submit buttons that POST to
 * {@code /oauth2/authorization/{id}} together with the stay-signed-in choice. This filter records the
 * choice in the {@code rapla-remember-choice} cookie before Spring's {@code oauth2Login()} redirect to
 * the provider, so the session cookies written after the provider's callback follow the choice, without
 * JavaScript. A plain GET link carries no choice and leaves the earlier choice alone.
 */
public class RememberChoiceFilter extends OncePerRequestFilter
{
    private static final String SSO_KICKOFF = "/oauth2/authorization/";

    private final CookieAuthSupport cookies;

    public RememberChoiceFilter(CookieAuthSupport cookies)
    {
        this.cookies = cookies;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request)
    {
        return !"POST".equals(request.getMethod())
                || !request.getRequestURI().startsWith(request.getContextPath() + SSO_KICKOFF);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException
    {
        cookies.setRememberChoiceCookie(request, response, "on".equals(request.getParameter("remember-me")));
        chain.doFilter(request, response);
    }
}
