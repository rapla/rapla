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
package org.rapla.plugin.mail;

import org.rapla.client.ClientServiceContainer;
import org.rapla.client.RaplaClientExtensionPoints;
import org.rapla.entities.configuration.RaplaConfiguration;
import org.rapla.framework.Configuration;
import org.rapla.framework.PluginDescriptor;
import org.rapla.framework.RaplaContextException;
import org.rapla.framework.TypedComponentRole;
import org.rapla.plugin.mail.client.MailOption;
import org.rapla.plugin.mail.internal.RaplaMailToUserOnServer;
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
 * @see MailToUserInterface
 */
public class MailPlugin implements PluginDescriptor<ClientServiceContainer>
{
	public static final boolean ENABLE_BY_DEFAULT = false;
    public static final TypedComponentRole<String> DEFAULT_SENDER_ENTRY = new TypedComponentRole<String>("org.rapla.plugin.mail.DefaultSender");
    public static final TypedComponentRole<RaplaConfiguration> MAILSERVER_CONFIG = new TypedComponentRole<RaplaConfiguration>("org.rapla.plugin.mail.server.Config");
 
    public void provideServices(ClientServiceContainer container, Configuration config) throws RaplaContextException {
        container.addContainerProvidedComponent( RaplaClientExtensionPoints.PLUGIN_OPTION_PANEL_EXTENSION,MailOption.class);
        if ( !config.getAttributeAsBoolean("enabled", ENABLE_BY_DEFAULT) )
        	return;
        
        container.addContainerProvidedComponent( MailToUserInterface.class, RaplaMailToUserOnServer.class);
    }

    
}

