package org.rapla.server.spring.web;

import jakarta.servlet.http.HttpServletRequest;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.configuration.internal.PreferencesImpl;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.DefaultConfiguration;
import org.rapla.framework.RaplaException;
import org.rapla.plugin.mail.MailConfigService;
import org.rapla.plugin.mail.MailPlugin;
import org.rapla.plugin.mail.server.MailInterface;
import org.rapla.plugin.mail.server.MailapiClient;
import org.rapla.server.RemoteSession;
import org.rapla.server.ServerService;
import org.rapla.storage.RaplaSecurityException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.RestController;

import java.util.function.Supplier;

@RestController
@ConditionalOnBean(RemoteSession.class)
public class MailConfigController implements MailConfigService
{
    private final RemoteSession session;
    private final RaplaFacade facade;
    private final MailInterface mailInterface;
    private final Supplier<Object> externalMailSession;
    private final HttpServletRequest request;

    public MailConfigController(RemoteSession session,
                                 RaplaFacade facade,
                                 MailInterface mailInterface,
                                 @Qualifier(ServerService.ENV_RAPLAMAIL_ID) Supplier<Object> externalMailSession,
                                 HttpServletRequest request)
    {
        this.session = session;
        this.facade = facade;
        this.mailInterface = mailInterface;
        this.externalMailSession = externalMailSession;
        this.request = request;
    }

    @Override
    public boolean isExternalConfigEnabled()
    {
        try
        {
            return externalMailSession.get() != null;
        }
        catch (NullPointerException ex)
        {
            return false;
        }
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
        Preferences preferences = facade.getSystemPreferences();
        DefaultConfiguration config = preferences.getEntry(MailPlugin.MAILSERVER_CONFIG);
        if (config == null)
        {
            config = (DefaultConfiguration) ((PreferencesImpl) preferences).getOldPluginConfig(MailPlugin.class.getName());
        }
        return config;
    }

    @Override
    public void testMail(DefaultConfiguration config, String defaultSender) throws RaplaException
    {
        User user = session.checkAndGetUser(request);
        if (!user.isAdmin())
        {
            throw new RaplaSecurityException("Access only for admin users");
        }
        String subject = "Rapla Test Mail";
        String mailBody = "If you receive this mail the rapla mail settings are successfully configured.";
        String recipient = user.getEmail();
        if (mailInterface instanceof MailapiClient)
        {
            if (isExternalConfigEnabled())
            {
                mailInterface.sendMail(defaultSender, recipient, subject, mailBody);
            }
            else
            {
                ((MailapiClient) mailInterface).sendMail(defaultSender, recipient, subject, mailBody, config);
            }
        }
        else
        {
            mailInterface.sendMail(defaultSender, recipient, subject, mailBody);
        }
    }
}
