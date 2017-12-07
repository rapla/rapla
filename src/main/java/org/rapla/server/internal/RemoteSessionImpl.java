package org.rapla.server.internal;

import org.rapla.entities.EntityNotFoundException;
import org.rapla.entities.User;
import org.rapla.framework.RaplaException;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;
import org.rapla.logger.Logger;
import org.rapla.rest.server.RaplaAuthRestPage;
import org.rapla.server.RemoteSession;
import org.rapla.storage.RaplaSecurityException;
import org.rapla.storage.dbrm.LoginTokens;

import javax.inject.Inject;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;

@DefaultImplementation(of = RemoteSession.class, context = InjectionContext.server)
public class RemoteSessionImpl implements RemoteSession
{
    private User user;
    final private Logger logger;
    private TokenHandler tokenHandler;
    private RaplaAuthentificationService service;

    public RemoteSessionImpl(Logger logger, User user)
    {
        this.logger = logger;
        this.user = user;
    }

    @Inject
    public RemoteSessionImpl(Logger logger, TokenHandler tokenHandler, RaplaAuthentificationService service)
    {
        this.logger = logger;
        this.tokenHandler = tokenHandler;
        this.service = service;

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
                        if (RaplaAuthRestPage.LOGIN_COOKIE.equals(cookie.getName()))
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
        User user = null;
        if (token == null)
        {
            String username = request.getParameter("username");
            String password = request.getParameter("password");
            if (username != null && password != null)
            {
                try
                {
                    user = service.getUserWithPassword(username, password);
                }
                catch(RaplaException e)
                {
                    throw new RaplaSecurityException(e);
                }
            }
        }
        if (user == null)
        {
            try
            {
                user = tokenHandler.getUserWithAccessToken(token);
            }
            catch ( EntityNotFoundException ex)
            {
                throw new RaplaSecurityException("User not found.");
            }
        }
        return user;
    }

    public Logger getLogger()
    {
        return logger;
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