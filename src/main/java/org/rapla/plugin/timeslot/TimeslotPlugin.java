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
package org.rapla.plugin.timeslot;

import org.rapla.entities.configuration.RaplaConfiguration;
import org.rapla.framework.TypedComponentRole;

public class TimeslotPlugin
{
    public final static String PLUGIN_ID = "org.rapla.plugin.timeslot";
	public final static boolean ENABLE_BY_DEFAULT = false;
    public final static String DAY_TIMESLOT = "day_timeslot";
    public final static String WEEK_TIMESLOT = "week_timeslot";
    public static final TypedComponentRole<RaplaConfiguration> CONFIG = new TypedComponentRole<RaplaConfiguration>(PLUGIN_ID + ".config");
}

