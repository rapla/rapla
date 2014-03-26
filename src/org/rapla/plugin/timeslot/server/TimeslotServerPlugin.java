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
package org.rapla.plugin.timeslot.server;

import org.rapla.framework.Configuration;
import org.rapla.framework.PluginDescriptor;
import org.rapla.plugin.timeslot.TimeslotPlugin;
import org.rapla.plugin.timeslot.TimeslotProvider;
import org.rapla.server.RaplaServerExtensionPoints;
import org.rapla.server.ServerServiceContainer;

public class TimeslotServerPlugin implements PluginDescriptor<ServerServiceContainer>
{
    public void provideServices(ServerServiceContainer container, Configuration config) {
    	if ( !config.getAttributeAsBoolean("enabled",  TimeslotPlugin.ENABLE_BY_DEFAULT))
        	return;

       
        container.addContainerProvidedComponent(RaplaServerExtensionPoints.HTML_CALENDAR_VIEW_EXTENSION,CompactHTMLDayViewFactory.class);
        container.addContainerProvidedComponent(RaplaServerExtensionPoints.HTML_CALENDAR_VIEW_EXTENSION,CompactHTMLWeekViewFactory.class);
        container.addContainerProvidedComponent(TimeslotProvider.class,TimeslotProvider.class,config );
    }

}

