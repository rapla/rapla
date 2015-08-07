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
import java.util.Collection;
import java.util.Locale;

import org.rapla.entities.Named;
/** A Classification is an instance of a DynamicType. It holds the
 * attribute values for the attributesof the corresponding type. You
 * need one classification for each object you want to
 * classify. 
 */
public interface Classification extends Named,Cloneable {
    DynamicType getType();
    String getName(Locale locale);
    String format(Locale locale, String annotationName);
    Attribute[] getAttributes();
    Attribute getAttribute(String key);
    void setValue(Attribute attribute,Object value);
    <T> void setValues(Attribute attribute,Collection<T> values);
    /** calls setValue(getAttribute(key),value)*/
    void setValue(String key,Object value);
    
    /** calls getValue(getAttribte(key))*/
    Object getValue(Attribute attribute);
    Object getValue(String key);
    
    Collection<Object> getValues(Attribute attribute);
    String getValueAsString(Attribute attribute,Locale locale);
    Object clone();
}




