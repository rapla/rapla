package org.rapla.server;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.rapla.entities.EntityNotFoundException;
import org.rapla.entities.User;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.TypedComponentRole;
import org.rapla.logger.Logger;
import org.rapla.server.RaplaKeyStorage.LoginInfo;
import org.rapla.server.internal.RaplaKeyStorageImpl;
import org.rapla.test.util.RaplaTestCase;

@RunWith(JUnit4.class)
public class RaplaKeyStorageTest  {

	@Test
	public void testKeyStore() throws RaplaException
	{
		Logger logger = RaplaTestCase.initLoger();
		RaplaFacade facade = RaplaTestCase.createFacadeWithFile(logger,"/testdefault.xml");
		RaplaKeyStorageImpl storage = new RaplaKeyStorageImpl(facade,logger);
        User user = facade.newUser();
		user.setUsername("testuser");
		facade.store( user);
		
		TypedComponentRole<String> tagName = new TypedComponentRole<String>("org.rapla.server.secret.test");
		String login ="username";
		String secret = "secret";
		storage.storeLoginInfo(user, tagName, login, secret);
		{
			LoginInfo secrets = storage.getSecrets(user, tagName);
			Assert.assertEquals(login, secrets.login);
			Assert.assertEquals(secret, secrets.secret);
		}
		
		facade.remove( user);
		try
		{
		    storage.getSecrets(user, tagName);
			Assert.fail("Should throw Entity not found exception");
		}
		catch ( EntityNotFoundException ex)
		{
		}
	}

}
