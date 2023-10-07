package org.rapla.plugin.urlencryption.server;

import org.rapla.entities.User;
import org.rapla.facade.CalendarNotFoundExeption;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaException;
import org.rapla.inject.Extension;
import org.rapla.logger.Logger;
import org.rapla.plugin.urlencryption.UrlEncryption;
import org.rapla.plugin.urlencryption.UrlEncryptionPlugin;
import org.rapla.server.extensionpoints.ServletRequestPreprocessor;

import javax.inject.Inject;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.TreeMap;

/**
 * User: kuestermann
 * Date: 15.08.12
 * Time: 19:39
 */
@Extension(provides = ServletRequestPreprocessor.class,id= UrlEncryptionPlugin.PLUGIN_ID)
public class UrlEncryptionServletRequestResponsePreprocessor  implements ServletRequestPreprocessor {
    private final UrlEncryptor urlEncryptor;
    private final RaplaFacade facade;
    private final Logger logger;
    @Inject
    public UrlEncryptionServletRequestResponsePreprocessor(UrlEncryptor urlEncryptor, RaplaFacade facade, Logger logger)
    {
    	this.urlEncryptor =  urlEncryptor;
    	this.facade = facade;
    	this.logger = logger;
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
            logger.error(ex.getMessage());
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
            encryptionEnabled = encryptionOption.equals("true");
        }
        // check if the page was called via encrypted parameters
        boolean calledViaEncryptedParameter = false;
        return encryptionEnabled && !calledViaEncryptedParameter;

    }


}
