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
package org.rapla.server.servletpages;

import java.io.IOException;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


/**
 * You can add arbitrary serlvet pages to your rapla webapp.
 *<p>
 * Example that adds a page with the name "my-page-name" and the class
 * "org.rapla.plugin.myplugin.MyPageGenerator". You can call this page with <code>rapla?page=my-page-name</code>
 * </p>
 * <p>
 * In the provideService Method of your PluginDescriptor do the following
 * </p>
 <pre>
 container.addContainerProvidedComponent( RaplaExtensionPoints.SERVLET_PAGE_EXTENSION, org.rapla.plugin.myplugin.MyPageGenerator);
 </pre>

 *@see org.rapla.server.servletpages.RaplaPageGenerator
 */

public interface RaplaPageGenerator
{
    void generatePage( ServletContext context, HttpServletRequest request, HttpServletResponse response ) throws IOException, ServletException;
}
