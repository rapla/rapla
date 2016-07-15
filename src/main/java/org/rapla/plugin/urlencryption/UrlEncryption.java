package org.rapla.plugin.urlencryption;

import javax.ws.rs.POST;
import javax.ws.rs.Path;

import org.rapla.framework.RaplaException;

/**
 * This Interface is used to provide the encryption functionality to the RAPLA Clients.
 * 
 * @author Jonas Kohlbrenner
 * 
 */
@Path("urlencryption")
public interface UrlEncryption 
{
	
	/**
	 *  Parameter in the URL which contains the encrypted parameters
	 */
	String ENCRYPTED_PARAMETER_NAME = "key";
	String ENCRYPTED_SALT_PARAMETER_NAME = "salt";


    /**
	 * Encrypts a given string on the RAPLA server.
	 * 
	 * @param plain Plain parameter string
	 * @return String Encrypted parameter string
	 * @throws RaplaException In case the encryption fails
	 */
	@POST
    String encrypt(String plain) throws RaplaException;
    
}
