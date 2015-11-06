package org.rapla.server;

import org.rapla.framework.Disposable;
import org.rapla.jsonrpc.server.WebserviceCreator;
import org.rapla.server.servletpages.RaplaPageGenerator;
import org.rapla.server.servletpages.ServletRequestPreprocessor;
import org.rapla.storage.StorageOperator;

import java.util.Collection;
import java.util.Map;

public interface ServerServiceContainer extends Disposable
{
    /** @return null when the server doesn't have the webpage  */
    // main servlet
	RaplaPageGenerator getWebpage(String page);

    void setServiceMap(Map<String,WebserviceCreator> serviceMap);

    Collection<ServletRequestPreprocessor> getServletRequestPreprocessors();

    StorageOperator getOperator();

    String getFirstAdmin();
    // json servlet
    //<T> T createWebservice(Class<T> role,HttpServletRequest request ) throws RaplaException;
    //
    //boolean hasWebservice(String interfaceName);

	
}
