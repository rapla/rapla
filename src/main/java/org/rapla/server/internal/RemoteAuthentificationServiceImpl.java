package org.rapla.server.internal;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Context;

import org.rapla.entities.User;
import org.rapla.framework.RaplaException;
import org.rapla.framework.logger.Logger;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;
import org.rapla.jsonrpc.common.FutureResult;
import org.rapla.jsonrpc.common.ResultImpl;
import org.rapla.jsonrpc.common.VoidResult;
import org.rapla.server.RemoteSession;
import org.rapla.storage.dbrm.LoginCredentials;
import org.rapla.storage.dbrm.LoginTokens;
import org.rapla.storage.dbrm.RemoteAuthentificationService;

@DefaultImplementation(context=InjectionContext.server, of=RemoteAuthentificationService.class)
public class RemoteAuthentificationServiceImpl extends RaplaAuthentificationService implements RemoteAuthentificationService
{
    @Inject
    RemoteSession session;
    private final HttpServletRequest request;

    @Inject
    public RemoteAuthentificationServiceImpl(@Context HttpServletRequest request )
    {
        this.request = request;
    }

    public Logger getLogger()
    {
        return session.getLogger();
    }

    @Override public FutureResult<VoidResult> logout()
    {
        try
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
        catch (RaplaException ex)
        {
            return new ResultImpl<VoidResult>(ex);
        }
        return ResultImpl.VOID;
    }

    @Override public FutureResult<LoginTokens> login(String username, String password, String connectAs)
    {
        LoginCredentials loginCredentials = new LoginCredentials(username, password, connectAs);
        return auth(loginCredentials);
    }

    @Override public FutureResult<LoginTokens> auth(LoginCredentials credentials)
    {
        try
        {
            User user = getUserFromCredentials(credentials);
            LoginTokens generateAccessToken = tokenHandler.generateAccessToken(user);
            return new ResultImpl<LoginTokens>(generateAccessToken);
        }
        catch (RaplaException ex)
        {
            return new ResultImpl<LoginTokens>(ex);
        }
    }

    @Override public FutureResult<String> getRefreshToken()
    {
        try
        {
            User user = getValidUser(session, request);
            String refreshToken = tokenHandler.getRefreshToken(user);
            return new ResultImpl<String>(refreshToken);
        }
        catch (RaplaException ex)
        {
            return new ResultImpl<String>(ex);
        }
    }

    @Override public FutureResult<String> regenerateRefreshToken()
    {
        try
        {
            User user = getValidUser(session, request);
            String refreshToken = tokenHandler.regenerateRefreshToken(user);
            return new ResultImpl<String>(refreshToken);
        }
        catch (Exception ex)
        {
            return new ResultImpl<String>(ex);
        }
    }

    @Override public FutureResult<LoginTokens> refresh(String refreshToken)
    {
        try
        {
            LoginTokens refresh = tokenHandler.refresh(refreshToken);
            return new ResultImpl<LoginTokens>(refresh);
        }
        catch (Exception ex)
        {
            return new ResultImpl<LoginTokens>(ex);
        }
    }


}
