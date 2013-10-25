package org.rapla.servletpages;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;

/**
 * User: kuestermann
 * Date: 15.08.12
 * Time: 19:24
 */
public interface ServletRequestPreprocessor {
    
    /**
     * will return request handle to service
     *
     * @param context
     * @param servletContext
     * @param request
     * @return  null values will be ignored, otherwise return object will be used for further processing
     */
    HttpServletRequest handleRequest(RaplaContext context, ServletContext servletContext, HttpServletRequest request,HttpServletResponse response) throws RaplaException;

    
}
