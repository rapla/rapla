package org.rapla.server.internal;

import org.rapla.RaplaResources;
import org.rapla.entities.User;
import org.rapla.framework.RaplaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.rapla.server.AuthenticationStore;
import org.rapla.server.IdentityClaims;
import org.rapla.server.RemoteSession;
import org.rapla.server.UserProvisioner;
import org.rapla.storage.CachableStorageOperator;
import org.rapla.storage.RaplaSecurityException;
import org.rapla.storage.dbrm.LoginCredentials;

import jakarta.servlet.http.HttpServletRequest;

public class RaplaAuthentificationService
{
    private static final Logger LOGGER = LoggerFactory.getLogger(RaplaAuthentificationService.class);
    final RaplaResources i18n;
    /** At most one external auth source — dhbwrapla NTLM, rapla JNDI/LDAP, or
     *  similar plugin. Vanilla rapla has none, so this is {@code null} and
     *  authentication falls through to the local-DB path. Multiple
     *  {@code @Bean AuthenticationStore} declarations are rejected at startup
     *  (Spring's {@code ObjectProvider.getIfAvailable()} throws on ambiguity)
     *  — see {@link ServerServiceConfig#raplaAuthentificationService}. */
    final AuthenticationStore authenticationStore;
    final CachableStorageOperator operator;
    /** PRD 050 Phase 8 — provisioning lives behind an SPI bean.
     *  {@link org.rapla.server.internal.DefaultUserProvisioner} ships as the
     *  {@code @ConditionalOnMissingBean} default; plugins (dhbwrapla today)
     *  contribute their own to swap field-policy / group-resolution. */
    final UserProvisioner userProvisioner;

    /** PRD 054 (2026-05-25) — standalone trial install: skip password verification
     *  entirely (single-user, no auth). Bound from the Spring property
     *  {@code rapla.password-check-disabled}. Defaults false; the {@code standalone}
     *  Spring profile flips it true via {@code application-standalone.yml}. */
    private final boolean passwordCheckDisabled;

    public RaplaAuthentificationService(RaplaResources i18n,
                                        CachableStorageOperator operator,
                                        UserProvisioner userProvisioner,
                                        AuthenticationStore authenticationStore,
                                        @org.springframework.beans.factory.annotation.Value("${rapla.password-check-disabled:false}") boolean passwordCheckDisabled)
    {
        this.i18n = i18n;
        this.operator = operator;
        this.userProvisioner = userProvisioner;
        this.authenticationStore = authenticationStore;
        this.passwordCheckDisabled = passwordCheckDisabled;
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
            // PRD 050 Phase 8: extract claims (read-only) → hand to provisioner
            // (single write). Replaces the previous inline find-or-create +
            // initUser mutation + hardcoded "ldap" stamp block. The auth store
            // owns the source label now (via IdentityClaims.sourceId) — fixes
            // the pre-Phase-8 mislabel where DHBW users got stamped "ldap"
            // regardless of which store ran.
            IdentityClaims claims;
            try
            {
                claims = authenticationStoreSuccessfull.extractClaims(username, password);
            }
            catch (RaplaSecurityException ex)
            {
                throw new RaplaSecurityException(i18n.getString("error.login") + ex.getMessage());
            }
            userProvisioner.provision(claims);
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
        User user = operator.getUser(username);

        if (user == null)
        {
            throw new RaplaException("User with username '" + username + "' not found");
        }
        return user;
    }

}
