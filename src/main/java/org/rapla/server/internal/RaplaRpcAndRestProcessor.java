package org.rapla.server.internal;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.rapla.entities.DependencyException;
import org.rapla.entities.configuration.internal.RaplaMapImpl;
import org.rapla.framework.RaplaException;
import org.rapla.framework.logger.Logger;
import org.rapla.jsonrpc.common.RemoteJsonMethod;
import org.rapla.jsonrpc.common.internal.JSONParserWrapper;
import org.rapla.jsonrpc.server.JsonServlet;
import org.rapla.jsonrpc.server.WebserviceCreator;
import org.rapla.jsonrpc.server.WebserviceCreatorMap;
import org.rapla.jsonrpc.server.internal.ActiveCall;
import org.rapla.jsonrpc.server.internal.RPCServletUtils;
import org.rapla.storage.RaplaSecurityException;

import javax.inject.Singleton;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Singleton public class RaplaRpcAndRestProcessor
{
    //final ServerServiceContainer serverContainer;
    final Map<String, WebserviceCreator> webserviceMap;
    Logger logger;

    public RaplaRpcAndRestProcessor(Logger logger, WebserviceCreatorMap webservices)
    {
        this.logger = logger;
        this.webserviceMap = webservices.asMap();
    }

    Map<Class, JsonServlet> servletMap = new HashMap<Class, JsonServlet>();

    private JsonServlet getJsonServlet(HttpServletRequest request, Class interfaceClass) throws RaplaException
    {
        JsonServlet servlet = servletMap.get(interfaceClass);
        if (servlet == null)
        {
            try
            {
                // security check, we need to be sure a webservice with the name is provide before we load the class
                final Class webserviceAnnotation = RemoteJsonMethod.class;
                if (interfaceClass.getAnnotation(webserviceAnnotation) == null)
                {
                    throw new RaplaException(interfaceClass + " is not a webservice. Did you forget the annotation " + webserviceAnnotation.getName() + "?");
                }
                // Test if service is found
                servlet = new RaplaJsonServlet(logger, interfaceClass);
            }
            catch (Exception ex)
            {
                throw new RaplaException(ex.getMessage(), ex);
            }
            servletMap.put(interfaceClass, servlet);
        }
        return servlet;
    }

    public class Path
    {
        final String path;
        final String appendix;

        Path(String path, String appendix)
        {
            this.path = path;
            this.appendix = appendix;
        }
    }

    public Path find(HttpServletRequest request)
    {
        String path = null;
        String appendix = null;
        String requestURI = request.getPathInfo();
        String subPath;
        if (requestURI.startsWith("/rapla/"))
        {
            subPath = requestURI.substring("/rapla/".length());
        }
        else if (requestURI.length() > 0)
        {
            subPath = requestURI.substring(1);
        }
        else
        {
            throw new RaplaException("No pathinfo found");
        }
        for (String key : webserviceMap.keySet())
        {
            if (subPath.startsWith(key))
            {
                path = key;
                if (subPath.length() > key.length())
                {
                    appendix = subPath.substring(key.length() + 1);
                }
            }
        }
        if (path == null)
        {
            return null;
        }
        return new Path(path, appendix);
    }

    public void generate(ServletContext servletContext, HttpServletRequest request, HttpServletResponse response, Path path) throws IOException
    {
        String id = request.getParameter("id");
        final WebserviceCreator webserviceCreator = webserviceMap.get(path.path);
        Class serviceClass = webserviceCreator.getServiceClass();

        JsonServlet servlet;
        try
        {
            servlet = getJsonServlet(request, serviceClass);
        }
        catch (RaplaException ex)
        {
            logger.error(ex.getMessage(), ex);
            String out = serializeException(id, ex);
            RPCServletUtils.writeResponse(servletContext, response, out, false);
            return;
        }
        Object impl;
        try
        {
            impl = webserviceCreator.create(request, response);
        }
        catch (RaplaSecurityException ex)
        {
            servlet.serviceError(request, response, servletContext, ex);
            return;
        }
        catch (RaplaException ex)
        {
            logger.error(ex.getMessage(), ex);
            servlet.serviceError(request, response, servletContext, ex);
            return;
        }
        servlet.service(request, response, servletContext, impl, path.appendix);
    }

    protected String serializeException(String id, Exception ex)
    {
        final JsonObject r = new JsonObject();
        String versionName = "jsonrpc";
        int code = -32603;
        r.add(versionName, new JsonPrimitive("2.0"));
        if (id != null)
        {
            r.add("id", new JsonPrimitive(id));
        }
        Class[] nonPrimitiveClasses = getNonPrimitiveClasses();
        GsonBuilder gb = JSONParserWrapper.defaultGsonBuilder(nonPrimitiveClasses);
        final JsonObject error = JsonServlet.getError(versionName, code, ex, null, gb);
        r.add("error", error);
        GsonBuilder builder = JSONParserWrapper.defaultGsonBuilder(nonPrimitiveClasses);
        String out = builder.create().toJson(r);
        return out;
    }

    private Class[] getNonPrimitiveClasses()
    {
        return new Class[] { RaplaMapImpl.class };
    }

    class RaplaJsonServlet extends JsonServlet
    {
        Logger logger = null;

        public RaplaJsonServlet(Logger logger, Class class1) throws Exception
        {
            super(class1);
            this.logger = logger;
        }

        @Override protected JsonElement getParams(Throwable failure)
        {
            JsonArray params = null;
            if (failure instanceof DependencyException)
            {
                params = new JsonArray();
                for (String dep : ((DependencyException) failure).getDependencies())
                {
                    params.add(new JsonPrimitive(dep));
                }
            }
            return params;
        }

        @Override protected void debug(String childLoggerName, String out)
        {
            Logger childLogger = logger.getChildLogger(childLoggerName);
            if (childLogger.isDebugEnabled())
            {
                childLogger.debug(out);
            }
        }

        @Override protected void error(String message, Throwable ex)
        {
            logger.error(message, ex);
        }

        @Override protected boolean isSecurityException(Throwable i)
        {
            return i instanceof RaplaSecurityException;
        }

        @Override protected Class[] getAdditionalClasses()
        {
            return new Class[] { RaplaMapImpl.class };
        }
    }

}
