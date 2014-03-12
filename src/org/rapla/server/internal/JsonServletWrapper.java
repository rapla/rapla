package org.rapla.server.internal;

import java.io.IOException;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.rapla.framework.RaplaContextException;
import org.rapla.server.RemoteMethodFactory;
import org.rapla.server.RemoteSession;

import com.google.gwtjsonrpc.server.JsonServlet;

public class JsonServletWrapper
{
	JsonServlet servlet;
	RemoteMethodFactory factory;
	
	public JsonServletWrapper(RemoteMethodFactory factory, JsonServlet servlet) 
	{
		this.factory = factory;
		this.servlet = servlet;
	}

	public void service(HttpServletRequest request,	HttpServletResponse response, ServletContext servletContext,RemoteSession remoteSession) throws RaplaContextException, IOException {
		Object impl = factory.createService(remoteSession);
		servlet.service(request, response, servletContext, impl);
		
	}
	
}