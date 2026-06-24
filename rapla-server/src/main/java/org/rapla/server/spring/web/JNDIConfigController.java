package org.rapla.server.spring.web;

import jakarta.servlet.http.HttpServletRequest;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.configuration.internal.PreferencesImpl;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.DefaultConfiguration;
import org.rapla.framework.RaplaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.rapla.plugin.jndi.JNDIPlugin;
import org.rapla.plugin.jndi.internal.JNDIConfig;
import org.rapla.plugin.jndi.server.JNDIAuthenticationStore;
import org.rapla.server.ApiKeyScopeContext;
import org.rapla.server.RemoteSession;
import org.rapla.storage.RaplaSecurityException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnBean(RemoteSession.class)
@ConditionalOnProperty(prefix = "rapla.services", name = "org.rapla.plugin.jndi", matchIfMissing = true)
public class JNDIConfigController implements JNDIConfig
{
    private static final Logger LOGGER = LoggerFactory.getLogger(JNDIConfigController.class);
    private final RaplaFacade facade;
    private final RemoteSession session;
    private final HttpServletRequest request;

    public JNDIConfigController(RaplaFacade facade,
                                RemoteSession session,
                                HttpServletRequest request)
    {
        this.facade = facade;
        this.session = session;
        this.request = request;
    }

    @Override
    public boolean test(MailTestRequest job) throws RaplaException
    {
        DefaultConfiguration config = job.getConfig();
        String username = job.getUsername();
        String password = job.getPassword();
        User user = session.checkAndGetUser(request);
        if (!user.isAdmin())
        {
            throw new RaplaSecurityException("Access only for admin users");
        }
        JNDIAuthenticationStore testStore = new JNDIAuthenticationStore(facade);
        testStore.initWithConfig(config);
        LOGGER.info("Test of JNDI Plugin started");
        boolean authenticate;
        if (password == null || password.equals(""))
        {
            throw new RaplaException("LDAP Plugin doesnt accept empty passwords.");
        }
        try
        {
            authenticate = testStore.authenticate(username, password);
        }
        catch (Exception e)
        {
            throw new RaplaException(e);
        }
        finally
        {
            testStore.dispose();
        }
        if (!authenticate)
        {
            throw new RaplaSecurityException("Can establish connection but can't authenticate test user " + username);
        }
        LOGGER.info("Test of JNDI Plugin successfull");
        return true;
    }

    @Override
    @SuppressWarnings("deprecation")
    public DefaultConfiguration getConfig() throws RaplaException
    {
        User user = session.checkAndGetUser(request);
        if (!user.isAdmin())
        {
            throw new RaplaSecurityException("Access only for admin users");
        }
        // server-side LDAP bind credentials — interactive admin session only, never an api-key.
        ApiKeyScopeContext.requireInteractiveSession("ldap config");
        Preferences preferences = facade.getSystemPreferences();
        DefaultConfiguration config = preferences.getEntry(JNDIPlugin.JNDISERVER_CONFIG);
        if (config == null)
        {
            config = (DefaultConfiguration) ((PreferencesImpl) preferences).getOldPluginConfig(JNDIPlugin.class.getName());
        }
        return config;
    }
}
