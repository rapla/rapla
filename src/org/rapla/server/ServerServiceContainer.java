package org.rapla.server;

import javax.servlet.http.HttpServletRequest;

import org.rapla.entities.User;
import org.rapla.framework.Configuration;
import org.rapla.framework.Container;
import org.rapla.framework.RaplaContextException;
import org.rapla.framework.RaplaException;
import org.rapla.servletpages.RaplaPageGenerator;

public interface ServerServiceContainer extends Container 
{
    <T> void addRemoteMethodFactory( Class<T> service, Class<? extends RemoteMethodFactory<T>> factory);
    <T> void addRemoteMethodFactory( Class<T> service, Class<? extends RemoteMethodFactory<T>> factory, Configuration config);

    /**
     * You can add arbitrary serlvet pages to your rapla webapp.
     *<p>
     * Example that adds a page with the name "my-page-name" and the class
     * "org.rapla.plugin.myplugin.MyPageGenerator". You can call this page with <code>rapla?page=my-page-name</code>
     * </p>
     * <p>
     * In the provideService Method of your PluginDescriptor do the following
     * </p>
     <pre>
     container.addContainerProvidedComponent( RaplaExtensionPoints.SERVLET_PAGE_EXTENSION, "org.rapla.plugin.myplugin.MyPageGenerator", "my-page-name", config);
     </pre>

    *@see org.rapla.servletpages.RaplaPageGenerator
     */
    <T extends RaplaPageGenerator> void addWebpage(String pagename, Class<T> pageClass);
    <T extends RaplaPageGenerator> void addWebpage(String pagename, Class<T> pageClass, Configuration config);
    /** @return null when the server doesn't have the webpage  */
	RaplaPageGenerator getWebpage(String page);
	
	public User getUser(HttpServletRequest request) throws RaplaException;
    <T> T createWebservice(Class<T> role,HttpServletRequest request ) throws RaplaException;
    boolean hasWebservice(String interfaceName);

	
}
