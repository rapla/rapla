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

import org.rapla.entities.configuration.RaplaMap;
import org.rapla.entities.domain.Allocatable;
import org.rapla.framework.TypedComponentRole;

/** Users can subscribe for allocation change notifications for selected resources or persons.*/

public interface NotificationPlugin
{
    String PLUGIN_ID ="org.rapla.plugin.notification";
    TypedComponentRole<Boolean> NOTIFY_IF_OWNER_CONFIG = new TypedComponentRole<>(PLUGIN_ID + ".notify_if_owner");
	TypedComponentRole<RaplaMap<Allocatable>> ALLOCATIONLISTENERS_CONFIG = new TypedComponentRole<>(PLUGIN_ID + ".allocationlisteners");


}

