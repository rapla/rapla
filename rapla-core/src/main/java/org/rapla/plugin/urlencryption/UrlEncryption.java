package org.rapla.plugin.urlencryption;

import org.rapla.framework.RaplaException;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * This Interface is used to provide the encryption functionality to the RAPLA Clients.
 *
 * @author Jonas Kohlbrenner
 *
 */
@HttpExchange("/api/urlencryption")
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
	@PostExchange
    String encrypt(@RequestBody String plain) throws RaplaException;
    
}
