/**
 *
 */
package org.rapla.server.servletpages;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.Iterator;

import javax.inject.Inject;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.rapla.RaplaDefaultResources;
import org.rapla.facade.ClientFacade;
import org.rapla.framework.Container;
import org.rapla.framework.RaplaContextException;
import org.rapla.framework.RaplaException;
import org.rapla.framework.internal.ContainerImpl;
import org.rapla.server.RaplaServerExtensionPoints;

public class RaplaIndexPageGenerator implements RaplaPageGenerator
{
	Collection<RaplaMenuGenerator> entries;
	RaplaDefaultResources i18n;
	
	ClientFacade facade;
	@Inject
    public RaplaIndexPageGenerator( Container container, RaplaDefaultResources i18n, ClientFacade facade) throws RaplaContextException
    {
        this.i18n = i18n;
        this.facade = facade;
        entries = container.lookupServicesFor( RaplaServerExtensionPoints.HTML_MAIN_MENU_EXTENSION_POINT);
    }

    public void generatePage( ServletContext context, HttpServletRequest request, HttpServletResponse response )
            throws IOException, ServletException
    {
        if ( request.getParameter("page") == null && request.getRequestURI().endsWith("/rapla/"))
        {
            response.sendRedirect("../rapla");
        }
		response.setContentType("text/html; charset=ISO-8859-1");
		java.io.PrintWriter out = response.getWriter();
		String linkPrefix = request.getPathTranslated() != null ? "../": "";
		    	
		out.println("<html>");
		out.println("  <head>");
		// add the link to the stylesheet for this page within the <head> tag
		out.println("    <link REL=\"stylesheet\" href=\""+linkPrefix+"default.css\" type=\"text/css\">");
		// tell the html page where its favourite icon is stored
		out.println("    <link REL=\"shortcut icon\" type=\"image/x-icon\" href=\""+linkPrefix+"images/favicon.ico\">");
		out.println("    <title>");
		 String title;
		 final String defaultTitle = i18n.getString("rapla.title");
		 try {
            title= facade.getSystemPreferences().getEntryAsString(ContainerImpl.TITLE, defaultTitle);
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
        
        for (Iterator<RaplaMenuGenerator> it = entries.iterator();it.hasNext();)
        {
        	RaplaMenuGenerator entry = it.next();
            out.println("<div class=\"menuEntry\">");
            entry.generatePage(   request, out );
            out.println("</div>");
        }
//        out.println("</ul>");
    }


}