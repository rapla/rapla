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
package org.rapla.entities.dynamictype;


/** Attributes are to DynamicTypes, what properties are to Beans.
Currently Rapla supports the following types:
<ul>
        <li>string</li>
        <li>int</li>
        <li>date</li>
        <li>boolean</li>
        <li>rapla:category</li>
        <li>rapla:allocatable</li>
</ul>
@see DynamicType */
public enum AttributeType {
	 STRING("string"),
	    INT("int"),
	    DATE("date"),
	    BOOLEAN("boolean"),
	    CATEGORY("rapla:category"),
	    ALLOCATABLE("rapla:allocatable");
    
    private String type;
   

    AttributeType(String type) {
        this.type = type;
    }
    
    public boolean is(AttributeType other) {
        if ( other == null)
            return false;
        return type.equals( other.type); 
    }
    
    public static AttributeType findForString(String string  ) {
    	for (AttributeType type:values())
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
    
}




