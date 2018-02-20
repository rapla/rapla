
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

package org.rapla.server.extensionpoints;

import org.rapla.facade.CalendarModel;
import org.rapla.inject.ExtensionPoint;
import org.rapla.inject.InjectionContext;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@ExtensionPoint(context = InjectionContext.server,id ="htmlexport")
public interface HTMLViewPage
{
    void generatePage( ServletContext context, HttpServletRequest request, HttpServletResponse response, CalendarModel model ) throws IOException, ServletException;
}
