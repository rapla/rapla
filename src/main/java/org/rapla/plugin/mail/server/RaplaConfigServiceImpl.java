/*--------------------------------------------------------------------------*
 | Copyright (C) 2014 Christopher Kohlhaas                                  |
 |                                                                          |
 | This program is free software; you can redistribute it and/or modify     |
 | it under the terms of the GNU General Public License as published by the |
 | Free Software Foundation. A copy of the license has been included with   |
 | these distribution in the COPYING file, if not go to www.fsf.org         |
 |                                                                          |
 | As a special exception, you are granted the permissions to link this     |
 | program with every library, which license fulfills the Open Source       |
 | Definition as published by the Open Source Initiative (OSI).             |
 *--------------------------------------------------------------------------*/
package org.rapla.plugin.mail.server;

import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.configuration.internal.PreferencesImpl;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.DefaultConfiguration;
import org.rapla.framework.RaplaException;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;
import org.rapla.plugin.mail.MailConfigService;
import org.rapla.plugin.mail.MailPlugin;
import org.rapla.server.RaplaKeyStorage;
import org.rapla.server.RemoteSession;
import org.rapla.server.ServerService;
import org.rapla.storage.RaplaSecurityException;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Context;

@DefaultImplementation(context = InjectionContext.server, of = MailConfigService.class)
public class RaplaConfigServiceImpl implements MailConfigService
{
    @Inject
    RaplaKeyStorage keyStore;
    @Inject
    RemoteSession remoteSession;
    @Inject
    @Named(ServerService.ENV_RAPLAMAIL_ID)
    Provider<Object> externalMailSession;

    @Inject
    RaplaFacade facade;
    @Inject
    MailInterface mailInterface;
    private final HttpServletRequest request;

    @Inject
    public RaplaConfigServiceImpl(@Context HttpServletRequest request)
    {
        this.request = request;
    }

    @Override
    public boolean isExternalConfigEnabled()
    {
        try
        {
            final Object o = externalMailSession.get();
            return o != null;
        }
        catch (NullPointerException ex)
        {
            return false;
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public DefaultConfiguration getConfig() throws RaplaException
    {
        User user = remoteSession.checkAndGetUser(request);
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

        User user = remoteSession.checkAndGetUser(request);
        if (!user.isAdmin())
        {
            throw new RaplaSecurityException("Access only for admin users");
        }
        String subject = "Rapla Test Mail";
        String mailBody = "If you receive this mail the rapla mail settings are successfully configured.";
        MailInterface test = mailInterface;

        String recipient = user.getEmail();
        if (test instanceof MailapiClient)
        {
            // ignore config if
            if (isExternalConfigEnabled())
            {
                test.sendMail(defaultSender, recipient, subject, mailBody);
            }
            else
            {
                ((MailapiClient) test).sendMail(defaultSender, recipient, subject, mailBody, config);
            }
        }
        else
        {
            test.sendMail(defaultSender, recipient, subject, mailBody);
        }
    }

    //			@Override
    //			public LoginInfo getLoginInfo() throws RaplaException {
    //				User user = remoteSession.getUserFromRequest();
    //				if ( user == null || !user.isAdmin())
    //				{
    //					throw new RaplaSecurityException("Only admins can get mailserver login info");
    //				}
    //				org.rapla.server.RaplaKeyStorage.LoginInfo secrets = keyStore.getSecrets( null, MailPlugin.MAILSERVER_CONFIG);
    //				LoginInfo result = new LoginInfo();
    //				if ( secrets != null)
    //				{
    //					result.username = secrets.login;
    //					result.password = secrets.secret;
    //				}
    //				return result;
    //			}
    //
    //			@Override
    //			public void setLogin(String username, String password) throws RaplaException {
    //				User user = remoteSession.getUserFromRequest();
    //				if ( user == null || !user.isAdmin())
    //				{
    //					throw new RaplaSecurityException("Only admins can set mailserver login info");
    //				}
    //				if ( username.length() == 0 && password.length() == 0)
    //				{
    //					keyStore.removeLoginInfo(null, MailPlugin.MAILSERVER_CONFIG);
    //				}
    //				else
    //				{
    //					keyStore.storeLoginInfo( null, MailPlugin.MAILSERVER_CONFIG, username, password);
    //				}
    //			}

}

