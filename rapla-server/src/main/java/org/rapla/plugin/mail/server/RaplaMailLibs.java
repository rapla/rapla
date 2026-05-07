package org.rapla.plugin.mail.server;

import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import java.util.Properties;

public class RaplaMailLibs
{
	static public Object getSession(Properties props) {
		jakarta.mail.Authenticator authenticator = null;
		final String username2 = (String) props.get("username");
		final String password2 = (String) props.get("password");
		if ( props.containsKey("username"))
		{
			authenticator = new jakarta.mail.Authenticator() {
		    	   protected PasswordAuthentication getPasswordAuthentication() {
						return new PasswordAuthentication(username2,password2);
					}
		       };
		}
		Object session = Session.getInstance(props, authenticator);
		return session;
	}
}