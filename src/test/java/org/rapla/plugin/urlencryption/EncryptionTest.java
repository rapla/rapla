/*--------------------------------------------------------------------------*
 | Copyright (C) 2014 Christopher Kohlhaas                                  |
 |                                                                          |
 | This program is free software; you can redistribute it and/or modify     |
 | it under the terms of the GNU General Public License as published by the |
 | Free Software Foundation. A copy of the license has been included with   |
 | these distribution in the COPYING file, if not go to www.fsf.org         |
 |                                                                          |
 | As a special exception, you are granted the permissions to link this     |
 | program with every library, which license fulfills the Open Source       |
 | Definition as published by the Open Source Initiative (OSI).             |
 *--------------------------------------------------------------------------*/
package org.rapla.plugin.urlencryption;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.rapla.MockMailer;
import org.rapla.entities.User;
import org.rapla.facade.RaplaFacade;
import org.rapla.logger.Logger;
import org.rapla.plugin.mail.server.MailToUserImpl;
import org.rapla.plugin.urlencryption.server.EncryptedHttpServletRequest;
import org.rapla.plugin.urlencryption.server.UrlEncryptor;
import org.rapla.server.RaplaKeyStorage;
import org.rapla.server.RemoteSession;
import org.rapla.server.internal.RaplaKeyStorageImpl;
import org.rapla.server.internal.RemoteSessionImpl;
import org.rapla.server.internal.ServerServiceImpl;
import org.rapla.storage.RaplaSecurityException;
import org.rapla.test.util.RaplaTestCase;

import javax.servlet.http.HttpServletRequest;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Locale;
import java.util.Map;

@RunWith(JUnit4.class)
public class EncryptionTest {
    ServerServiceImpl raplaServer;

    RaplaFacade facade1;
    Locale locale;

    Logger logger = RaplaTestCase.initLoger();

    @Before
    public void setUp() throws Exception {
        raplaServer = (ServerServiceImpl) RaplaTestCase.createServiceContainer(logger, "/testdefault.xml");
        // start the server
        // start the client service
        facade1 =  raplaServer.getFacade();
        locale = Locale.getDefault();
    }
    

    @Test
    public void test() throws Exception 
    {
        User user = facade1.getUser("homer");
        RemoteSessionImpl session = new RemoteSessionImpl(logger, user);
        RaplaKeyStorage keyStore = new RaplaKeyStorageImpl(facade1, logger);
        UrlEncryptor urlEncryptor = new UrlEncryptor(facade1, logger,keyStore, session);
        String filename= "test+ ";
        String pageParameters = "user=" + user.getUsername();
        if (filename != null)
        {
            pageParameters = pageParameters + "&file=" + URLEncoder.encode(filename, "UTF-8");
        }
        String userId = user.getId();
        String encryptedParamters = urlEncryptor.encrypt(pageParameters, userId);
        String encryptedParamterWithoutSalt=encryptedParamters.substring(0,encryptedParamters.indexOf("&salt="));
        final String salt = Integer.toString(userId.hashCode());

        String decrypt = urlEncryptor.decrypt(encryptedParamterWithoutSalt, salt);
        Assert.assertEquals(pageParameters, decrypt);
        Map<String, String[]> map = EncryptedHttpServletRequest.createParamterMapFromQueryString(decrypt);
        String decryptedFile = URLDecoder.decode(map.get("file")[0],"UTF-8");
        Assert.assertEquals(filename, decryptedFile);
    }
 
}

