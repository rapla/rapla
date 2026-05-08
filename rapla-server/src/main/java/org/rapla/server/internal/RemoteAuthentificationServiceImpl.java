package org.rapla.server.internal;

import org.rapla.entities.User;
import org.rapla.framework.RaplaException;
import org.rapla.logger.Logger;
import org.rapla.server.RemoteSession;
import org.rapla.storage.dbrm.LoginCredentials;
import org.rapla.storage.dbrm.LoginTokens;
import org.rapla.storage.dbrm.RemoteAuthentificationService;

import org.springframework.beans.factory.annotation.Autowired;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.Context;

public class RemoteAuthentificationServiceImpl extends RaplaAuthentificationService implements RemoteAuthentificationService
{
    @Autowired
    RemoteSession session;
    private final HttpServletRequest request;

    @Autowired
    public RemoteAuthentificationServiceImpl(@Context HttpServletRequest request)
    {
        this.request = request;
    }

    public Logger getLogger()
    {
        return session.getLogger();
    }

    public void logout() throws RaplaException
    {
        if (session != null)
        {
            if (session.isAuthentified(request))
            {
                User user = session.checkAndGetUser(request);
                if (user != null)
                {
                    getLogger().getChildLogger("login").info("Request Logout " + user.getUsername());
                }
                session.logout();
            }
        }
    }

    @Override
    public LoginTokens login(LoginCredentials credentials) throws RaplaException
    {
        User user = getUserFromCredentials(credentials);
        return tokenHandler.generateAccessToken(user);
    }

    public String getRefreshToken() throws RaplaException
    {
        User user = getValidUser(session, request);
        return tokenHandler.getRefreshToken(user);
    }

    public String regenerateRefreshToken() throws RaplaException
    {
        User user = getValidUser(session, request);
        return tokenHandler.regenerateRefreshToken(user);
    }

    @Override
    public LoginTokens refresh(RemoteAuthentificationService.RefreshRequest body) throws RaplaException
    {
        return tokenHandler.refresh(body.refreshToken);
    }

}
