package org.rapla.server;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.rapla.entities.EntityNotFoundException;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.TypedComponentRole;
import org.rapla.logger.Logger;
import org.rapla.server.RaplaKeyStorage.LoginInfo;
import org.rapla.server.internal.RaplaKeyStorageImpl;
import org.rapla.test.util.RaplaTestCase;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

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

    private static final TypedComponentRole<String> APIKEY = new TypedComponentRole<String>("org.rapla.crypto.server.refreshToken");

    /** Rapla 3 stores a JSON map {clientId: token} in the same preference. Parsing must
     *  understand both that map and the bare legacy string. */
    @Test
    public void apiKeySlotsAreParsed()
    {
        Assert.assertTrue(RaplaKeyStorageImpl.parseSlots(null).isEmpty());
        Assert.assertTrue(RaplaKeyStorageImpl.parseSlots("").isEmpty());

        Map<String, String> legacy = RaplaKeyStorageImpl.parseSlots("bare.jwt.value");
        Assert.assertEquals(1, legacy.size());
        Assert.assertEquals("bare.jwt.value", legacy.get("refreshToken"));

        Map<String, String> slots = RaplaKeyStorageImpl.parseSlots("{\"refreshToken\":\"r2token\",\"spa-1\":\"r3token\"}");
        Assert.assertEquals(2, slots.size());
        Assert.assertEquals("r2token", slots.get("refreshToken"));
        Assert.assertEquals("r3token", slots.get("spa-1"));
    }

    /** A single refreshToken slot stays a bare string so r2 nodes without this patch keep
     *  working; anything else has to be written as the JSON map Rapla 3 expects. */
    @Test
    public void apiKeySlotsAreWritten()
    {
        Map<String, String> single = new LinkedHashMap<String, String>();
        single.put("refreshToken", "r2token");
        Assert.assertEquals("r2token", RaplaKeyStorageImpl.writeSlots(single));

        Map<String, String> several = new LinkedHashMap<String, String>();
        several.put("refreshToken", "r2token");
        several.put("spa-1", "r3token");
        Assert.assertEquals(several, RaplaKeyStorageImpl.parseSlots(RaplaKeyStorageImpl.writeSlots(several)));
    }

    /** The regression: regenerating the r2 refresh token must not wipe the API keys
     *  Rapla 3 stored for the same user, and getAPIKeys must never hand an r3 key out. */
    @Test
    public void regeneratingRefreshTokenKeepsRaplaThreeKeys() throws RaplaException
    {
        Logger logger = RaplaTestCase.initLoger();
        RaplaFacade facade = RaplaTestCase.createFacadeWithFile(logger, "/testdefault.xml");
        RaplaKeyStorageImpl storage = new RaplaKeyStorageImpl(facade, logger);
        User user = facade.newUser();
        user.setUsername("testuser");
        facade.store(user);

        Preferences edit = facade.edit(facade.getPreferences(user));
        edit.putEntry(APIKEY, "{\"refreshToken\":\"oldR2\",\"spa-1\":\"r3token\"}");
        facade.store(edit);

        storage.storeAPIKey(user, "refreshToken", "newR2");

        Map<String, String> slots = RaplaKeyStorageImpl.parseSlots(facade.getPreferences(user).getEntryAsString(APIKEY, null));
        Assert.assertEquals("r3token", slots.get("spa-1"));
        Assert.assertEquals("newR2", slots.get("refreshToken"));

        Collection<String> apiKeys = storage.getAPIKeys(user);
        Assert.assertEquals(1, apiKeys.size());
        Assert.assertEquals("newR2", apiKeys.iterator().next());
    }

}
