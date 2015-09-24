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
package org.rapla.plugin.jndi;

import org.rapla.entities.Category;
import org.rapla.entities.configuration.RaplaConfiguration;
import org.rapla.entities.configuration.RaplaMap;
import org.rapla.framework.TypedComponentRole;

public class JNDIPlugin {
    public static final String PLUGIN_ID = "org.rapla.plugin.jndi";
	public final static boolean ENABLE_BY_DEFAULT = false;
    public static final String PLUGIN_NAME = "Ldap or other JNDI Authentication";

    public static final TypedComponentRole<RaplaConfiguration> JNDISERVER_CONFIG = new TypedComponentRole<RaplaConfiguration>(PLUGIN_ID +".server.config");
    public final static TypedComponentRole<RaplaMap<Category>> USERGROUP_CONFIG = new TypedComponentRole<RaplaMap<Category>>(PLUGIN_ID + ".newusergroups");
    
//    public void provideServices(ClientServiceContainer container, Configuration config)
//    {
//        container.addContainerProvidedComponent( RaplaClientExtensionPoints.PLUGIN_OPTION_PANEL_EXTENSION,JNDIOption.class);
//
//    	if ( !config.getAttributeAsBoolean("enabled", ENABLE_BY_DEFAULT) )
//        	return;
//    }

    
}

