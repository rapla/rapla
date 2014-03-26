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
package org.rapla.plugin.jndi.server;

import java.util.Map;
import java.util.TreeMap;

import org.rapla.facade.RaplaComponent;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.framework.logger.Logger;
import org.rapla.plugin.jndi.internal.JNDITest;
import org.rapla.server.RemoteMethodFactory;
import org.rapla.server.RemoteSession;
import org.rapla.storage.RaplaSecurityException;

public class RaplaJNDITestOnLocalhost extends RaplaComponent implements JNDITest, RemoteMethodFactory<JNDITest>
{
        public RaplaJNDITestOnLocalhost( RaplaContext context)  {
            super( context );
        }

        public void test(String config,String username,String password ) throws RaplaException 
        {
            String[] test = config.split("RAPLANEXT");
            Map<String,String> map = new TreeMap<String,String>();
            for (int i =0;i<test.length;i++)
            {
                String next = test[i];
                int delimeter = next.indexOf("=");
                String key = next.substring(0, delimeter);
                String value = next.substring( delimeter+1);
                map.put(key, value);
            }
            JNDIAuthenticationStore testStore;
            Logger logger = getLogger();
            testStore = JNDIAuthenticationStore.createJNDIAuthenticationStore(map,
                    logger);
            logger.info("Test of JNDI Plugin started");
            boolean authenticate;
          	if ( password == null || password.equals(""))
        	{
              	throw new RaplaException("LDAP Plugin doesnt accept empty passwords.");
        	}

            try {
                authenticate = testStore.authenticate(username, password);
            } catch (Exception e) {
            	throw new RaplaException(e);
            } finally {
                testStore.dispose();
            }
            if (!authenticate)
            {
                throw new RaplaSecurityException("Can establish connection but can't authenticate test user " + username);
            }
            logger.info("Test of JNDI Plugin successfull");
        }

		public JNDITest createService(RemoteSession remoteSession) {
			return this;
		}

        
}

