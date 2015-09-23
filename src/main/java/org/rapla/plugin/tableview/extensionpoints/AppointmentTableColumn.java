/*--------------------------------------------------------------------------*
 | Copyright (C) 2012 Christopher Kohlhaas                                  |
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
package org.rapla.plugin.tableview.extensionpoints;

import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.inject.ExtensionPoint;
import org.rapla.inject.InjectionContext;
import org.rapla.plugin.tableview.RaplaTableColumn;
import org.rapla.plugin.tableview.TableViewPlugin;

@ExtensionPoint(context = InjectionContext.all, id= TableViewPlugin.APPOINTMENT_TABLE_COLUMN)
public interface AppointmentTableColumn<C> extends RaplaTableColumn<AppointmentBlock, C>
{
}

