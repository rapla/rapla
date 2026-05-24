package org.rapla.server.internal;

import org.rapla.RaplaResources;
import org.rapla.entities.Category;
import org.rapla.entities.Entity;
import org.rapla.entities.User;
import org.rapla.entities.domain.Permission;
import org.rapla.entities.internal.UserImpl;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.framework.RaplaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.rapla.server.AuthenticationStore;
import org.rapla.server.RemoteSession;
import org.rapla.storage.CachableStorageOperator;
import org.rapla.storage.PermissionController;
import org.rapla.storage.RaplaSecurityException;
import org.rapla.storage.dbrm.LoginCredentials;

import org.springframework.beans.factory.annotation.Autowired;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RaplaAuthentificationService
{
    private static final Logger LOGGER = LoggerFactory.getLogger(RaplaAuthentificationService.class);
    @Autowired
    RaplaResources i18n;
    @Autowired
    TokenHandler tokenHandler;
    @Autowired
    Set<AuthenticationStore> authenticationStores;
    @Autowired
    CachableStorageOperator operator;

    private static boolean passwordCheckDisabled = false;

    @Autowired
    public RaplaAuthentificationService()
    {
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
            user = authenticate(username, password, connectAs);
        }
        checkConnectAsRights(user, username, connectAs);
        return user;
    }

    private void checkConnectAsRights(User user, String username, String connectAs) throws RaplaException {
        if (connectAs != null && connectAs.length() > 0)
        {
            final User user1 = operator.getUser(username);
            if (!PermissionController.canAdminUser(user1, user))
            {
                throw new RaplaSecurityException("Non admin user is requesting switchToUser permission!");
            }
        }
    }

    public User getUserWithPassword(String username, String password) throws RaplaException
    {
        String connectAs = null;
        User user = authenticate(username, password, connectAs);
        return user;
    }

    public User authenticate(String username, String password, String connectAs) throws RaplaException
    {
        User user = null;
        String toConnect = connectAs != null && !connectAs.isEmpty() ? connectAs : username;
        LOGGER.info("User '{}' is requesting login.", username);
        AuthenticationStore authenticationStoreSuccessfull = null;
        for (AuthenticationStore authenticationStore : authenticationStores)
        {
            LOGGER.info("Checking external authentifiction for user {}", username);
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
                LOGGER.error(ex.getMessage(), ex);
            }
        }

        if (authenticationStoreSuccessfull != null)
        {
            //@SuppressWarnings("unchecked")
            user = operator.getUser(username);
            if (user == null)
            {
                LOGGER.info("Successfull for User {}.Creating new Rapla user.", username);
                java.time.LocalDateTime now = operator.getCurrentTimestamp();
                UserImpl newUser = new UserImpl(now, now);
                final ReferenceInfo<User> userReferenceInfo = operator.createIdentifier(User.class, 1).get(0);
                newUser.setId(userReferenceInfo.getId());
                newUser.setResolver( operator);
                user = newUser;
            }
            else
            {
                Set<Entity> singleton = Collections.singleton(user);
                Map<Entity,Entity> editList = operator.editObjects(singleton, null);
                user = (User) editList.values().iterator().next();
            }

            boolean initUser;
            try
            {
                Category groupCategory = operator.getSuperCategory().getCategory(Permission.GROUP_CATEGORY_KEY);
                LOGGER.debug("Looking for update for rapla user '{}' from external source.", username);
                initUser = authenticationStoreSuccessfull.initUser(user, username, password, groupCategory);
            }
            catch (RaplaSecurityException ex)
            {
                throw new RaplaSecurityException( i18n.getString("error.login")+ex.getMessage());
            }
            // PRD 050: stamp the authentication source so the user is gated
            // from self-changing password / name / email locally. Format
            // mirrors the OAuth path: "ldap" today (single-store deployments);
            // multi-LDAP-store setups can append the store id later if needed.
            // Don't overwrite an existing marker — once set (e.g. by an
            // earlier OAuth login), the source is sticky until admin
            // disconnects.
            if (user.getAuthenticationSource() == null)
            {
                user.setAuthenticationSource("ldap");
                initUser = true;
            }
            if (initUser)
            {
                LOGGER.info("Udating rapla user '{}' from external source.", username);
                List<Entity<?>> storeList = new ArrayList<>(1);
                storeList.add(user);
                List<ReferenceInfo<Entity<?>>> removeList = Collections.emptyList();

                operator.storeAndRemove(storeList, removeList, null);
            }
            else
            {
                LOGGER.info("User '{}' already up to date", username);
            }
        }
        else
        {
            if (authenticationStores.size() == 0)
            {
                LOGGER.info("Check password for {}", username);
            }
            else
            {
                LOGGER.info("Now trying to authenticate with local store '{}'", username);
            }
            operator.authenticate(username, password);
        }

        if (connectAs != null && connectAs.length() > 0 && user != null)
        {
            checkConnectAsRights(user, username, connectAs);
            LOGGER.info("Successfull login for '{}' acts as user '{}'", username, connectAs);
        }
        else
        {
            LOGGER.info("Successfull login for '{}'", username);
        }
        user = operator.getUser(toConnect);

        if (user == null)
        {
            throw new RaplaException("User with username '" + toConnect + "' not found");
        }
        return user;
    }

}
