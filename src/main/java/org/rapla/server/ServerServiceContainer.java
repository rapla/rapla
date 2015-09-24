package org.rapla.server;

import org.rapla.entities.User;
import org.rapla.framework.RaplaException;
import org.rapla.server.servletpages.RaplaPageGenerator;

import javax.servlet.http.HttpServletRequest;

public interface ServerServiceContainer
{
    /** @return null when the server doesn't have the webpage  */
    // main servlet
	RaplaPageGenerator getWebpage(String page);

    // abstract rest page
	public User getUser(HttpServletRequest request) throws RaplaException;
    // json servlet
    <T> T createWebservice(Class<T> role,HttpServletRequest request ) throws RaplaException;
    //
    boolean hasWebservice(String interfaceName);

	
}
