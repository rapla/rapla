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
package org.rapla.plugin.notification;

import org.rapla.client.ClientServiceContainer;
import org.rapla.client.RaplaClientExtensionPoints;
import org.rapla.components.xmlbundle.I18nBundle;
import org.rapla.entities.configuration.RaplaMap;
import org.rapla.entities.domain.Allocatable;
import org.rapla.framework.Configuration;
import org.rapla.framework.PluginDescriptor;
import org.rapla.framework.TypedComponentRole;

/** Users can subscribe for allocation change notifications for selected resources or persons.*/

public class NotificationPlugin implements PluginDescriptor<ClientServiceContainer>
{
    public static final TypedComponentRole<I18nBundle> RESOURCE_FILE = new TypedComponentRole<I18nBundle>(NotificationPlugin.class.getPackage().getName() + ".NotificationResources");
    public static final boolean ENABLE_BY_DEFAULT = false;
	public final static TypedComponentRole<Boolean> NOTIFY_IF_OWNER_CONFIG = new TypedComponentRole<Boolean>("org.rapla.plugin.notification.notify_if_owner");
	public final static TypedComponentRole<RaplaMap<Allocatable>> ALLOCATIONLISTENERS_CONFIG = new TypedComponentRole<RaplaMap<Allocatable>>("org.rapla.plugin.notification.allocationlisteners");

    public void provideServices(ClientServiceContainer container, Configuration config) {
        if ( !config.getAttributeAsBoolean("enabled", ENABLE_BY_DEFAULT) )
        	return;

        container.addResourceFile(RESOURCE_FILE);
        container.addContainerProvidedComponent( RaplaClientExtensionPoints.USER_OPTION_PANEL_EXTENSION, NotificationOption.class);
    }

}

