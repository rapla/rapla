package org.rapla.rest.server;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import org.rapla.entities.User;
import org.rapla.framework.RaplaException;
import org.rapla.framework.internal.ContainerImpl;
import org.rapla.framework.logger.Logger;
import org.rapla.server.RemoteSession;
import org.rapla.server.ServerServiceContainer;
import org.rapla.server.extensionpoints.RaplaPageExtension;
import org.rapla.server.servletpages.RaplaPageGenerator;
import org.rapla.storage.RaplaSecurityException;

/**
 * Wrapper for all REST pages, which listen under the rest @Path.
 */
public class RaplaRestApiWrapper implements RaplaPageExtension, RaplaPageGenerator
{
    private static final String ALL = "ALL";
    private final ServerServiceContainer serverContainer;
    private final Logger logger;
    private final Class<?> restPageClass;
    private final Map<String, String> getMethods;
    private final Map<String, String> updatetMethods;
    private final Map<String, String> createMethods;
    private final Map<String, String> listMethods;

    RemoteSession session;
    @Inject
    public RaplaRestApiWrapper(ServerServiceContainer serverContainer, Logger logger, Class<?> restPageClass, RemoteSession session) throws RaplaException
    {
        this.serverContainer = serverContainer;
        this.session = session;
        this.logger = logger;
        this.restPageClass = restPageClass;
        getMethods = getMethodNameWithAnnotations(restPageClass, GET.class, Path.class);
        updatetMethods = getMethodNameWithAnnotations(restPageClass, PUT.class);
        createMethods = getMethodNameWithAnnotations(restPageClass, POST.class);
        listMethods = getMethodNameWithAnnotations(restPageClass, GET.class);
    }

    private static Map<String, String> getMethodNameWithAnnotations(Class<?> clazz, Class<? extends Annotation>... annotations)
    {
        final HashMap<String, String> result = new HashMap<>();
        final Method[] methods = clazz.getMethods();
        for (Method method : methods)
        {
            if (Modifier.isPublic(method.getModifiers()))
            {
                Class<? extends Annotation> annotationsWithProduces[] = new Class[annotations.length + 1];
                System.arraycopy(annotations, 0, annotationsWithProduces, 0, annotations.length);
                annotationsWithProduces[annotations.length] = Produces.class;
                if (containsOnlyThisAnnotatoins(method, annotations))
                {
                    result.put(ALL, method.getName());
                }
                else if (containsOnlyThisAnnotatoins(method, annotationsWithProduces))
                {
                    final String[] produces = method.getAnnotation(Produces.class).value();
                    for (String produce : produces)
                    {
                        result.put(produce, method.getName());
                    }
                }
                method.getName();
            }
        }
        return result.isEmpty() ? null : result;
    }

    private static boolean containsOnlyThisAnnotatoins(Method method, Class<? extends Annotation>[] annotations)
    {
        final Annotation[] methodAnnotations = method.getAnnotations();
        if (methodAnnotations.length != annotations.length)
        {
            return false;
        }
        final ArrayList<Class<? extends Annotation>> methodAppointmentsClassList = new ArrayList<Class<? extends Annotation>>();
        for (Annotation annotation : methodAnnotations)
        {
            methodAppointmentsClassList.add(annotation.annotationType());
        }
        final List<Class<? extends Annotation>> annotationsAsList = Arrays.asList(annotations);
        if (methodAppointmentsClassList.containsAll(annotationsAsList))
        {
            return true;
        }
        return false;
    }

    @Override
    public void generatePage(ServletContext servletContext, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException
    {
        RaplaJsonServlet servlet = null;
        Object serviceObj;
        boolean authentificationRequired = false;
        //      Collection<String> publicMethodNames = getPublicMethods(class1);
        try
        {
            servlet = new RaplaJsonServlet(logger, restPageClass);
            serviceObj = ((ContainerImpl) serverContainer).inject(restPageClass);
            if (serviceObj instanceof AbstractRestPage)
            {
                authentificationRequired = ((AbstractRestPage) serviceObj).isAuthenificationRequired();
            }
        }
        catch (RaplaException ex)
        {
            servlet.serviceError(request, response, servletContext, ex);
            return;
        }
        catch (Exception ex)
        {
            servlet.serviceError(request, response, servletContext, ex);
            return;
        }
        //        Object serviceObj = getServiceObject();
        try
        {

            if (authentificationRequired)
            {
                if (!session.isAuthentified())
                {
                    throw new RaplaSecurityException("User not authentified. Pass access token");
                }
                final User user = session.getUser();
                request.setAttribute("user", user);
            }
            String pathInfo = request.getPathInfo();
            int appendix = pathInfo != null ? pathInfo.indexOf("/", 1) : 0;
            String method = request.getMethod();
            if (appendix > 0)
            {
                if (method.equals("GET") && getAnnotadedMethod(getMethods, request) != null)
                {
                    request.setAttribute("method", getAnnotadedMethod(getMethods, request));
                }
                else if (method.equals("PATCH") && getAnnotadedMethod(getMethods, request) != null && getAnnotadedMethod(updatetMethods, request) != null)
                {
                    request.setAttribute("method", getAnnotadedMethod(getMethods, request));
                    request.setAttribute("patchMethod", getAnnotadedMethod(updatetMethods, request));
                }
                else if (method.equals("PUT") && getAnnotadedMethod(updatetMethods, request) != null)
                {
                    request.setAttribute("method", getAnnotadedMethod(updatetMethods, request));
                }
                else
                {
                    throw new RaplaException(method + " Method not supported in this context");
                }
                String id = pathInfo.substring(appendix + 1);
                request.setAttribute("id", id);
            }
            else
            {
                if (method.equals("GET") && getAnnotadedMethod(listMethods, request) != null)
                {
                    request.setAttribute("method", getAnnotadedMethod(listMethods, request));
                }
                else if (method.equals("POST") && getAnnotadedMethod(createMethods, request) != null)
                {
                    request.setAttribute("method", getAnnotadedMethod(createMethods, request));
                }
                else
                {
                    throw new RaplaException(method + " Method not supported in this context");
                }
            }
        }
        catch (RaplaException ex)
        {
            servlet.serviceError(request, response, servletContext, ex);
            return;
        }
        servlet.service(request, response, servletContext, serviceObj);
    }

    private String getAnnotadedMethod(Map<String, String> methods, HttpServletRequest request)
    {
        if (methods != null)
        {
            final String acceptHeaders = request.getHeader("Accept");
            if (acceptHeaders != null)
            {
                final String[] acceptHeadersSplitted = acceptHeaders.split(",");
                for (String acceptHeader : acceptHeadersSplitted)
                {
                    if (acceptHeader != null)
                    {
                        final String annotadedMethod = methods.get(acceptHeader);
                        if (annotadedMethod != null)
                        {
                            return annotadedMethod;
                        }
                    }
                }
            }
            return methods.get(ALL);
        }
        return null;
    }
}
