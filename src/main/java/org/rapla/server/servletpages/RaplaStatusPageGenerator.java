/**
 *
 */
package org.rapla.server.servletpages;

import org.rapla.RaplaResources;
import org.rapla.components.xmlbundle.I18nBundle;
import org.rapla.facade.RaplaComponent;
import org.rapla.framework.RaplaContextException;

import javax.inject.Inject;
import javax.inject.Named;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class RaplaStatusPageGenerator implements RaplaPageGenerator{
    I18nBundle m_i18n;
    @Inject
    public RaplaStatusPageGenerator(@Named(RaplaResources.ID) I18nBundle i18n) throws RaplaContextException {
        m_i18n = i18n;
    }

    public void generatePage( ServletContext context, HttpServletRequest request, HttpServletResponse response ) throws IOException {
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
     	out.println( "<p>Server running </p>" +  m_i18n.format("info.text", "yes", javaversion));
        out.println( "<hr>" );
        out.println( "</body>" );
        out.println( "</html>" );
        out.close();
    }

}