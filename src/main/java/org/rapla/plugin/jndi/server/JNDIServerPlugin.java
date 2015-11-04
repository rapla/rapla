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
package org.rapla.plugin.jndi.server;

import org.rapla.entities.configuration.RaplaConfiguration;
import org.rapla.facade.ClientFacade;
import org.rapla.framework.Configuration;
import org.rapla.framework.RaplaContextException;
import org.rapla.framework.TypedComponentRole;
import org.rapla.framework.logger.Logger;
import org.rapla.plugin.jndi.JNDIPlugin;
import org.rapla.server.internal.UpdateDataManagerImpl;

public class JNDIServerPlugin  {
    
    private void convertSettings(ClientFacade facade, Logger logger,Configuration config) throws RaplaContextException
    {
        String className = JNDIPlugin.class.getName();
        TypedComponentRole<RaplaConfiguration> newConfKey = JNDIPlugin.JNDISERVER_CONFIG;
        if ( config.getAttributeNames().length > 2)
        {
            UpdateDataManagerImpl.convertToNewPluginConfig(facade,logger, className, newConfKey);
        }
    }
    
}

