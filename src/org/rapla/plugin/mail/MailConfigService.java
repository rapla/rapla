package org.rapla.plugin.mail;

import javax.jws.WebService;

import org.rapla.framework.DefaultConfiguration;
import org.rapla.framework.RaplaException;

@WebService
public interface MailConfigService
{
	boolean isExternalConfigEnabled() throws RaplaException;
	void testMail( DefaultConfiguration config,String defaultSender) throws RaplaException;
	DefaultConfiguration getConfig() throws RaplaException;
//	LoginInfo getLoginInfo() throws RaplaException;
//	void setLogin(String username,String password) throws RaplaException; 
//	public class LoginInfo
//	{
//		public String username;
//		public String password;
//	}
    
}
