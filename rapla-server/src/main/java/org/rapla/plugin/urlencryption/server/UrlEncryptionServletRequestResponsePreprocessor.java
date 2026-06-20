package org.rapla.plugin.urlencryption.server;

import org.rapla.entities.User;
import org.rapla.facade.CalendarNotFoundExeption;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.rapla.plugin.urlencryption.UrlEncryption;
import org.rapla.plugin.urlencryption.UrlEncryptionPlugin;
import org.rapla.server.extensionpoints.ServletRequestPreprocessor;

import org.springframework.beans.factory.annotation.Autowired;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.TreeMap;

import java.time.LocalDateTime;
/**
 * User: kuestermann
 * LocalDateTime: 15.08.12
 * Time: 19:39
 */

public class UrlEncryptionServletRequestResponsePreprocessor  implements ServletRequestPreprocessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(UrlEncryptionServletRequestResponsePreprocessor.class);
    private final UrlEncryptor urlEncryptor;
    private final RaplaFacade facade;
    @Autowired
    public UrlEncryptionServletRequestResponsePreprocessor(UrlEncryptor urlEncryptor, RaplaFacade facade)
    {
    	this.urlEncryptor =  urlEncryptor;
    	this.facade = facade;
    }
    
    public HttpServletRequest handleRequest( ServletContext servletContext, HttpServletRequest request, HttpServletResponse response) throws RaplaException {
        try {
            // check if the page was called via encrypted parameters
            if (request.getParameter(UrlEncryption.ENCRYPTED_PARAMETER_NAME) != null && request.getParameter("page") == null )
            {
            	HttpServletRequest newRequest = handleEncryptedSource(request);
            	return newRequest;
            }
            else
            {
            	if (isCalendarExportCalledIllegally(request))
            	{
            	    response.sendError(403);
            	}
            	return request;
            }
        } catch (Exception ignored) {
            return request;
        }
    }
    
    /**
     * Looks whether an HttpServletRequest contains encrypted parameters.
     * If so, the parameters will be decrypted and passed on together with the original request.
     *
     * @param request Original request
     * @return HttpServletRequest Request with decrypted URL parameters and null if request could not be decrypted
     */
    public HttpServletRequest handleEncryptedSource(HttpServletRequest request) {
        String parameters = request.getParameter(UrlEncryption.ENCRYPTED_PARAMETER_NAME);
        // If no encrypted parameters are provided return the original request.
        if (parameters == null)
            return request;
        try {
            final EncryptedHttpServletRequest servletRequest = new EncryptedHttpServletRequest(request, urlEncryptor);
            return servletRequest;
        } catch (Exception ex) {
            LOGGER.error(ex.getMessage());
            return null;
        }
    }

    
    /**
     * Checks if the requested page has been called illegally.
     * This is the case, when the page was called using plain parameters
     * instead of encrypted ones.
     *
     * @param request Page request Object
     * @return boolean true if page was called illegally
     */
    public boolean isCalendarExportCalledIllegally(HttpServletRequest request) throws RaplaException {
    	 String username = request.getParameter("user");
        final String contextPath = request.getPathInfo();
        if ( contextPath != null && contextPath.toLowerCase().contains("terminal-export") ) {
            return true;
        }

        if ( contextPath == null || (!contextPath.toLowerCase().contains("cal") )) {
            return false;
        }

        if (username== null)  {
             // no calendar information, so no model and no a
             return false;
         }
        String filename = request.getParameter("file");
        final User user = facade.getUser(username);
         final CalendarSelectionModel model = facade.newCalendarModel( user);
         try
         {
         	model.load(filename);
         } 
         catch (CalendarNotFoundExeption ex)
         {
         	return false;            	
         }

        // check if encryption is enabled for the requested source
        boolean encryptionEnabled = false;
        Object encryptionOption = model.getOption(UrlEncryptionPlugin.URL_ENCRYPTION);
        if (encryptionOption != null)
        {
            encryptionEnabled = UrlEncryptionPlugin.isEnabled(encryptionOption.toString());
        }
        // check if the page was called via encrypted parameters
        boolean calledViaEncryptedParameter = false;
        return encryptionEnabled && !calledViaEncryptedParameter;

    }


}
