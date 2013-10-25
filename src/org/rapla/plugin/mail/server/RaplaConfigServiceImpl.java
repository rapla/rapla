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

import org.rapla.RaplaMainContainer;
import org.rapla.facade.RaplaComponent;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.plugin.mail.MailConfigService;
import org.rapla.server.RemoteMethodFactory;
import org.rapla.server.RemoteSession;

public class RaplaConfigServiceImpl extends RaplaComponent implements MailConfigService, RemoteMethodFactory<MailConfigService>
{
        public RaplaConfigServiceImpl( RaplaContext context)  {
            super( context );
        }
        
        public MailConfigService createService(RemoteSession remoteSession) {
            return this;
        }

        public boolean isExternalConfigEnabled() throws RaplaException 
		{
			return getContext().has(RaplaMainContainer.ENV_RAPLAMAIL);
		}

		
  
}

