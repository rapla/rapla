/**
 *
 */
package org.rapla.server.servletpages;

import org.rapla.inject.dagger.DaggerReflectionStarter;
import org.rapla.server.ServerServiceContainer;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.Collection;

@Singleton
@Path("raplaapplet")
public class RaplaAppletPageGenerator
{
    private final String moduleId;
    @Inject
    public RaplaAppletPageGenerator()
    {
        moduleId = DaggerReflectionStarter.loadModuleId(ServerServiceContainer.class.getClassLoader());
    }

    private String getLibsApplet(ServletContext context) throws java.io.IOException {
        StringBuffer buf = new StringBuffer();
        Collection<String> files = RaplaJNLPPageGenerator.getClientLibs(context);
        boolean first= true;
        for (String file:files) {
          if ( !first)
          {
            buf.append(", ");
          }
          else
          {
        	  first = false;
          }
          buf.append("../");
          buf.append(file);
       }
       return buf.toString();
    }


    @GET
    @Produces(MediaType.TEXT_HTML)
    public void generatePage(@Context HttpServletRequest request, @Context HttpServletResponse response ) throws IOException {
        ServletContext context = request.getServletContext();
    	String linkPrefix = request.getPathTranslated() != null ? "../": "";
		response.setContentType("text/html; charset=ISO-8859-1");
        java.io.PrintWriter out = response.getWriter();
        out.println("<html>");
        out.println("<head>");
        out.println("  <title>Rapla Applet</title>");
        out.println("  <link REL=\"stylesheet\" href=\""+linkPrefix+"default.css\" type=\"text/css\">");
        out.println("</head>");
        out.println("<body>");
        out.println("   <object type=\"application/x-java-applet\" codebase=\"../.\" align=\"baseline\"");
        //out.println("   <applet code=\"org.rapla.client.MainApplet\" codebase=\".\" align=\"baseline\"");
        out.println("        width=\"300\" height=\"300\" " );
        //out.println(" archive=\""+getLibsApplet(context)+"\" codebase_lookup=\"false\"");
        out.println("   >");
        out.println("     <param name=\"archive\" value=\""+getLibsApplet(context) +"\"/>");
        out.println("     <param name=\"java_code\" value=\"org.rapla.client.MainApplet\"/>");
        out.println("     <param name=\"java_codebase\" value=\"./\">");
        out.println("     <param name=\"java_type\" value=\"application/x-java-applet;jpi-version=1.4.1\"/>");
        out.println("     <param name=\"codebase_lookup\" value=\"false\"/>");
        out.println("     <param name=\"scriptable\" value=\"true\"/>");
        String passedUsername = request.getParameter("username");
        if ( passedUsername != null)
        {
            String safeUsername = URLEncoder.encode(passedUsername, "UTF-8");
            out.println("  <param name=\"org.rapla.startupUser\" value=\""+safeUsername + "\"/>");
        }
        out.println("  <param name=\"org.rapla.moduleId\" value=\""+moduleId + "\"/>");
        out.println("      No Java support for APPLET tags please install java plugin for your browser!!");
        //out.println("   </applet>");
        out.println("   </object>");
        out.println("</body>");
        out.println("</html>");
        out.close();
    }

}