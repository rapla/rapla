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
package org.rapla.entities.domain;


import java.util.Locale;

/**Currently Rapla supports the following request status:
  <ul>
        <li>requested</li>
 </ul>
 */
public enum RequestStatus {
    REQUESTED("requested");

    String type;
    RequestStatus(String type) {
        this.type = type;
    }

    
    public boolean is(RequestStatus other) {
        if ( other == null)
            return false;
        return type.equals( other.type); 
    }
    
    public static RequestStatus findForString(String string  ) {
    	for (RequestStatus type:values())
    	{
    		if ( type.type.equals( string))
    		{
    			return type;
    		}
    	}
    	return null;
    }
    
    public String toString() {
        return type;
    }

    public String getName(Locale locale) {
        if ( locale.getLanguage().contains("de")) {
            return "Anfrage";
        } else {
             return "request";
        }
    }
}




