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
package org.rapla.plugin.tempatewizard;

import org.rapla.client.ClientServiceContainer;
import org.rapla.client.RaplaClientExtensionPoints;
import org.rapla.framework.Configuration;
import org.rapla.framework.PluginDescriptor;

public class TempateWizardPlugin implements PluginDescriptor<ClientServiceContainer> {
    public static final String PLUGIN_CLASS = TempateWizardPlugin.class.getName();
    public static boolean ENABLE_BY_DEFAULT = true;
    
    public void provideServices(ClientServiceContainer container, Configuration config) {
    	if ( !config.getAttributeAsBoolean("enabled", ENABLE_BY_DEFAULT) )
        	return;

        container.addContainerProvidedComponent( RaplaClientExtensionPoints.RESERVATION_WIZARD_EXTENSION, TemplateWizard.class);
    }
}

