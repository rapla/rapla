/**
 *
 */
package org.rapla.server.servletpages;

import org.rapla.RaplaResources;
import org.rapla.components.util.Tools;
import org.rapla.facade.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.internal.ContainerImpl;
import org.rapla.inject.Extension;
import org.rapla.server.extensionpoints.HtmlMainMenu;
import org.rapla.server.extensionpoints.RaplaPageExtension;

import javax.inject.Inject;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

@Extension(provides = RaplaPageExtension.class,id="index")
public class RaplaIndexPageGenerator implements RaplaPageExtension
{
	Set<RaplaMenuGenerator> entries;
	RaplaResources i18n;
	
	ClientFacade facade;
	@Inject
    public RaplaIndexPageGenerator( RaplaResources i18n, ClientFacade facade, Set<HtmlMainMenu> entries)
    {
        this.i18n = i18n;
        this.facade = facade;
        this.entries = new LinkedHashSet<>(entries);
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
            title= Tools.createXssSafeString(facade.getSystemPreferences().getEntryAsString(ContainerImpl.TITLE, defaultTitle));
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