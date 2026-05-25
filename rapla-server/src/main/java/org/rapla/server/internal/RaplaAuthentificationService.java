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

import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RaplaAuthentificationService
{
    private static final Logger LOGGER = LoggerFactory.getLogger(RaplaAuthentificationService.class);
    final RaplaResources i18n;
    final TokenHandler tokenHandler;
    /** At most one external auth source — dhbwrapla NTLM, rapla JNDI/LDAP, or
     *  similar plugin. Vanilla rapla has none, so this is {@code null} and
     *  authentication falls through to the local-DB path. Multiple
     *  {@code @Bean AuthenticationStore} declarations are rejected at startup
     *  (Spring's {@code ObjectProvider.getIfAvailable()} throws on ambiguity)
     *  — see {@link ServerServiceConfig#raplaAuthentificationService}. */
    final AuthenticationStore authenticationStore;
    final CachableStorageOperator operator;

    private static boolean passwordCheckDisabled = false;

    public RaplaAuthentificationService(RaplaResources i18n,
                                        TokenHandler tokenHandler,
                                        CachableStorageOperator operator,
                                        AuthenticationStore authenticationStore)
    {
        this.i18n = i18n;
        this.tokenHandler = tokenHandler;
        this.operator = operator;
        this.authenticationStore = authenticationStore;
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
        // authenticate() takes String — bridge to char[] at this in-server seam.
        // The String is request-scoped and becomes GC-eligible at end of request.
        String password = credentials.getPassword() == null ? null : new String(credentials.getPassword());

        if (passwordCheckDisabled)
        {
            // don't check passwords in standalone version
            user = operator.getUser(username);
            if (user == null)
            {
                throw new RaplaSecurityException(i18n.getString("error.login"));
            }
        }
        else
        {
            // PRD 029 Phase 5 (2026-05-25): impersonation no longer routes
            // through the password grant. The dedicated /api/auth/impersonate
            // endpoint + dual-slot model on the client (PRD 051 / Phase 5 §7)
            // covers admin "switch to user".
            user = authenticate(username, password);
        }
        return user;
    }


    public User authenticate(String username, String password) throws RaplaException
    {
        // PRD 029 Phase 5 (2026-05-25): dropped the legacy connectAs parameter.
        // Admin "switch to user" is now its own server endpoint
        // (/api/auth/impersonate, PRD 051) — not piggybacked on password login.
        User user = null;
        LOGGER.info("User '{}' is requesting login.", username);
        AuthenticationStore authenticationStoreSuccessfull = null;
        if (authenticationStore != null && authenticationStore.isEnabled())
        {
            LOGGER.info("Checking external authentication for user {}", username);
            try
            {
                if (authenticationStore.authenticate(username, password))
                {
                    authenticationStoreSuccessfull = authenticationStore;
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
            if (authenticationStore == null)
            {
                LOGGER.info("Check password for {}", username);
            }
            else
            {
                LOGGER.info("Now trying to authenticate with local store '{}'", username);
            }
            operator.authenticate(username, password);
        }

        LOGGER.info("Successfull login for '{}'", username);
        user = operator.getUser(username);

        if (user == null)
        {
            throw new RaplaException("User with username '" + username + "' not found");
        }
        return user;
    }

}
