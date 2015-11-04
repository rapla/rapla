package org.rapla.server;

import org.rapla.server.servletpages.RaplaPageGenerator;

public interface ServerServiceContainer
{
    /** @return null when the server doesn't have the webpage  */
    // main servlet
	RaplaPageGenerator getWebpage(String page);

    // json servlet
    //<T> T createWebservice(Class<T> role,HttpServletRequest request ) throws RaplaException;
    //
    //boolean hasWebservice(String interfaceName);

	
}
