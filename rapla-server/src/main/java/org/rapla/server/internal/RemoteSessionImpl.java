package org.rapla.server.internal;

import org.rapla.entities.EntityNotFoundException;
import org.rapla.entities.User;
import org.rapla.server.RemoteSession;
import org.rapla.storage.RaplaSecurityException;
import org.rapla.storage.dbrm.LoginTokens;

import org.springframework.beans.factory.annotation.Autowired;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Legacy {@link RemoteSession} fallback — fires only when
 * {@link org.rapla.server.spring.SpringSecurityRemoteSession} doesn't find a
 * JwtAuthenticationToken in the SecurityContextHolder. Used today by permit-all
 * endpoints (iCal export, JNLP, status page) that don't go through Spring
 * Security's JWT filter chain but still need to resolve a user from the request.
 *
 * <p>PRD 029 Phase 5 (2026-05-25): dropped the legacy {@code ?username=...&password=...}
 * request-param branch — confirmed dead via audit (nothing in the codebase
 * sends those params for auth). Three live extraction paths remain:
 * {@code Authorization: Bearer} header, {@code ?access_token=...} query param
 * (URL-embedded JWT for external iCal subscribers), {@code raplaLoginToken}
 * cookie (legacy browser session login).
 */
public class RemoteSessionImpl implements RemoteSession
{
    private static final String LOGIN_COOKIE = "raplaLoginToken";

    private User user;
    private TokenHandler tokenHandler;

    public RemoteSessionImpl(User user)
    {
        this.user = user;
    }

    @Autowired
    public RemoteSessionImpl(TokenHandler tokenHandler)
    {
        this.tokenHandler = tokenHandler;
    }

    private User extractUser(HttpServletRequest request) throws RaplaSecurityException
    {
        String token = request.getHeader("Authorization");
        if (token != null)
        {
            String bearerStr = "bearer";
            int bearer = token.toLowerCase().indexOf(bearerStr);
            if (bearer >= 0)
            {
                token = token.substring(bearer + bearerStr.length()).trim();
            }
        }
        else
        {
            token = request.getParameter("access_token");
            if (token == null)
            {
                final Cookie[] cookies = request.getCookies();
                if (cookies != null)
                {
                    for (Cookie cookie : cookies)
                    {
                        if (LOGIN_COOKIE.equals(cookie.getName()))
                        {
                            final String value = cookie.getValue();
                            try
                            {
                                token = LoginTokens.fromString(value).getAccessToken();
                            } catch (Exception ex)
                            {
                                throw new RaplaSecurityException("Invalid LoginToken " + value);
                            }
                            break;
                        }
                    }
                }
            }
        }
        try
        {
            return tokenHandler.getUserWithAccessToken(token);
        }
        catch (EntityNotFoundException ex)
        {
            throw new RaplaSecurityException("User not found.");
        }
    }

    public User checkAndGetUser(HttpServletRequest request) throws RaplaSecurityException
    {
        if (user != null)
            return user;
        final User userFromRequest = extractUser(request);
        if (userFromRequest == null)
            throw new RaplaSecurityException("No user found in session.");
        return userFromRequest;
    }

    public boolean isAuthentified(HttpServletRequest request)
    {
        if (user != null)
        {
            return true;
        }
        try
        {
            final User userFromRequest = extractUser(request);
            return userFromRequest != null;
        }
        catch (RaplaSecurityException e)
        {
            return false;
        }
    }

    public void logout()
    {
        user = null;
    }

}