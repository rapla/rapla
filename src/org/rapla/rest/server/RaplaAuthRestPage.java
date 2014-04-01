package org.rapla.rest.server;

import java.io.IOException;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaContextException;
import org.rapla.servletpages.RaplaPageGenerator;
import org.rapla.storage.dbrm.RemoteServer;


public class RaplaAuthRestPage extends RaplaAPIPage implements RaplaPageGenerator
{
    public RaplaAuthRestPage(RaplaContext context) throws RaplaContextException {
        super(context);
    }
    

    @Override
    protected String getServiceAndMethodName(HttpServletRequest request)
    {
        return RemoteServer.class.getName() +"/auth";
    }
    
    @Override
    public void generatePage(ServletContext servletContext, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        super.generatePage(servletContext, request, response);
    }
    

}
