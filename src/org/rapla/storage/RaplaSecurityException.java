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
package org.rapla.storage;

import org.rapla.framework.RaplaException;

/** 
 * This exception is thrown on an invalid login,
 * or when a client tries to access data without
 * the proper permissions.
 */
public class RaplaSecurityException extends RaplaException {
    private static final long serialVersionUID = 1L;
    
    public RaplaSecurityException(String text)  {
        super(text);
    }

    public RaplaSecurityException(Throwable throwable) {
        super(throwable);
    }

    public RaplaSecurityException(String text,Throwable ex) {
        super(text,ex);
    }
}







