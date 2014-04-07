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

import org.rapla.RaplaMainContainer;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.configuration.internal.PreferencesImpl;
import org.rapla.facade.RaplaComponent;
import org.rapla.framework.DefaultConfiguration;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaContextException;
import org.rapla.framework.RaplaException;
import org.rapla.plugin.mail.MailConfigService;
import org.rapla.plugin.mail.MailPlugin;
import org.rapla.server.RaplaKeyStorage;
import org.rapla.server.RemoteMethodFactory;
import org.rapla.server.RemoteSession;
import org.rapla.storage.RaplaSecurityException;

public class RaplaConfigServiceImpl extends RaplaComponent implements RemoteMethodFactory<MailConfigService>
{
	RaplaKeyStorage keyStore;
	public RaplaConfigServiceImpl( RaplaContext context) throws RaplaContextException  {
		super( context );
		keyStore = context.lookup( RaplaKeyStorage.class);
	}
        
	public MailConfigService createService(final RemoteSession remoteSession) {
		return new MailConfigService()
		{
			public boolean isExternalConfigEnabled() throws RaplaException 
			{
				return getContext().has(RaplaMainContainer.ENV_RAPLAMAIL);
			}
			
			@SuppressWarnings("deprecation")
            @Override
			public DefaultConfiguration getConfig() throws RaplaException {
			    User user = remoteSession.getUser();
                if ( !user.isAdmin())
                {
                    throw new RaplaSecurityException("Access only for admin users");
                }
		        Preferences preferences = getQuery().getSystemPreferences();
		        DefaultConfiguration config = preferences.getEntry( MailPlugin.MAILSERVER_CONFIG);
		        if ( config == null)
		        {
		            config = (DefaultConfiguration) ((PreferencesImpl)preferences).getOldPluginConfig(MailPlugin.class.getName());
		        }
			    return config;
			}
			
			@Override
			public void testMail(DefaultConfiguration config, String defaultSender) throws RaplaException {
			    
                User user = remoteSession.getUser();
                if ( !user.isAdmin())
                {
                    throw new RaplaSecurityException("Access only for admin users");
                }
			    String subject ="Rapla Test Mail";
			    String mailBody ="If you receive this mail the rapla mail settings are successfully configured.";
			    MailInterface test = getService(MailInterface.class);
			    
                String recipient = user.getEmail();
                if (test instanceof MailapiClient)
                {
                    ((MailapiClient)test).sendMail(defaultSender, recipient, subject, mailBody, config);
                }
                else
			    {
                    test.sendMail(defaultSender,recipient, subject, mailBody);
			    }
			}
			
//			@Override
//			public LoginInfo getLoginInfo() throws RaplaException {
//				User user = remoteSession.getUser();
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
//				User user = remoteSession.getUser();
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
			
		};
				
	}


		
  
}

