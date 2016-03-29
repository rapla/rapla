package org.rapla.plugin.mail;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;

import org.rapla.framework.DefaultConfiguration;
import org.rapla.framework.RaplaException;

@Path("mail/config")
public interface MailConfigService 
{
    @GET
    @Path("external")
	boolean isExternalConfigEnabled() throws RaplaException;
    @POST
	void testMail( DefaultConfiguration config,@QueryParam("defaultSender")String defaultSender) throws RaplaException;
    @GET
	DefaultConfiguration getConfig() throws RaplaException;
//	LoginInfo getLoginInfo() throws RaplaException;
//	void setLogin(String username,String password) throws RaplaException; 
//	public class LoginInfo
//	{
//		public String username;
//		public String password;
//	}
    
}
