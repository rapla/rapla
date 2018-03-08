package org.rapla.server.internal;

import org.rapla.logger.Logger;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class RaplaRpcAndRestProcessor
{
    public static final String READBODY = "readbody";
    //final ServerServiceContainer serverContainer;
//    final Map<String, WebserviceCreator> webserviceMap;
    Logger logger;
    Logger readBodyLogger;

//    public RaplaRpcAndRestProcessor(Logger logger, WebserviceCreatorMap webservices)
//    {
//        this.logger = logger;
//        this.webserviceMap = webservices.asMap();
//        for (String key : webserviceMap.keySet())
//        {
//
//        }
//        readBodyLogger = logger.getChildLogger(READBODY);
//    }

//    Map<Class, JsonServlet> servletMap = new HashMap<Class, JsonServlet>();

    public class Path
    {
        final String path;
        final String subPath;

        Path(String path, String subPath)
        {
            this.path = path;
            this.subPath = subPath;
        }
    }

    public Path find(String pagename,String appendix)
    {
//        if (!webserviceMap.containsKey( pagename))
//        {
            return null;
//        }
//        return new Path(pagename, appendix);
    }

    public void generate(ServletContext servletContext, HttpServletRequest request, HttpServletResponse response, Path path) throws IOException
    {
//        final WebserviceCreator webserviceCreator = webserviceMap.get(path.path);
//        Class serviceClass = webserviceCreator.getServiceClass();
//        // try to find a json servlet in map
//        JsonServlet servlet = servletMap.get(serviceClass);
//        if (servlet == null)
//        {
//            // try to createInfoDialog one from the service interface
//            try
//            {
//                servlet = new RaplaJsonServlet(logger, serviceClass);
//            }
//            catch (Exception ex)
//            {
//                logger.error(ex.getMessage(), ex);
//                JsonServlet.writeException(request, response, ex);
//                return;
//            }
//            servletMap.put(serviceClass, servlet);
//        }
//
//        // instanciate the service Object
//        Object impl;
//        try
//        {
//            impl = webserviceCreator.createInfoDialog(request, response);
//        }
//        catch (RaplaSecurityException ex)
//        {
//            servlet.serviceError(request, response, servletContext, ex);
//            return;
//        }
//        catch (RaplaException ex)
//        {
//            logger.error(ex.getMessage(), ex);
//            servlet.serviceError(request, response, servletContext, ex);
//            return;
//        }
//        // proccess the call with the servlet
//        servlet.service(request, response, servletContext, impl, path.subPath);
    }

//    class RaplaJsonServlet extends JsonServlet
//    {
//        Logger logger = null;
//
//
//        public RaplaJsonServlet(Logger logger, Class class1) throws Exception
//        {
//            super(class1);
//            this.logger = logger;
//        }
//
//        @Override protected JsonElement getParams(Throwable failure)
//        {
//            JsonArray params = null;
//            if (failure instanceof DependencyException)
//            {
//                params = new JsonArray();
//                for (String dep : ((DependencyException) failure).getDependencies())
//                {
//                    params.add(new JsonPrimitive(dep));
//                }
//            }
//            return params;
//        }
//
//        @Override protected void debug(String childLoggerName, String out)
//        {
//            Logger childLogger = childLoggerName.equals(READBODY) ? readBodyLogger :logger.getChildLogger(childLoggerName);
//            if (childLogger.isDebugEnabled())
//            {
//                childLogger.debug(out);
//            }
//        }
//
//        protected boolean isDebugEnabled(String childLoggerName)
//        {
//            Logger childLogger = childLoggerName.equals(READBODY) ? readBodyLogger :logger.getChildLogger(childLoggerName);
//            return (childLogger.isDebugEnabled());
//        }
//
//        @Override protected void error(String message, Throwable ex)
//        {
//            logger.error(message, ex);
//        }
//
//        @Override protected boolean isSecurityException(Throwable i)
//        {
//            return i instanceof RaplaSecurityException;
//        }
//
//        @Override protected Class[] getAdditionalClasses()
//        {
//            return new Class[] { RaplaMapImpl.class };
//        }
//    }

}
