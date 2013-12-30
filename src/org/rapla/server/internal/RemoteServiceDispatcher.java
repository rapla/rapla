package org.rapla.server.internal;

import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;

import org.rapla.framework.RaplaException;
import org.rapla.server.RemoteSession;
import org.rapla.server.jsonrpc.JsonServlet;


public interface RemoteServiceDispatcher {

    byte[] dispatch(RemoteSession remoteSession, String methodName, Map<String, String> parameterMap) throws Exception;

	JsonServlet getJsonServlet(HttpServletRequest request) throws ServletException, RaplaException;

}
