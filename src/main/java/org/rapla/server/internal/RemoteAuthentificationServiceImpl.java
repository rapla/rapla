package org.rapla.server.internal;

import org.rapla.RaplaResources;
import org.rapla.entities.Category;
import org.rapla.entities.Entity;
import org.rapla.entities.User;
import org.rapla.entities.domain.Permission;
import org.rapla.entities.internal.UserImpl;
import org.rapla.framework.RaplaContextException;
import org.rapla.framework.RaplaException;
import org.rapla.framework.logger.Logger;
import org.rapla.gwtjsonrpc.common.FutureResult;
import org.rapla.gwtjsonrpc.common.ResultImpl;
import org.rapla.gwtjsonrpc.common.VoidResult;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;
import org.rapla.server.AuthenticationStore;
import org.rapla.server.RemoteSession;
import org.rapla.storage.CachableStorageOperator;
import org.rapla.storage.RaplaSecurityException;
import org.rapla.storage.StorageOperator;
import org.rapla.storage.dbrm.LoginCredentials;
import org.rapla.storage.dbrm.LoginTokens;
import org.rapla.storage.dbrm.RemoteAuthentificationService;

import javax.inject.Inject;
import java.util.*;

@DefaultImplementation(of = RemoteAuthentificationService.class, context = InjectionContext.server)
public class RemoteAuthentificationServiceImpl implements RemoteAuthentificationService
{
    private static boolean passwordCheckDisabled = false;
    RaplaResources i18n;
    private final RemoteSession session;
    TokenHandler tokenHandler;
    Set<AuthenticationStore> authenticationStores;
    CachableStorageOperator operator;

    @Inject public RemoteAuthentificationServiceImpl(RemoteSession session, TokenHandler tokenHandler, RaplaResources i18n,
            Set<AuthenticationStore> authenticationStores, CachableStorageOperator operator)
    {
        this.session = session;
        this.tokenHandler = tokenHandler;
        this.i18n = i18n;
        this.authenticationStores = authenticationStores;
        this.operator = operator;
    }

    public static void setPasswordCheckDisabled(boolean passwordCheckDisabled)
    {
        RemoteAuthentificationServiceImpl.passwordCheckDisabled = passwordCheckDisabled;
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
                if (session.isAuthentified())
                {
                    User user = session.getUser();
                    if (user != null)
                    {
                        getLogger().getChildLogger("login").info("Request Logout " + user.getUsername());
                    }
                    ((RemoteSessionImpl) session).logout();
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
            User user = getValidUser(session);
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
            User user = getValidUser(session);
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

    private User getValidUser(final RemoteSession session) throws RaplaContextException, RaplaSecurityException
    {
        User user = session.getUser();
        if (user == null)
        {
            throw new RaplaSecurityException(i18n.getString("error.login"));
        }
        return user;
    }

    public User getUserFromCredentials(LoginCredentials credentials) throws RaplaException
    {
        User user;
        String username = credentials.getUsername();
        String password = credentials.getPassword();
        String connectAs = credentials.getConnectAs();

        if (passwordCheckDisabled)
        {
            String toConnect = connectAs != null && !connectAs.isEmpty() ? connectAs : username;
            // don't check passwords in standalone version
            user = operator.getUser(toConnect);
            if (user == null)
            {
                throw new RaplaSecurityException(i18n.getString("error.login"));
            }
        }
        else
        {
            Logger logger = getLogger().getChildLogger("login");
            user = authenticate(username, password, connectAs, logger);
        }
        if (connectAs != null && connectAs.length() > 0)
        {
            if (!operator.getUser(username).isAdmin())
            {
                throw new SecurityException("Non admin user is requesting change user permission!");
            }
        }
        return user;
    }

    public User getUserWithPassword(String username, String password) throws RaplaException
    {
        String connectAs = null;
        User user = authenticate(username, password, connectAs, getLogger());
        return user;
    }

    public User authenticate(String username, String password, String connectAs, Logger logger) throws RaplaException, RaplaSecurityException
    {
        User user;
        String toConnect = connectAs != null && !connectAs.isEmpty() ? connectAs : username;
        logger.info("User '" + username + "' is requesting login.");
        AuthenticationStore authenticationStoreSuccessfull = null;
        for (AuthenticationStore authenticationStore : authenticationStores)
        {
            logger.info("Checking external authentifiction for user " + username);
            try
            {
                boolean authenticateExternal = authenticationStore.authenticate(username, password);
                if (authenticateExternal)
                {
                    authenticationStoreSuccessfull = authenticationStore;
                    break;
                }
            }
            catch (RaplaException ex)
            {
                getLogger().error(ex.getMessage(), ex);
            }
        }

        if (authenticationStoreSuccessfull != null)
        {
            //@SuppressWarnings("unchecked")
            user = operator.getUser(username);
            if (user == null)
            {
                logger.info("Successfull for User " + username + ".Creating new Rapla user.");
                Date now = operator.getCurrentTimestamp();
                UserImpl newUser = new UserImpl(now, now);
                newUser.setId(operator.createIdentifier(User.TYPE, 1)[0]);
                user = newUser;
            }
            else
            {
                Set<Entity> singleton = Collections.singleton((Entity) user);
                Collection<Entity> editList = operator.editObjects(singleton, null);
                user = (User) editList.iterator().next();
            }

            boolean initUser;
            try
            {
                Category groupCategory = operator.getSuperCategory().getCategory(Permission.GROUP_CATEGORY_KEY);
                logger.debug("Looking for update for rapla user '" + username + "' from external source.");
                initUser = authenticationStoreSuccessfull.initUser((User) user, username, password, groupCategory);
            }
            catch (RaplaSecurityException ex)
            {
                throw new RaplaSecurityException(i18n.getString("error.login"));
            }
            if (initUser)
            {
                logger.info("Udating rapla user '" + username + "' from external source.");
                List<Entity> storeList = new ArrayList<Entity>(1);
                storeList.add(user);
                List<Entity> removeList = Collections.emptyList();

                operator.storeAndRemove(storeList, removeList, null);
            }
            else
            {
                logger.info("User '" + username + "' already up to date");
            }
        }
        else
        {
            if (authenticationStores.size() == 0)
            {
                logger.info("Check password for " + username);
            }
            else
            {
                logger.info("Now trying to authenticate with local store '" + username + "'");
            }
            operator.authenticate(username, password);
        }

        if (connectAs != null && connectAs.length() > 0)
        {
            logger.info("Successfull login for '" + username + "' acts as user '" + connectAs + "'");
        }
        else
        {
            logger.info("Successfull login for '" + username + "'");
        }
        user = operator.getUser(toConnect);

        if (user == null)
        {
            throw new RaplaException("User with username '" + toConnect + "' not found");
        }
        return user;
    }

}
