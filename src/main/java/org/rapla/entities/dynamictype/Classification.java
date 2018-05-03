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

import jsinterop.annotations.JsIgnore;
import jsinterop.annotations.JsType;
import org.rapla.entities.Named;

import java.util.Collection;
import java.util.Locale;
/** A Classification is an instance of a DynamicType. It holds the
 * attribute values for the attributesof the corresponding type. You
 * need one classification for each object you want to
 * classify.
 */
@JsType
public interface Classification extends Named,Cloneable {
    DynamicType getType();
    String getName(Locale locale);
    String format(Locale locale, String annotationName);
    Attribute[] getAttributes();
    Attribute getAttribute(String key);
    void setValueForAttribute(Attribute attribute,Object value);
    <T> void setValues(String attributeKey, T[] values);
    @JsIgnore
    <T> void setValues(Attribute attribute,Collection<T> values);
    /** calls setValue(getAttribute(key),value)*/
    void setValue(String key,Object value);

    /** calls getValue(getAttribte(key))*/
    Object getValueForAttribute(Attribute attribute);
    Object getValue(String key);

    Collection<Object> getValues(Attribute attribute);
    String getValueAsString(Attribute attribute,Locale locale);
    Object clone();
}




