package org.rapla.server.internal;

import org.rapla.entities.User;
import org.rapla.framework.logger.Logger;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;
import org.rapla.inject.server.RequestScoped;
import org.rapla.rest.server.RaplaAuthRestPage;
import org.rapla.server.RemoteSession;
import org.rapla.storage.RaplaSecurityException;
import org.rapla.storage.dbrm.LoginTokens;

import javax.inject.Inject;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;

@RequestScoped
@DefaultImplementation(of=RemoteSession.class,context = InjectionContext.server)
public class RemoteSessionImpl implements RemoteSession {
    private User user;
    final private Logger logger;

    public RemoteSessionImpl(Logger logger, User user)
    {
        this.logger = logger;
        this.user = user;
    }

    @Inject
    public RemoteSessionImpl(Logger logger, TokenHandler tokenHandler, RaplaAuthentificationService service, HttpServletRequest request )
    {
        this.logger = logger;
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
                if(cookies != null)
                {
                    for (Cookie cookie : cookies)
                    {
                        if(RaplaAuthRestPage.LOGIN_COOKIE.equals(cookie.getName()))
                        {
                            final String value = cookie.getValue();
                            token = LoginTokens.fromString(value).getAccessToken();
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
                user = service.getUserWithPassword(username, password);
            }
        }
        if (user == null)
        {
            user = tokenHandler.getUserWithAccessToken(token);
        }
        this.user = user;
    }


    public Logger getLogger()
    {
        return logger;
    }

    public User getUser() throws RaplaSecurityException
    {
        if (user == null)
            throw new RaplaSecurityException("No user found in session.");
        return user;
    }

    public boolean isAuthentified()
    {
        return user != null;
    }

    public void logout()
    {
        user = null;
    }



}