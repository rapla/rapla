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

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.rapla.components.util.IOUtil;
import org.rapla.components.util.ParseDateException;
import org.rapla.entities.EntityNotFoundException;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.facade.CalendarNotFoundExeption;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.RaplaComponent;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaContextException;
import org.rapla.plugin.abstractcalendar.server.HTMLViewFactory;
import org.rapla.plugin.autoexport.AutoExportPlugin;
import org.rapla.server.RaplaServerExtensionPoints;
import org.rapla.servletpages.RaplaPageGenerator;


/******* USAGE: ************
 * ReadOnly calendarview view.
 * You will need the autoexport plugin to create a calendarview-view.
 *
 * Call:
 * rapla?page=calendar&user=<username>&file=<export_name>
 *
 * Optional Parameters:
 *
 * &hide_nav: will hide the navigation bar.
 * &day=<day>:  int-value of the day of month that should be displayed
 * &month=<month>:  int-value of the month
 * &year=<year>:  int-value of the year
 * &today:  will set the view to the current day. Ignores day, month and year
 */
public class CalendarPageGenerator extends RaplaComponent implements RaplaPageGenerator
{
	 private Map<String,HTMLViewFactory> factoryMap = new HashMap<String, HTMLViewFactory>();

	 public CalendarPageGenerator(RaplaContext context) throws  RaplaContextException
	 {
		 super(context);
		 for (HTMLViewFactory fact: getContainer().lookupServicesFor(RaplaServerExtensionPoints.HTML_CALENDAR_VIEW_EXTENSION))
		 {
			 String id = fact.getViewId();
			 factoryMap.put( id , fact);
		 }
	 }

    public void generatePage( ServletContext servletContext, HttpServletRequest request, HttpServletResponse response )
            throws IOException, ServletException
    {
        try
        {
            String username = request.getParameter( "user" );
            String filename = request.getParameter( "file" );
            CalendarSelectionModel model = null;
            User user;
            try
            {
            	user = getQuery().getUser( username );
            }
            catch (EntityNotFoundException ex)
            {
              	String message = "404 Calendar not availabe  " + username +"/" + filename ;
             	write404(response, message);
              	getLogger().getChildLogger("html.404").warn("404 User not found "+ username);
    			return;
            }
            try
            {
                model = getModification().newCalendarModel( user );
            	model.load(filename);
            } 
            catch (CalendarNotFoundExeption ex)
            {
              	String message = "404 Calendar not availabe  " + user +"/" + filename ;
    			write404(response, message);
            	return;            	
            }
            String allocatableId = request.getParameter( "allocatable_id" );
            if ( allocatableId != null)
            {
            	Allocatable[] selectedAllocatables = model.getSelectedAllocatables();
            	Allocatable foundAlloc = null; 
            	for ( Allocatable alloc:selectedAllocatables)
            	{
                    if (alloc.getId().equals( allocatableId))
            		{
            			foundAlloc = alloc;
            			break;
            		}
            	}
            	if ( foundAlloc !=  null)
            	{
            		model.setSelectedObjects( Collections.singleton(foundAlloc));
            		request.setAttribute("allocatable_id", allocatableId);
            	}
            	else
            	{
            		String message = "404 allocatable with id '" + allocatableId + "' not found for calendar " + user + "/" + filename  ;
        			write404(response, message);
                	return;
            	}
            }
            final Object isSet = model.getOption(AutoExportPlugin.HTML_EXPORT);
            if( isSet == null || isSet.equals("false"))
            {
              	String message = "404 Calendar not published " + username + "/" + filename ;
    			write404(response, message);
            	return;
            }
            
            final String viewId = model.getViewId();
            HTMLViewFactory factory = getFactory(viewId);

            if ( factory != null )
            {
                RaplaPageGenerator currentView = factory.createHTMLView( getContext(), model );
                if ( currentView != null )
                {
                    try
                    {
                    	currentView.generatePage( servletContext, request, response );
                    }
                    catch ( ServletException ex)
                    {
                    	Throwable cause = ex.getCause();
                    	if ( cause instanceof ParseDateException)
                    	{
                    		write404( response, cause.getMessage() + " in calendar " + user + "/" + filename);
                    	}
                    	else
                    	{
                    		throw ex;
                    	}
                    }
                }
                else
                {
                    write404( response, "No view available for calendar " + user + "/" + filename
                            + ". Rapla has currently no html support for the view with the id '"
                            + viewId
                            + "'." );
                }
            }
            else
            {
                writeError( response, "No view available for exportfile '"
                        + filename
                        + "'. Please install and select the plugin for "
                        + viewId );
            }
        }
        catch ( Exception ex )
        {
            writeStacktrace(response, ex);
            throw new ServletException( ex );
        }
       
    }

	protected HTMLViewFactory getFactory(final String viewId) {
		return factoryMap.get( viewId );
	}

	private void writeStacktrace(HttpServletResponse response, Exception ex)
			throws IOException {
		 	response.setContentType( "text/html; charset=" + getRaplaLocale().getCharsetNonUtf() );
		java.io.PrintWriter out = response.getWriter();
		out.println( IOUtil.getStackTraceAsString( ex ) );
		out.close();
	}

    protected void write404(HttpServletResponse response, String message) throws IOException {
    	response.setStatus( 404 );
        response.getWriter().print(message);
        getLogger().getChildLogger("html.404").warn( message);
        response.getWriter().close();
    }

    private void writeError( HttpServletResponse response, String message ) throws IOException
    {
    	response.setStatus( 500 );
    	response.setContentType( "text/html; charset=" + getRaplaLocale().getCharsetNonUtf() );
        java.io.PrintWriter out = response.getWriter();
        out.println( message );
        out.close();
    }

}
