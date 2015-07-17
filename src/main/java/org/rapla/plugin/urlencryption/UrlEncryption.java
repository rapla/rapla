package org.rapla.plugin.urlencryption;

import javax.jws.WebService;

import org.rapla.framework.RaplaException;
import org.rapla.rest.gwtjsonrpc.common.RemoteJsonService;

/**
 * This Interface is used to provide the encryption functionality to the RAPLA Clients.
 * 
 * @author Jonas Kohlbrenner
 * 
 */
@WebService
public interface UrlEncryption extends RemoteJsonService
{
	
	/**
	 *  Parameter in the URL which contains the encrypted parameters
	 */
	String ENCRYPTED_PARAMETER_NAME = "key";


    /**
	 * Encrypts a given string on the RAPLA server.
	 * 
	 * @param plain Plain parameter string
	 * @return String Encrypted parameter string
	 * @throws RaplaException In case the encryption fails
	 */
    public String encrypt(String plain) throws RaplaException;
    
}
