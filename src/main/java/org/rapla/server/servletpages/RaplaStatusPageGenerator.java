/**
 *
 */
package org.rapla.server.servletpages;

import org.rapla.RaplaResources;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.io.IOException;

@Singleton
@Path("server")
public class RaplaStatusPageGenerator  {
    RaplaResources m_i18n;
    @Inject
    public RaplaStatusPageGenerator(RaplaResources i18n)
    {
        m_i18n = i18n;
    }

    @GET
    @Produces(MediaType.TEXT_HTML)
    public void generatePage(  HttpServletRequest request, HttpServletResponse response ) throws IOException {
        response.setContentType("text/html; charset=ISO-8859-1");
        String linkPrefix = request.getPathTranslated() != null ? "../": "";
		
        java.io.PrintWriter out = response.getWriter();
        out.println( "<html>" );
        out.println( "<head>" );
        out.println("  <link REL=\"stylesheet\" href=\"" + linkPrefix + "default.css\" type=\"text/css\">");
        out.println("  <title>Rapla Server status</title>");
        out.println("</head>" );

        out.println( "<body>" );
        String javaversion = System.getProperty("java.version");
     	out.println( "<p>Server running </p>" +  m_i18n.infoText( javaversion));
        out.println( "<hr>" );
        out.println( "</body>" );
        out.println( "</html>" );
    }

}