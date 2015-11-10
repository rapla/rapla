/**
 *
 */
package org.rapla.server.servletpages;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.Collection;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.rapla.inject.Extension;
import org.rapla.server.extensionpoints.RaplaPageExtension;

@Extension(provides = RaplaPageExtension.class,id="raplaapplet")
@Singleton
public class RaplaAppletPageGenerator implements RaplaPageExtension
{
    @Inject
    public RaplaAppletPageGenerator()
    {
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
          buf.append(file);
       }
       return buf.toString();
    }


    public void generatePage( ServletContext context, HttpServletRequest request, HttpServletResponse response ) throws IOException {
    	String linkPrefix = request.getPathTranslated() != null ? "../": "";
		response.setContentType("text/html; charset=ISO-8859-1");
        java.io.PrintWriter out = response.getWriter();
        out.println("<html>");
        out.println("<head>");
        out.println("  <title>Rapla Applet</title>");
        out.println("  <link REL=\"stylesheet\" href=\""+linkPrefix+"default.css\" type=\"text/css\">");
        out.println("</head>");
        out.println("<body>");
        out.println("   <applet code=\"org.rapla.client.MainApplet\" codebase=\".\" align=\"baseline\"");
        out.println("        width=\"300\" height=\"300\" archive=\""+getLibsApplet(context)+"\" codebase_lookup=\"false\"");
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
        out.println("      No Java support for APPLET tags please install java plugin for your browser!!");
        out.println("   </applet>");
        out.println("</body>");
        out.println("</html>");
        out.close();
    }

}