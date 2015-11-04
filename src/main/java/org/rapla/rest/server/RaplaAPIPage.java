package org.rapla.rest.server;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.rapla.entities.configuration.internal.RaplaMapImpl;
import org.rapla.framework.RaplaContextException;
import org.rapla.framework.RaplaException;
import org.rapla.framework.logger.Logger;
import org.rapla.inject.Extension;
import org.rapla.jsonrpc.common.RemoteJsonMethod;
import org.rapla.jsonrpc.common.internal.JSONParserWrapper;
import org.rapla.jsonrpc.server.JsonServlet;
import org.rapla.jsonrpc.server.internal.RPCServletUtils;
import org.rapla.server.extensionpoints.RaplaPageExtension;
import org.rapla.server.internal.dagger.WebMethodProvider;
import org.rapla.storage.RaplaSecurityException;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;



@Extension(provides = RaplaPageExtension.class,id="json")
@Singleton
public class RaplaAPIPage implements RaplaPageExtension
{
    //final ServerServiceContainer serverContainer;
    WebMethodProvider webservices;
    Logger logger;

    @Inject
    public RaplaAPIPage(Logger logger, WebMethodProvider webservices) throws RaplaContextException {
        this.logger = logger;
        this.webservices = webservices;
      //  this.serverContainer = serverContainer;
    }
    
    Map<String,JsonServlet> servletMap = new HashMap<String, JsonServlet>();
    private JsonServlet getJsonServlet(HttpServletRequest request,HttpServletResponse response,String serviceAndMethodName) throws RaplaException {
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
                // security check, we need to be sure a webservice with the name is provide before we load the class
                final Provider<Object> objectProvider = getService(request, response, interfaceName);
                if (objectProvider == null)
                {
                    throw new RaplaException("Webservice " + interfaceName + " not configured or initialized.");
                }
                Class<?> interfaceClass =  Class.forName(interfaceName, true,RaplaAPIPage.class.getClassLoader());
                final Class webserviceAnnotation = RemoteJsonMethod.class;
                if (interfaceClass.getAnnotation(webserviceAnnotation) == null)
                {
                    throw new RaplaException(interfaceName + " is not a webservice. Did you forget the annotation " + webserviceAnnotation.getName() + "?");
                }
                // Test if service is found
                servlet = new RaplaJsonServlet(logger, interfaceClass);
            }
            catch (Exception ex)
            {
                throw new RaplaException( ex.getMessage(), ex);
            }
            servletMap.put( interfaceName, servlet);
        }
        return servlet;
    }

    private Provider<Object> getService(HttpServletRequest request, HttpServletResponse response, String interfaceName)
    {
        return webservices.find(request, response, interfaceName);
    }

    private <T> Provider<T> getService(HttpServletRequest request, HttpServletResponse response, Class<T> interfaceClass)
    {
        return webservices.find(request, response, interfaceClass);
    }

    public void generatePage(ServletContext servletContext, HttpServletRequest request, HttpServletResponse response ) throws IOException, ServletException
    {
        String id = request.getParameter("id");
        String serviceAndMethodName = getServiceAndMethodName(request);
        JsonServlet servlet;
        try
        {
            servlet = getJsonServlet( request, response,serviceAndMethodName );
        }
        catch (RaplaException ex)
        {
            logger.error(ex.getMessage(), ex);
            String out = serializeException(id, ex);
            RPCServletUtils.writeResponse(servletContext, response,  out, false);
            return;
        }
        Class<?> role = servlet.getInterfaceClass();
        Object impl;
        try
        {
            final Provider<?> service = getService(request, response, role);
            impl = service.get();
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
        servlet.service(request, response, servletContext, impl);
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
        return new Class[] {RaplaMapImpl.class};
    }

    

}
