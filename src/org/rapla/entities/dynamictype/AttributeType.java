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
package org.rapla.entities.dynamictype;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/** Attributes are to DynamicTypes, what properties are to Beans.
Currently Rapla supports the following types:
        <li>string</li>
        <li>int</li>
        <li>date</li>
        <li>boolean</li>
        <li>rapla:category</li>
@see DynamicType */
public class AttributeType implements Serializable {
    //  Don't forget to increase the serialVersionUID when you change the fields
    private static final long serialVersionUID = 1;
    
    private String type;
    
    static public AttributeType STRING = new AttributeType("string");
    static public AttributeType INT = new AttributeType("int");
    static public AttributeType DATE = new AttributeType("date");
    static public AttributeType BOOLEAN = new AttributeType("boolean");
    static public AttributeType CATEGORY = new AttributeType("rapla:category");
    
    private static Map<String,AttributeType> types;

    private AttributeType(String type) {
        this.type = type;
        if (types == null) {
            types = new HashMap<String,AttributeType>();
        }
        types.put( type, this);
    }
    
    public boolean is(AttributeType other) {
        if ( other == null)
            return false;
        return type.equals( other.type); 
    }
    
    public static AttributeType findForString(String string  ) {
         return  types.get( string );
    }
    
    public String toString() {
        return type;
    }
    
    public boolean equals( Object other) {
        if ( !(other instanceof AttributeType))
            return false;
        return is( (AttributeType)other);
    }
    
    public int hashCode() {
        return type.hashCode();
    }
}




