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

import com.fasterxml.jackson.annotation.JsonCreator;


/**Currently Rapla supports the following types:
  <ul>
        <li>weekly</li>
        <li>daily</li>
        <li>monthly</li>
        <li>yearly</li>
        </ul>
 */
public enum RepeatingType {
    WEEKLY("weekly"),
    DAILY("daily"),
    MONTHLY("monthly"),
    YEARLY("yearly");

    String type;
    RepeatingType(String type) {
        this.type = type;
    }
//     public RepeatingType WEEKLY = new RepeatingType("weekly");
//    public RepeatingType DAILY = new RepeatingType("daily");
//    public RepeatingType MONTHLY = new RepeatingType("monthly");
//    public RepeatingType YEARLY = new RepeatingType("yearly");
//    
   
    
    
    public boolean is(RepeatingType other) {
        if ( other == null)
            return false;
        return type.equals( other.type); 
    }
    
    public static RepeatingType findForString(String string  ) {
    	for (RepeatingType type:values())
    	{
    		if ( type.type.equals( string))
    		{
    			return type;
    		}
    	}
    	return null;
    }
    
    /** Reads both the canonical {@code toString()} form ("weekly", "daily") and the
     *  legacy {@code name()} form ("WEEKLY", "DAILY") persisted by the pre-PRD-011
     *  Gson serializer / Jackson 2. Read-only — serialization stays toString(). */
    @JsonCreator
    public static RepeatingType fromJson(String value) {
        RepeatingType found = findForString(value);
        return found != null ? found : RepeatingType.valueOf(value);
    }

    public String toString() {
        return type;
    }

}




