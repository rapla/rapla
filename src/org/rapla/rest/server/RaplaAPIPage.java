package org.rapla.rest.server;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.rapla.facade.RaplaComponent;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaContextException;
import org.rapla.framework.RaplaException;
import org.rapla.rest.gwtjsonrpc.common.JSONParserWrapper;
import org.rapla.rest.gwtjsonrpc.server.JsonServlet;
import org.rapla.rest.gwtjsonrpc.server.RPCServletUtils;
import org.rapla.server.ServerServiceContainer;
import org.rapla.servletpages.RaplaPageGenerator;
import org.rapla.storage.RaplaSecurityException;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;


public class RaplaAPIPage extends RaplaComponent implements RaplaPageGenerator
{

    final ServerServiceContainer serverContainer;
    
    public RaplaAPIPage(RaplaContext context) throws RaplaContextException {
        super(context);
        serverContainer = context.lookup(ServerServiceContainer.class);
    }
    
    Map<String,JsonServlet> servletMap = new HashMap<String, JsonServlet>();
    private JsonServlet getJsonServlet(HttpServletRequest request,String serviceAndMethodName) throws RaplaException {
        if  ( serviceAndMethodName == null || serviceAndMethodName.length() == 0) {
            throw new RaplaException("Servicename missing in url");
        }
        int indexRole = serviceAndMethodName.indexOf( "/" );
        String interfaceName;
        if ( indexRole > 0 )
        {
            interfaceName= serviceAndMethodName.substring( 0, indexRole );
            if ( serviceAndMethodName.length() >= interfaceName.length())
            {
                String methodName = serviceAndMethodName.substring( indexRole + 1 );
                request.setAttribute(JsonServlet.JSON_METHOD, methodName);
            }
        }
        else
        {
            interfaceName = serviceAndMethodName;
        }
        JsonServlet servlet = servletMap.get( interfaceName);
        if ( servlet == null)
        {
            try
            {
                if (!serverContainer.hasWebservice(interfaceName))
                {
                    throw new RaplaException("Webservice " + interfaceName + " not configured or initialized.");
                }
                Class<?> interfaceClass =  Class.forName(interfaceName, true,RaplaAPIPage.class.getClassLoader());
                // Test if service is found
                servlet = new JsonServlet(getLogger(), interfaceClass);
            }
            catch (Exception ex)
            {
                throw new RaplaException( ex.getMessage(), ex);
            }
            servletMap.put( interfaceName, servlet);
        }
        return servlet;
    }

    public void generatePage(ServletContext servletContext, HttpServletRequest request, HttpServletResponse response ) throws IOException, ServletException
    {
        String id = request.getParameter("id");
        String serviceAndMethodName = getServiceAndMethodName(request);
        try
        {
            JsonServlet servlet;
            try
            {
                servlet = getJsonServlet( request, serviceAndMethodName );
            }
            catch (RaplaException ex)
            {
                getLogger().error(ex.getMessage(), ex);
                String out = serializeException(id, ex);
                RPCServletUtils.writeResponse(servletContext, response,  out, false);
                return;
            }
            Class<?> role = servlet.getInterfaceClass();
            Object impl;
            try
            {
                impl = serverContainer.createWebservice(role, request);
            }
            catch (RaplaSecurityException ex)
            {
                servlet.serviceError(request, response, servletContext, ex);
                return;
            }
            servlet.service(request, response, servletContext, impl);
        }
        catch ( RaplaSecurityException ex)
        {
            getLogger().error(ex.getMessage());
        }
        catch ( RaplaException ex)
        {
            getLogger().error(ex.getMessage(), ex);
        }
    }

    protected String getServiceAndMethodName(HttpServletRequest request) {
        String requestURI =request.getPathInfo();
        String path = "/json/";
        int rpcIndex=requestURI.indexOf(path) ;
        String serviceAndMethodName = requestURI.substring(rpcIndex + path.length()).trim();
        return serviceAndMethodName;
    }

    protected String serializeException(String id, Exception ex) 
    {
        final JsonObject r = new JsonObject();
        String versionName = "jsonrpc";
        int code = -32603;
        r.add(versionName, new JsonPrimitive("2.0"));
        if (id != null) {
          r.add("id", new JsonPrimitive(id));
        }
        GsonBuilder gb = JSONParserWrapper.defaultGsonBuilder();
        final JsonObject error = JsonServlet.getError(versionName, code, ex, gb);
        r.add("error", error);
        GsonBuilder builder = JSONParserWrapper.defaultGsonBuilder();
        String out = builder.create().toJson( r);
        return out;
    }

    

}
