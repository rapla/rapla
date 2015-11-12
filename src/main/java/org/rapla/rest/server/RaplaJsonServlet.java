package org.rapla.rest.server;

import java.io.IOException;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.MediaType;

import org.rapla.entities.DependencyException;
import org.rapla.entities.configuration.internal.RaplaMapImpl;
import org.rapla.framework.logger.Logger;
import org.rapla.jsonrpc.server.JsonServlet;
import org.rapla.jsonrpc.server.internal.ActiveCall;
import org.rapla.storage.RaplaSecurityException;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

class RaplaJsonServlet extends JsonServlet
{
    Logger logger = null;
    public RaplaJsonServlet(Logger logger,Class class1) throws Exception
    {
        super(class1);
        this.logger = logger;
    }

    @Override
    protected JsonElement getParams(Throwable failure)
    {
        JsonArray params = null;
        if (failure instanceof DependencyException) {
            params = new JsonArray();
            for (String dep : ((DependencyException) failure).getDependencies()) {
                params.add(new JsonPrimitive(dep));
            }
        }
        return params;
    };


    @Override
    protected void debug(String childLoggerName, String out )
    {

        Logger childLogger = logger.getChildLogger(childLoggerName);
        if ( childLogger.isDebugEnabled() )
        {
            childLogger.debug(out);
        }
    }

    /*
    private boolean isHtmlTextRequest(HttpServletRequest req)
    {
        final String header = req.getHeader("Accept");
        if(!header.contains(MediaType.APPLICATION_JSON) && header.contains(MediaType.TEXT_HTML))
        {
            return true;
        }
        return false;
    }
    
    @Override
    protected String readBody(ActiveCall call) throws IOException
    {
        if(isHtmlTextRequest(call.getHttpRequest()))
            return null;
        return super.readBody(call);
    }
    
    @Override
    protected void writeResponse(ServletContext servletContext, ActiveCall call, String out) throws IOException
    {
        if(isHtmlTextRequest(call.getHttpRequest()))
            return;
        super.writeResponse(servletContext, call, out);
    }
    */

    @Override
    protected  void error(String message,Throwable ex)
    {
        logger.error(message, ex);
    }

    @Override protected boolean isSecurityException(Throwable i)
    {
        return i instanceof RaplaSecurityException;
    }

    @Override protected Class[] getAdditionalClasses()
    {
        return new Class[] {RaplaMapImpl.class};
    }
}
