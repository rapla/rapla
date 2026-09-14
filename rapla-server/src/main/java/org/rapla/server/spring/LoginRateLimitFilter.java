package org.rapla.server.spring;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * H2: throttles the credential login paths — form login ({@code POST /login}) and the
 * OAuth password grant ({@code POST /oauth2/token}, {@code grant_type=password}) — with
 * an exponential backoff per client-IP + username (see {@link LoginAttemptTracker}).
 *
 * <p>Pre-check rejects an over-threshold key with {@code 429 + Retry-After} before any
 * authentication runs; after the request it records success/failure from the outcome
 * (token endpoint: status; form login: redirect to {@code ?error}). The client IP comes
 * from {@code getRemoteAddr()}, which is the real client behind the LB thanks to
 * {@code forward-headers-strategy: native}.
 */
public class LoginRateLimitFilter extends OncePerRequestFilter
{
    private final LoginAttemptTracker tracker;

    public LoginRateLimitFilter(LoginAttemptTracker tracker)
    {
        this.tracker = tracker;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException
    {
        if (!isLoginAttempt(request))
        {
            chain.doFilter(request, response);
            return;
        }
        String key = key(request);
        long wait = tracker.retryAfterSeconds(key);
        if (wait > 0)
        {
            response.setStatus(429);
            response.setHeader("Retry-After", Long.toString(wait));
            response.setContentType("text/plain; charset=UTF-8");
            response.getWriter().write("Too many login attempts. Retry in " + wait + "s.");
            return;
        }
        chain.doFilter(request, response);
        if (isFailure(request, response))
        {
            tracker.onFailure(key);
        }
        else
        {
            tracker.onSuccess(key);
        }
    }

    private static boolean isLoginAttempt(HttpServletRequest r)
    {
        if (!"POST".equalsIgnoreCase(r.getMethod()))
        {
            return false;
        }
        String uri = r.getRequestURI();
        if (uri == null)
        {
            return false;
        }
        if (uri.endsWith("/login"))
        {
            return true;
        }
        return uri.endsWith("/oauth2/token") && "password".equals(r.getParameter("grant_type"));
    }

    private static String key(HttpServletRequest r)
    {
        String user = r.getParameter("username");
        return r.getRemoteAddr() + "|" + (user == null ? "" : user.trim().toLowerCase());
    }

    private static boolean isFailure(HttpServletRequest r, HttpServletResponse response)
    {
        int status = response.getStatus();
        if (status >= 400)
        {
            return true;
        }
        // form login signals a bad credential by redirecting to the error page
        String uri = r.getRequestURI();
        if (uri != null && uri.endsWith("/login") && status >= 300 && status < 400)
        {
            String location = response.getHeader("Location");
            return location != null && location.contains("error");
        }
        return false;
    }
}
