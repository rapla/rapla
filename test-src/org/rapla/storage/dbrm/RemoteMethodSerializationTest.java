/*--------------------------------------------------------------------------*
 | Copyright (C) 2006 Christopher Kohlhaas                                  |
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
package org.rapla.storage.dbrm;
import java.util.Locale;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.rapla.RaplaTestCase;
import org.rapla.framework.Provider;
import org.rapla.framework.RaplaContextException;
import org.rapla.storage.CachableStorageOperator;
import org.rapla.storage.LocalCache;
import org.rapla.storage.impl.EntityStore;

public class RemoteMethodSerializationTest extends RaplaTestCase {
    Locale locale;

    public RemoteMethodSerializationTest(String name) {
        super(name);
    }

    public static Test suite() {
        return new TestSuite(RemoteMethodSerializationTest.class);
    }

    protected void setUp() throws Exception {
        super.setUp();
    }

    
    
    public void testSerialization() throws Exception {
		CachableStorageOperator operator = (CachableStorageOperator) getFacade().getOperator();
		final LocalCache cache = operator.getCache();

		Provider<EntityStore> storeProvider = new Provider<EntityStore>() {

			
			public EntityStore get() throws RaplaContextException {
				return new EntityStore( cache, cache.getSuperCategory());
			}
    		
		};
		RemoteMethodSerialization serialization = new RemoteMethodSerialization(getContext(), storeProvider, cache);
		Integer[][] array = new Integer[][]{};
		String string = "{{},{0}}";
		Class<? extends Integer[][]> class1 = array.getClass();
		Integer[][] result = (Integer[][]) serialization.convertFromString(class1, string);
		Integer[][] expected = new Integer[][]{{},{0}};
		assertEquals(expected.length,result.length);
		for ( int i=0;i<result.length;i++)
		{
			Integer[] exp = expected[i];
			Integer[] res = result[i];
			assertEquals(exp.length,res.length);
			for ( int j=0;j<res.length;j++)
			{
				assertEquals(exp[j],res[j]);
			}
		}		
    }
}





