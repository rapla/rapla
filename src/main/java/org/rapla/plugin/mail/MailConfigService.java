package org.rapla.plugin.mail;

import org.rapla.framework.DefaultConfiguration;
import org.rapla.framework.RaplaException;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange("/mail/config")
public interface MailConfigService
{
    @GetExchange("/external")
	boolean isExternalConfigEnabled() throws RaplaException;
    @PostExchange
	void testMail(@RequestBody DefaultConfiguration config, @RequestParam(value = "defaultSender", required = false) String defaultSender) throws RaplaException;
    @GetExchange
	DefaultConfiguration getConfig() throws RaplaException;
//	LoginInfo getLoginInfo() throws RaplaException;
//	void setLogin(String username,String password) throws RaplaException; 
//	public class LoginInfo
//	{
//		public String username;
//		public String password;
//	}
    
}
