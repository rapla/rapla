/**
 *
 */
package org.rapla.servletpages;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.Iterator;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.rapla.RaplaMainContainer;
import org.rapla.facade.RaplaComponent;
import org.rapla.framework.Container;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaContextException;
import org.rapla.framework.RaplaException;
import org.rapla.server.RaplaServerExtensionPoints;

public class RaplaIndexPageGenerator extends RaplaComponent implements RaplaPageGenerator
{
	Collection<RaplaMenuGenerator> entries;
	
    public RaplaIndexPageGenerator( RaplaContext context ) throws RaplaContextException
    {
        super( context);
        entries = context.lookup(Container.class).lookupServicesFor( RaplaServerExtensionPoints.HTML_MAIN_MENU_EXTENSION_POINT);
    }

    public void generatePage( ServletContext context, HttpServletRequest request, HttpServletResponse response )
            throws IOException, ServletException
    {
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
		 final String defaultTitle = getI18n().getString("rapla.title");
		 try {
            title= getQuery().getPreferences( null ).getEntryAsString(RaplaMainContainer.TITLE, defaultTitle);
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
		out.println(getI18n().getString("webinfo.text"));
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