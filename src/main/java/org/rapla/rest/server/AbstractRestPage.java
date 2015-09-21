package org.rapla.rest.server;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;

import org.rapla.entities.User;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.ClassificationFilter;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.entities.storage.EntityResolver;
import org.rapla.facade.ClientFacade;
import org.rapla.facade.ModificationModule;
import org.rapla.facade.QueryModule;
import org.rapla.framework.RaplaException;
import org.rapla.framework.logger.Logger;
import org.rapla.gwtjsonrpc.common.RemoteJsonService;
import org.rapla.gwtjsonrpc.server.JsonServlet;
import org.rapla.server.ServerServiceContainer;
import org.rapla.server.servletpages.RaplaPageGenerator;
import org.rapla.storage.RaplaSecurityException;
import org.rapla.storage.StorageOperator;

public abstract class AbstractRestPage implements RaplaPageGenerator, RemoteJsonService {

	protected StorageOperator operator;
	protected JsonServlet servlet;
	protected final ServerServiceContainer serverContainer;
	private boolean authentificationRequired;
	String getMethod = null;
	String updatetMethod = null;
	String listMethod = null;
	String createMethod = null;
	protected ClientFacade facade;

	public AbstractRestPage(ClientFacade facade, ServerServiceContainer serverContainer, Logger logger, boolean authentificationRequired) throws RaplaException {
		this.authentificationRequired = authentificationRequired;
		this.facade = facade;
		operator = facade.getOperator();
		this.serverContainer = serverContainer;
		Class class1 = getServiceObject().getClass();
//		Collection<String> publicMethodNames = getPublicMethods(class1);
		try
		{
			servlet = new RaplaJsonServlet(logger, class1);
		}
		catch ( RaplaException ex)
		{
			throw ex;
		}
		catch ( Exception ex)
		{
			throw new RaplaException(ex);
		}
        getMethod = getMethodNameWithAnnotations(class1, GET.class, Path.class);
		updatetMethod = getMethodNameWithAnnotations(class1, PUT.class);
		createMethod = getMethodNameWithAnnotations(class1, POST.class);
		listMethod = getMethodNameWithAnnotations(class1, GET.class);
	}

	private static String getMethodNameWithAnnotations(Class<?> clazz, Class<? extends Annotation> ... annotations)
    {
	    final Method[] methods = clazz.getMethods();
	    for (Method method : methods)
        {
            if(Modifier.isPublic(method.getModifiers()) && containsOnlyThisAnnotatoins(method, annotations))
            {
                return method.getName();
            }
        }
        return null;
    }

    private static boolean containsOnlyThisAnnotatoins(Method method, Class<? extends Annotation>[] annotations)
    {
        final Annotation[] methodAnnotations = method.getAnnotations();
        if(methodAnnotations.length != annotations.length)
        {
            return false;
        }
        final ArrayList<Class<? extends Annotation>> methodAppointmentsClassList = new ArrayList<Class<? extends Annotation>>();
        for (Annotation annotation : methodAnnotations)
        {
            methodAppointmentsClassList.add(annotation.annotationType());
        }
        final List<Class<? extends Annotation>> annotationsAsList = Arrays.asList(annotations);
        if(methodAppointmentsClassList.containsAll(annotationsAsList))
        {
            return true;
        }
        return false;
    }

    @Override
	public void generatePage(ServletContext servletContext, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
		Object serviceObj = getServiceObject();
		try {
			if (authentificationRequired) {
				final User user = serverContainer.getUser(request);
				if (user == null) {
					throw new RaplaSecurityException("User not authentified. Pass access token");
				}
				request.setAttribute("user", user);
			}
			String pathInfo = request.getPathInfo();
			int appendix = pathInfo.indexOf("/", 1);
			String method = request.getMethod();
			if (appendix > 0) {
				if (method.equals("GET") && getMethod != null) {
					request.setAttribute("method", getMethod);
				} else if (method.equals("PATCH") && getMethod != null && updatetMethod != null) {
					request.setAttribute("method", getMethod);
					request.setAttribute("patchMethod", updatetMethod);
				} else if (method.equals("PUT") && updatetMethod != null) {
					request.setAttribute("method", updatetMethod);
				} else {
					throw new RaplaException(method + " Method not supported in this context");
				}
				String id = pathInfo.substring(appendix + 1);
				request.setAttribute("id", id);
			} else {
				if (method.equals("GET") && listMethod != null) {
					request.setAttribute("method", listMethod);
				} else if (method.equals("POST") && createMethod != null) {
					request.setAttribute("method", createMethod);
				} else {
					throw new RaplaException(method + " Method not supported in this context");
				}
			}
		} catch (RaplaException ex) {
			servlet.serviceError(request, response, servletContext, ex);
			return;
		}
		servlet.service(request, response, servletContext, serviceObj);
		// super.generatePage(servletContext, request, response);
	}

	protected Collection<String> getPublicMethods(Class class1) {
		HashSet<String> result = new HashSet<String>();
		for (Method m : class1.getMethods()) {
			if (Modifier.isPublic(m.getModifiers())) {
			    result.add(m.getName());
			}
		}
		return result;
	}

	public ClassificationFilter[] getClassificationFilter(Map<String, String> simpleFilter, Collection<String> selectedClassificationTypes,
			Collection<String> typeNames) throws RaplaException {
		ClassificationFilter[] filters = null;
		if (simpleFilter != null || typeNames == null) {
			DynamicType[] types = getQuery().getDynamicTypes(null);
			List<ClassificationFilter> filterList = new ArrayList<ClassificationFilter>();
			for (DynamicType type : types) {
				String classificationType = type.getAnnotation(DynamicTypeAnnotations.KEY_CLASSIFICATION_TYPE);
				if (classificationType == null || !selectedClassificationTypes.contains(classificationType)) {
					continue;
				}
				ClassificationFilter classificationFilter = type.newClassificationFilter();
				if (typeNames != null) {
					if (!typeNames.contains(type.getKey())) {
						continue;
					}
				}
				if (simpleFilter != null) {
					for (String key : simpleFilter.keySet()) {
						Attribute att = type.getAttribute(key);
						if (att != null) {
							String value = simpleFilter.get(key);
							// convert from string to attribute type
							Object object = att.convertValue(value);
							if (object != null) {
								classificationFilter.addEqualsRule(att.getKey(), object);
							}
							filterList.add(classificationFilter);
						}
					}
				} else {
					filterList.add(classificationFilter);
				}
			}
			filters = filterList.toArray(new ClassificationFilter[] {});
		}
		return filters;
	}

	protected QueryModule getQuery() {
		return facade;
	}

	protected ModificationModule getModification() {
		return facade;
	}

	protected EntityResolver getEntityResolver() {
		return facade.getOperator();
	}

	private Object getServiceObject() {
		return this;
	}

}