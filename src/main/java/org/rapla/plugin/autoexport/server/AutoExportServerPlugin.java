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
package org.rapla.plugin.autoexport.server;

import org.rapla.framework.Configuration;
import org.rapla.framework.PluginDescriptor;
import org.rapla.framework.RaplaContextException;
import org.rapla.server.RaplaServerExtensionPoints;
import org.rapla.server.ServerServiceContainer;

public class AutoExportServerPlugin implements PluginDescriptor<ServerServiceContainer>
{
    public void provideServices(ServerServiceContainer container, Configuration config) throws RaplaContextException {

        //container.addWebpage(AutoExportPlugin.CALENDAR_GENERATOR,CalendarPageGenerator.class);
        //RaplaResourcePageGenerator resourcePageGenerator = container.getContext().lookup(RaplaResourcePageGenerator.class);
        // registers the standard calendar files
        
        //resourcePageGenerator.registerResource( "calendar.css", "text/css", this.getClass().getResource("/org/rapla/plugin/autoexport/server/calendar.css"));
        // look if we should add a menu entry of exported lists
        //if (config.getAttributeAsBoolean(AutoExportPlugin.SHOW_CALENDAR_LIST_IN_HTML_MENU, false))
        {
        	container.addWebpage("calendarlist",CalendarListPageGenerator.class);
        	container.addContainerProvidedComponent( RaplaServerExtensionPoints.HTML_MAIN_MENU_EXTENSION_POINT, ExportMenuEntry.class);
        }
    }

}