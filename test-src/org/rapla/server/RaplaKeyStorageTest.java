package org.rapla.server;

import org.rapla.RaplaTestCase;
import org.rapla.entities.User;
import org.rapla.framework.RaplaException;
import org.rapla.server.RaplaKeyStorage.LoginInfo;
import org.rapla.server.internal.RaplaKeyStorageImpl;

public class RaplaKeyStorageTest extends RaplaTestCase {

	public RaplaKeyStorageTest(String name) {
		super(name);
	}
	
	public void testKeyStore() throws RaplaException
	{
		RaplaKeyStorageImpl storage = new RaplaKeyStorageImpl(getContext());
		User user = getFacade().newUser();
		user.setUsername("testuser");
		getFacade().store( user);
		
		String tagName = "test";
		String login ="username";
		String secret = "secret";
		storage.storeLoginInfo(user, tagName, login, secret);
		{
			LoginInfo secrets = storage.getSecrets(user, tagName);
			assertEquals( login,secrets.login);
			assertEquals( secret,secrets.secret);
		}
		
		getFacade().remove( user);
		
		LoginInfo secrets = storage.getSecrets(user, tagName);
		{
			assertNull( secrets);
		}
	}

}
