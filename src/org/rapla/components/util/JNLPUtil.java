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

package org.rapla.components.util;

import java.lang.reflect.Method;
import java.net.URL;
/** returns the codebase in an webstart application */
abstract public class JNLPUtil {
    final static String basicService = "javax.jnlp.BasicService";
    final public static URL getCodeBase() throws Exception {
        try {
            Class<?> serviceManagerC = Class.forName("javax.jnlp.ServiceManager");
            Class<?> basicServiceC = Class.forName( basicService );
            //Class unavailableServiceException = Class.forName("javax.jnlp.UnavailableServiceException");

            Method lookup = serviceManagerC.getMethod("lookup", new Class[] {String.class});
            Method getCodeBase = basicServiceC.getMethod("getCodeBase", new Class[] {});
            Object service = lookup.invoke( null, new Object[] { basicService });
            return (URL) getCodeBase.invoke( service, new Object[] {});
        } catch (ClassNotFoundException ex ) {
            throw new Exception( "Webstart not available :" + ex.getMessage());
        }
    }
}
