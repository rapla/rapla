package org.rapla.servletpages;

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
import org.rapla.storage.RaplaSecurityException;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;


public class RaplaResourcesRestPage extends RaplaComponent implements RaplaPageGenerator
{

    final ServerServiceContainer serverContainer;
    private static final String RAPLA_JSON_PATH = "/rapla/resources";
    
    public RaplaResourcesRestPage(RaplaContext context) throws RaplaContextException {
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
                Class<?> interfaceClass =  Class.forName(interfaceName, true,RaplaResourcesRestPage.class.getClassLoader());
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
        String requestURI =request.getRequestURI();
        int rpcIndex=requestURI.indexOf(RAPLA_JSON_PATH) ;
        String serviceAndMethodName = requestURI.substring(rpcIndex + RAPLA_JSON_PATH.length()).trim();
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
                final JsonObject r = new JsonObject();
                String versionName = "jsonrpc";
                int code = -32603;
                r.add(versionName, new JsonPrimitive("2.0"));
                String id = request.getParameter("id");
                if (id != null) {
                  r.add("id", new JsonPrimitive(id));
                }
                
                final JsonObject error = JsonServlet.getError(versionName, code, ex, JSONParserWrapper.defaultGsonBuilder());
                r.add("error", error);
                GsonBuilder builder = JSONParserWrapper.defaultGsonBuilder().disableHtmlEscaping();
                String out = builder.create().toJson( r);
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

    

}
