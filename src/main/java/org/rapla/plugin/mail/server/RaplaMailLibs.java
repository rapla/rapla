package org.rapla.plugin.mail.server;

import java.util.Properties;

import javax.mail.PasswordAuthentication;
import javax.mail.Session;

public class RaplaMailLibs
{
	static public Object getSession(Properties props) {
		javax.mail.Authenticator authenticator = null;
		final String username2 = (String) props.get("username");
		final String password2 = (String) props.get("password");
		if ( props.containsKey("username"))
		{
			authenticator = new javax.mail.Authenticator() {
		    	   protected PasswordAuthentication getPasswordAuthentication() {
						return new PasswordAuthentication(username2,password2);
					}
		       };
		}
		Object session = Session.getInstance(props, authenticator);
		return session;
	}
}