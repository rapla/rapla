package org.rapla.server.servletpages;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.rapla.framework.RaplaException;
import org.rapla.inject.ExtensionPoint;
import org.rapla.inject.InjectionContext;

/** you can add servlet pre processer to manipulate request and response before standard processing is
 * done by rapla
 */
@ExtensionPoint(context = InjectionContext.server,id="servletprocessor")
public interface ServletRequestPreprocessor {
    
    /**
     * will return request handle to service
     *
     * @param servletContext
     * @param request
     * @param response
     * @return  null values will be ignored, otherwise return object will be used for further processing
     */
    HttpServletRequest handleRequest(ServletContext servletContext, HttpServletRequest request,HttpServletResponse response) throws RaplaException;

    
}
