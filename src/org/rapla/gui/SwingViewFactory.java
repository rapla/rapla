
/*--------------------------------------------------------------------------*
 | Copyright (C) 2006  Christopher Kohlhaas                                 |
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

package org.rapla.gui;

import javax.swing.Icon;

import org.rapla.facade.CalendarModel;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.framework.TypedComponentRole;

public interface SwingViewFactory
{
    public TypedComponentRole<Boolean> PRINT_CONTEXT = new TypedComponentRole<Boolean>("org.rapla.PrintContext");
    public SwingCalendarView createSwingView(RaplaContext context, CalendarModel model, boolean editable) throws RaplaException;
    public String getViewId();
    /** return the key that is responsible for placing the view in the correct position in the drop down selection menu*/
    public String getMenuSortKey();
    public String getName();
    public Icon getIcon();
}
