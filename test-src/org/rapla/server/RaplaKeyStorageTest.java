package org.rapla.server;

import org.rapla.RaplaTestCase;
import org.rapla.entities.EntityNotFoundException;
import org.rapla.entities.User;
import org.rapla.facade.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.TypedComponentRole;
import org.rapla.server.RaplaKeyStorage.LoginInfo;
import org.rapla.server.internal.RaplaKeyStorageImpl;

public class RaplaKeyStorageTest extends RaplaTestCase {

	public RaplaKeyStorageTest(String name) {
		super(name);
	}
	
	public void testKeyStore() throws RaplaException
	{
        ClientFacade facade = getFacade();
		RaplaKeyStorageImpl storage = new RaplaKeyStorageImpl(facade,getLogger());
        User user = facade.newUser();
		user.setUsername("testuser");
		facade.store( user);
		
		TypedComponentRole<String> tagName = new TypedComponentRole<String>("org.rapla.server.secret.test");
		String login ="username";
		String secret = "secret";
		storage.storeLoginInfo(user, tagName, login, secret);
		{
			LoginInfo secrets = storage.getSecrets(user, tagName);
			assertEquals( login,secrets.login);
			assertEquals( secret,secrets.secret);
		}
		
		facade.remove( user);
		try
		{
		    storage.getSecrets(user, tagName);
		    fail("Should throw Entity not found exception"); 
		}
		catch ( EntityNotFoundException ex)
		{
		}
	}

}
