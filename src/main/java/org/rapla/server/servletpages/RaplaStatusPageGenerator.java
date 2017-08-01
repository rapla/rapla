/**
 *
 */
package org.rapla.server.servletpages;

import org.rapla.RaplaSystemInfo;
import org.rapla.server.internal.RaplaStatusEntry;
import org.rapla.server.internal.ServerContainerContext;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.io.IOException;

@Singleton
@Path("server")
public class RaplaStatusPageGenerator  {
    @Inject RaplaSystemInfo m_i18n;
    @Inject ServerContainerContext serverContainerContext;
    @Inject
    public RaplaStatusPageGenerator()
    {
    }

    @GET
    @Produces(MediaType.TEXT_HTML)
    public void generatePage( @Context HttpServletRequest request, @Context HttpServletResponse response ) throws IOException {
        java.io.PrintWriter out = response.getWriter();
        response.setContentType("text/html; charset=ISO-8859-1");
        if ( !serverContainerContext.isServiceEnabled( RaplaStatusEntry.ID))
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