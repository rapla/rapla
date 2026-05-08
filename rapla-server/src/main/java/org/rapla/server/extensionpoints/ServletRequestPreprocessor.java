package org.rapla.server.extensionpoints;

import org.rapla.framework.RaplaException;

import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** you can add servlet pre processer to manipulate request and response before standard processing is
 * done by rapla
 */

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
