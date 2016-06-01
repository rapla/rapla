package org.rapla.server.internal;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Context;

import org.rapla.entities.User;
import org.rapla.framework.RaplaException;
import org.rapla.logger.Logger;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;
import org.rapla.server.RemoteSession;
import org.rapla.storage.dbrm.LoginCredentials;
import org.rapla.storage.dbrm.LoginTokens;
import org.rapla.storage.dbrm.RemoteAuthentificationService;

@DefaultImplementation(context = InjectionContext.server, of = RemoteAuthentificationService.class)
public class RemoteAuthentificationServiceImpl extends RaplaAuthentificationService implements RemoteAuthentificationService
{
    @Inject
    RemoteSession session;
    private final HttpServletRequest request;

    @Inject
    public RemoteAuthentificationServiceImpl(@Context HttpServletRequest request)
    {
        this.request = request;
    }

    public Logger getLogger()
    {
        return session.getLogger();
    }

    @Override
    public void logout() throws RaplaException
    {
        if (session != null)
        {
            if (session.isAuthentified(request))
            {
                User user = session.getUser(request);
                if (user != null)
                {
                    getLogger().getChildLogger("login").info("Request Logout " + user.getUsername());
                }
                session.logout();
            }
        }
    }

    @Override
    public LoginTokens login(String username, String password, String connectAs) throws RaplaException
    {
        LoginCredentials loginCredentials = new LoginCredentials(username, password, connectAs);
        return auth(loginCredentials);
    }

    private LoginTokens auth(LoginCredentials credentials) throws RaplaException
    {
        User user = getUserFromCredentials(credentials);
        LoginTokens generateAccessToken = tokenHandler.generateAccessToken(user);
        return generateAccessToken;
    }

    @Override
    public String getRefreshToken() throws RaplaException
    {
        User user = getValidUser(session, request);
        String refreshToken = tokenHandler.getRefreshToken(user);
        return refreshToken;
    }

    @Override
    public String regenerateRefreshToken() throws RaplaException
    {
        User user = getValidUser(session, request);
        String refreshToken = tokenHandler.regenerateRefreshToken(user);
        return refreshToken;
    }

    @Override
    public LoginTokens refresh(String refreshToken) throws RaplaException
    {
        LoginTokens refresh = tokenHandler.refresh(refreshToken);
        return refresh;
    }

}
