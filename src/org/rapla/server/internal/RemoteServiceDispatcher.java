package org.rapla.server.internal;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;

import org.rapla.entities.User;
import org.rapla.framework.RaplaException;


public interface RemoteServiceDispatcher {

    //byte[] dispatch(RemoteSession remoteSession, String methodName, Map<String, String> parameterMap) throws Exception;

	JsonServletWrapper getJsonServlet(HttpServletRequest request) throws ServletException, RaplaException;

	User getUser(String token) throws RaplaException;

	User getUserWithoutPassword(String username) throws RaplaException;
}
