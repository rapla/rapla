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
package org.rapla.plugin.autoexport;

import org.rapla.entities.configuration.CalendarModelConfiguration;
import org.rapla.entities.configuration.RaplaMap;
import org.rapla.framework.TypedComponentRole;

public class AutoExportPlugin
{
	public static final String CALENDAR_GENERATOR = "calendar";
    public static final TypedComponentRole<RaplaMap<CalendarModelConfiguration>> PLUGIN_ENTRY = CalendarModelConfiguration.EXPORT_ENTRY;
    public static final String HTML_EXPORT= PLUGIN_ENTRY + ".selected";
    public static final String SHOW_CALENDAR_LIST_IN_HTML_MENU = "show_calendar_list_in_html_menu";
    public static final boolean ENABLE_BY_DEFAULT = true;

    public static final String PLUGIN_ID ="org.rapla.plugin.autoexport";
}