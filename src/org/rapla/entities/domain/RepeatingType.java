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
package org.rapla.entities.domain;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**Currently Rapla supports the following types:
        <li>weekly</li>
        <li>daily</li>
 */
public class RepeatingType implements Serializable {
    //  Don't forget to increase the serialVersionUID when you change the fields
    private static final long serialVersionUID = 1;
    
    private String type;
    
    final static public RepeatingType WEEKLY = new RepeatingType("weekly");
    final static public RepeatingType DAILY = new RepeatingType("daily");
    final static public RepeatingType MONTHLY = new RepeatingType("monthly");
    final static public RepeatingType YEARLY = new RepeatingType("yearly");
    
    private static Map<String,RepeatingType> types;

    private RepeatingType(String type) {
        this.type = type;
        if (types == null) {
            types = new HashMap<String,RepeatingType>();
        }
        types.put( type, this);
    }
    
    public boolean is(RepeatingType other) {
        if ( other == null)
            return false;
        return type.equals( other.type); 
    }
    
    public static RepeatingType findForString(String string  ) {
    	RepeatingType type =  types.get( string );
    	return type;
    }
    
    public String toString() {
        return type;
    }
    
    public boolean equals( Object other) {
        if ( !(other instanceof RepeatingType))
            return false;
        return is( (RepeatingType)other);
    }
    
    public int hashCode() {
        return type.hashCode();
    }
}




