package org.rapla.server.internal;

import org.rapla.RaplaResources;
import org.rapla.entities.Category;
import org.rapla.entities.Entity;
import org.rapla.entities.User;
import org.rapla.entities.domain.Permission;
import org.rapla.entities.internal.UserImpl;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.framework.RaplaException;
import org.rapla.logger.Logger;
import org.rapla.server.AuthenticationStore;
import org.rapla.server.RemoteSession;
import org.rapla.storage.CachableStorageOperator;
import org.rapla.storage.PermissionController;
import org.rapla.storage.RaplaSecurityException;
import org.rapla.storage.dbrm.LoginCredentials;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Singleton
public class RaplaAuthentificationService
{
    @Inject
    RaplaResources i18n;
    @Inject
    TokenHandler tokenHandler;
    @Inject
    Set<AuthenticationStore> authenticationStores;
    @Inject
    CachableStorageOperator operator;
    @Inject
    Logger logger;

    private static boolean passwordCheckDisabled = false;

    @Inject
    public RaplaAuthentificationService()
    {
    }

    public Logger getLogger()
    {
        return logger;
    }

    public static void setPasswordCheckDisabled(boolean passwordCheckDisabled)
    {
        RaplaAuthentificationService.passwordCheckDisabled = passwordCheckDisabled;
    }

    protected User getValidUser(final RemoteSession session, HttpServletRequest request) throws RaplaSecurityException
    {
        User user = session.checkAndGetUser(request);
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
            final User user1 = operator.getUser(username);
            if (!PermissionController.canAdminUser(user1, user))
            {
                throw new RaplaSecurityException("Non admin user is requesting switchToUser permission!");
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

    public User authenticate(String username, String password, String connectAs, Logger logger) throws RaplaException
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
                if ( !authenticationStore.isEnabled())
                {
                    continue;
                }
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
                final ReferenceInfo<User> userReferenceInfo = operator.createIdentifier(User.class, 1).get(0);
                newUser.setId(userReferenceInfo.getId());
                user = newUser;
            }
            else
            {
                Set<Entity> singleton = Collections.singleton((Entity) user);
                Map<Entity,Entity> editList = operator.editObjects(singleton, null);
                user = (User) editList.values().iterator().next();
            }

            boolean initUser;
            try
            {
                Category groupCategory = operator.getSuperCategory().getCategory(Permission.GROUP_CATEGORY_KEY);
                logger.debug("Looking for update for rapla user '" + username + "' from external source.");
                initUser = authenticationStoreSuccessfull.initUser(user, username, password, groupCategory);
            }
            catch (RaplaSecurityException ex)
            {
                throw new RaplaSecurityException(i18n.getString("error.login"));
            }
            if (initUser)
            {
                logger.info("Udating rapla user '" + username + "' from external source.");
                List<Entity<?>> storeList = new ArrayList<>(1);
                storeList.add(user);
                List<ReferenceInfo<Entity<?>>> removeList = Collections.emptyList();

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
