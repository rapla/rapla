
/*--------------------------------------------------------------------------*
 | Copyright (C) 2006  Christopher Kohlhaas                                 |
 |                                                                          |
 | This program is free software; you can redistribute it and/or modify     |
 | it under the terms of the GNU General Public License as published by the |
 | Free Software Foundation. A copyReservations of the license has been included with   |
 | these distribution in the COPYING file, if not go to www.fsf.org         |
 |                                                                          |
 | As a special exception, you are granted the permissions to link this     |
 | program with every library, which license fulfills the Open Source       |
 | Definition as published by the Open Source Initiative (OSI).             |
 *--------------------------------------------------------------------------*/

package org.rapla.client.swing.extensionpoints;

import org.rapla.client.swing.SwingCalendarView;
import org.rapla.facade.CalendarModel;
import org.rapla.framework.RaplaException;
import org.rapla.inject.ExtensionPoint;
import org.rapla.inject.InjectionContext;

import javax.swing.Icon;

@ExtensionPoint(context = InjectionContext.swing, id="week")
public interface SwingViewFactory
{
    // instance scope
    SwingCalendarView createSwingView(CalendarModel model, boolean editable, boolean printing) throws RaplaException;
    boolean isEnabled();
    String getViewId();
    /** return the key that is responsible for placing the view in the correct position in the drop down selection menu*/
    String getMenuSortKey();
    String getName();
    Icon getIcon();
}
