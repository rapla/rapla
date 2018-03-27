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

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**Currently Rapla supports the following types:
 * <ul>
        <li>weekly</li>
        <li>daily</li>
        </ul>
 */
public class RepeatingEnding implements Serializable {
    //  Don't forget to increase the serialVersionUID when you change the fields
    private static final long serialVersionUID = 1;
    
    private String type;
    
    final static public RepeatingEnding END_DATE = new RepeatingEnding("repeating.end_date");
    final static public RepeatingEnding N_TIMES = new RepeatingEnding("repeating.n_times");
    final static public RepeatingEnding FOREVEVER = new RepeatingEnding("repeating.forever");
    
    private static Map<String,RepeatingEnding> types;

    private RepeatingEnding(String type) {
        this.type = type;
        if (types == null) {
            types = new HashMap<>();
        }
        types.put( type, this);
    }
    
    public boolean is(RepeatingEnding other) {
        if ( other == null)
            return false;
        return type.equals( other.type); 
    }
    
    public static RepeatingEnding findForString(String string  ) {
    	RepeatingEnding type = types.get( string );
    	return type;
    }
    
    public String toString() {
        return type;
    }
    
    public boolean equals( Object other) {
        if ( !(other instanceof RepeatingEnding))
            return false;
        return is( (RepeatingEnding)other);
    }
    
    public int hashCode() {
        return type.hashCode();
    }
}




