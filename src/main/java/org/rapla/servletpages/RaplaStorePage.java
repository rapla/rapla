package org.rapla.servletpages;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


public class RaplaStorePage implements RaplaPageGenerator
{

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
