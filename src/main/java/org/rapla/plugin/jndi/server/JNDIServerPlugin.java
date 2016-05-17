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
import org.rapla.entities.configuration.internal.PreferencesImpl;
import org.rapla.facade.RaplaComponent;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.DefaultConfiguration;
import org.rapla.framework.RaplaException;
import org.rapla.framework.TypedComponentRole;
import org.rapla.logger.Logger;
import org.rapla.storage.xml.RaplaXMLContextException;

public class JNDIServerPlugin  {
    
//    private void convertSettings(RaplaFacade facade, Logger logger,Configuration config)
//    {
//        String className = JNDIPlugin.class.getName();
//        TypedComponentRole<RaplaConfiguration> newConfKey = JNDIPlugin.JNDISERVER_CONFIG;
//        if ( config.getAttributeNames().length > 2)
//        {
//            JNDIServerPlugin.convertToNewPluginConfig(facade, logger, className, newConfKey);
//        }
//    }

    static public void convertToNewPluginConfig(RaplaFacade facade, Logger logger, String className, TypedComponentRole<RaplaConfiguration> newConfKey)
            throws RaplaXMLContextException
    {
        try
        {
            PreferencesImpl clone = (PreferencesImpl) facade.edit(facade.getSystemPreferences());
            RaplaConfiguration entry = clone.getEntry(RaplaComponent.PLUGIN_CONFIG, null);
            if (entry == null)
            {
                return;
            }
            RaplaConfiguration newPluginConfigEntry = entry.clone();
            DefaultConfiguration pluginConfig = (DefaultConfiguration) newPluginConfigEntry.find("class", className);
            // we split the config entry in the plugin config and the new config entry;
            if (pluginConfig != null)
            {
                logger.info("Converting plugin conf " + className + " to preference entry " + newConfKey);
                newPluginConfigEntry.removeChild(pluginConfig);
                boolean enabled = pluginConfig.getAttributeAsBoolean("enabled", false);
                RaplaConfiguration newPluginConfig = new RaplaConfiguration(pluginConfig.getName());
                newPluginConfig.setAttribute("enabled", enabled);
                newPluginConfig.setAttribute("class", className);
                newPluginConfigEntry.addChild(newPluginConfig);
    
                RaplaConfiguration newConfigEntry = new RaplaConfiguration(pluginConfig);
    
                newConfigEntry.setAttribute("enabled", null);
                newConfigEntry.setAttribute("class", null);
    
                clone.putEntry(newConfKey, newConfigEntry);
                clone.putEntry(RaplaComponent.PLUGIN_CONFIG, newPluginConfigEntry);
                facade.store(clone);
            }
        }
        catch (RaplaException ex)
        {
            if (ex instanceof RaplaXMLContextException)
            {
                throw (RaplaXMLContextException)ex;
            }
            throw new RaplaXMLContextException(ex.getMessage(), ex);
        }
    }
    
}

