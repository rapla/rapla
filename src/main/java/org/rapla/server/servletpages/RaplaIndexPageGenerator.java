/**
 *
 */
package org.rapla.server.servletpages;

import org.rapla.RaplaResources;
import org.rapla.components.util.Tools;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.internal.AbstractRaplaLocale;
import org.rapla.plugin.abstractcalendar.server.AbstractHTMLCalendarPage;
import org.rapla.server.extensionpoints.HtmlMainMenu;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Iterator;
import java.util.Set;

@Path("index")
@Singleton
public class RaplaIndexPageGenerator
{
    @Inject
    Set<HtmlMainMenu> entries;
	@Inject
	RaplaResources i18n;
	
	@Inject
	RaplaFacade facade;

	@Inject
    public RaplaIndexPageGenerator( )
    {
    }

    @GET
    @Produces(MediaType.TEXT_HTML)
    public void generatePage(@Context HttpServletRequest request, @Context HttpServletResponse response )
            throws IOException, ServletException
    {
        if ( request.getParameter("page") == null && request.getRequestURI().endsWith("/rapla/index/"))
        {
            response.sendRedirect("../index");
        }
		response.setContentType("text/html; charset=ISO-8859-1");
		java.io.PrintWriter out = response.getWriter();
		out.println("<html>");
		out.println("  <head>");
		// add the link to the stylesheet for this page within the <head> tag
		out.println("    " + AbstractHTMLCalendarPage.getCssLine(request,"default.css"));
		// tell the html page where its favourite icon is stored
		out.println("    " + AbstractHTMLCalendarPage.getFavIconLine(request));
		out.println("    <title>");
		 String title;
		 final String defaultTitle = i18n.getString("rapla.title");
		 try {
            title= Tools.createXssSafeString(facade.getSystemPreferences().getEntryAsString(AbstractRaplaLocale.TITLE, defaultTitle));
        } catch (RaplaException e) {
            title = defaultTitle; 
        }
	       
		out.println(title);
		out.println("    </title>");
		out.println("  </head>");
		out.println("  <body>");
		out.println("    <h3>");
		out.println(title);
		out.println("    </h3>");
		generateMenu( request, out);
		out.println(i18n.getString("webinfo.text"));
		out.println("  </body>");
		out.println("</html>");
		out.close();
    }
    
    public void generateMenu( HttpServletRequest request, PrintWriter out ) 
    {
        if ( entries.size() == 0)
        {
            return;
        }
//        out.println("<ul>");
        
     // there is an ArraList of entries that wants to be part of the HTML
        // menu we go through this ArraList,
        
        for (Iterator<HtmlMainMenu> it = entries.iterator();it.hasNext();)
        {
        	RaplaMenuGenerator entry = it.next();
            out.println("<div class=\"menuEntry\">");
            entry.generatePage(   request, out );
            out.println("</div>");
        }
//        out.println("</ul>");
    }


}