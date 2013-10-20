/*--------------------------------------------------------------------------*
 | Copyright (C) 2006 Christopher Kohlhaas                                  |
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

import org.rapla.framework.Configuration;
import org.rapla.framework.PluginDescriptor;
import org.rapla.framework.RaplaContextException;
import org.rapla.framework.logger.Logger;
import org.rapla.plugin.mail.MailConfigService;
import org.rapla.plugin.mail.MailToUserInterface;
import org.rapla.server.ServerServiceContainer;

/** Provides the MailToUserInterface and the MailInterface for sending mails.
 * The MailInterface can only be used on a machine that can connect to the mailserver.
 * While the MailToUserInterface can be called from a client, it redirects the mail request to
 * the server, which must be able to connect to the mailserver.
 *
 * Example 1:
 *
 * <code>
 *  MailToUserInterface mail = getContext().loopup( MailToUserInterface.class );
 *  mail.sendMail( subject, body );
 * </code>
 *
 * Example 2:
 *
 * <code>
 *  MailInterface mail = getContext().loopup( MailInterface.class );
 *  mail.sendMail( senderMail, recipient, subject, body );
 * </code>

 * @see MailInterface
 * @see MailToUserInterface
 */
public class MailServerPlugin implements PluginDescriptor<ServerServiceContainer>
{

    public void provideServices(ServerServiceContainer container, Configuration config) throws RaplaContextException {
        container.addRemoteMethodFactory(MailConfigService.class,RaplaConfigServiceImpl.class);

    	if ( !config.getAttributeAsBoolean("enabled", false) )
        	return;
        
        Class<? extends  MailInterface> mailClass = null;
        String mailClassConfig =config.getChild("mailinterface").getValue( null);
        if (  mailClassConfig != null)
        {
            try {
                @SuppressWarnings("unchecked")
                Class<? extends MailInterface> forName = (Class<? extends MailInterface>) Class.forName( mailClassConfig);
                mailClass = forName;
            } catch (Exception e) {
                container.getContext().lookup(Logger.class).error( "Error loading mailinterface " +e.getMessage() );
            }
        }
        if ( mailClass == null)
        {
            mailClass = MailapiClient.class;
        }
        
        container.addContainerProvidedComponent( MailInterface.class, mailClass , config);
        // only add mail service on localhost
        container.addRemoteMethodFactory(MailToUserInterface.class,RaplaMailToUserOnLocalhost.class);
        container.addContainerProvidedComponent( MailToUserInterface.class, RaplaMailToUserOnLocalhost.class);
    }

}

