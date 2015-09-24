package org.rapla.rest.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import org.rapla.entities.DependencyException;
import org.rapla.entities.configuration.internal.RaplaMapImpl;
import org.rapla.framework.RaplaException;
import org.rapla.framework.logger.Logger;
import org.rapla.gwtjsonrpc.server.JsonServlet;
import org.rapla.gwtjsonrpc.server.NoPublicServiceMethodsException;
import org.rapla.storage.RaplaSecurityException;

class RaplaJsonServlet extends JsonServlet
{
    Logger logger = null;
    public RaplaJsonServlet(Logger logger,Class class1) throws Exception
    {
        super(class1);
        this.logger = logger;
    }

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


    protected void debug(String childLoggerName, String out )
    {

        Logger childLogger = logger.getChildLogger(childLoggerName);
        if ( childLogger.isDebugEnabled() )
        {
            childLogger.debug(out);
        }
    }

    protected  void error(String message,Throwable ex)
    {
        Logger logger = null;
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
