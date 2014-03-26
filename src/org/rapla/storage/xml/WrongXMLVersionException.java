/*--------------------------------------------------------------------------*
 | Copyright (C) 2014 Christopher Kohlhaas                                  |
 |                                                                          |
 | This program is free software; you can redistribute it and/or modify     |
 | it under the terms of the GNU General Public License as published by the |
 | Free Software Foundation. A copy of the license has been included with   |
 | these distribution in the COPYING file, if not go to www.fsf.org .       |
 |                                                                          |
 | As a special exception, you are granted the permissions to link this     |
 | program with every library, which license fulfills the Open Source       |
 | Definition as published by the Open Source Initiative (OSI).             |
 *--------------------------------------------------------------------------*/
package org.rapla.storage.xml;

public class WrongXMLVersionException extends Exception {
    private static final long serialVersionUID = 1L;
    
    String version;
    public WrongXMLVersionException(String version) {
        super("Wrong Version Exception " + version);
        this.version = version;
    }
    
    public String getVersion() {
        return version;
    }

}
