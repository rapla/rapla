package org.rapla.server.servletpages;

import java.io.IOException;
import java.io.PrintWriter;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.rapla.inject.Extension;
import org.rapla.server.extensionpoints.RaplaPageExtension;


@Extension(provides = RaplaPageExtension.class,id="store")
@Singleton
public class RaplaStorePage implements RaplaPageExtension
{

    @Inject
    public RaplaStorePage()
    {
        // TODO Auto-generated constructor stub
    }
    
    public void generatePage(
        ServletContext context,
        HttpServletRequest request,
        HttpServletResponse response ) throws IOException, ServletException
    {
        String storeString = request.getParameter("storeString");
        PrintWriter writer = response.getWriter();
        writer.println("<form method=\"POST\" action=\"\">");
        writer.println("<input name=\"storeString\" value=\"" +  storeString+"\"/>");
        writer.println("<button type=\"submit\">Store</button>");
        writer.println("</form>");
        writer.close();
    }

}
