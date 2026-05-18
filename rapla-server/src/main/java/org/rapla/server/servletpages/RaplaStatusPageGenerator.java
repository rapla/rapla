/**
 *
 */
package org.rapla.server.servletpages;

import org.rapla.RaplaSystemInfo;
import org.rapla.server.internal.RaplaStatusEntry;
import org.rapla.server.spring.RaplaServerProperties;

import org.springframework.beans.factory.annotation.Autowired;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import java.io.IOException;

@Path("server")
public class RaplaStatusPageGenerator  {
    private final RaplaSystemInfo m_i18n;
    private final RaplaServerProperties properties;

    @Autowired
    public RaplaStatusPageGenerator(RaplaSystemInfo m_i18n, RaplaServerProperties properties)
    {
        this.m_i18n = m_i18n;
        this.properties = properties;
    }

    @GET
    @Produces(MediaType.TEXT_HTML)
    public void generatePage( @Context HttpServletRequest request, @Context HttpServletResponse response ) throws IOException {
        java.io.PrintWriter out = response.getWriter();
        response.setContentType("text/html; charset=ISO-8859-1");
        if ( !properties.isServiceEnabled( RaplaStatusEntry.ID))
        {
            out.println("Server Status disabled");
            response.setStatus( 404);
            out.close();
            return;
        }
        String linkPrefix = request.getPathTranslated() != null ? "../": "";
		
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
        out.close();
    }

}