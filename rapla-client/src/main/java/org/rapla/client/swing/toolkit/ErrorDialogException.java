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
package org.rapla.client.swing.toolkit;


/** This exception is thrown by the ErrorDialog and
    is used to test error-messages.
    @see ErrorDialog */
final public class ErrorDialogException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    
    int type;

    /** @param type The type of the Error-Message.
    @see ErrorDialog */
    public ErrorDialogException(Throwable throwable,int type) {
        super(String.valueOf(type),throwable);
        this.type = type;
    }

    /** returns the type of the Error-Message.
    @see ErrorDialog */
    public int getType() {
        return type;
    }

}









