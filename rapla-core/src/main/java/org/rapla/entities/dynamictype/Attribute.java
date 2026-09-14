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

import org.rapla.entities.Annotatable;
import org.rapla.entities.Entity;
import org.rapla.entities.MultiLanguageNamed;

/** Attributes are to DynamicTypes, what properties are to Beans.
Currently Rapla supports the following types:
<ul>
        <li>string</li>
        <li>int</li>
        <li>date</li>
        <li>boolean</li>
        <li>rapla:category</li>
        </ul>
@see DynamicType */
public interface Attribute extends Entity<Attribute>, MultiLanguageNamed, Annotatable
{
    AttributeType getType();

    Class<? extends Entity> getRefType();

    /** Set the type of the Attribute.
    <strong>Warning:</strong> Changing the type after initialization can lead to data loss,
        if there are already classifications that use this attribute and the classification value
        can't be converted to the new type. Example a non numerical string can't be converted to an int.*/
    void setType(AttributeType type);

    void setKey(String key);

    /** The Key is identifier in string-form. Keys could be helpfull
        for interaction with other modules. An Invoice-Plugin could
        work on attributes with a "price" key. Keys also allow for a
        better readability of the XML-File. Changing of a key is
        possible, but should be used with care.
    */
    String getKey();

    Object defaultValue();

    /** converts the passed value to fit the attributes type.
        Example Conversions are:
        <ul>
          <li>to string: The result of the method toString() will be the new value.</li>
          <li>boolean to int: The new value will be 1 when the oldValue is true. Otherwise it is 0.</li>
          <li>other types to int: First the value will be converted to string-type. And then the
          trimmed string will be parsed for Integer-values. If that is not possible the new value will
          be null</li>
          <li>to boolean: First the value will be converted to string-type. If the trimmed string equals
          "0" or "false" the new value is false. Otherwise it is true</li>
        </ul>

     */
    Object convertValue(Object oldValue);

    /** Checks if the passed value matches the attribute type or needs conversion.
        @see #convertValue
     */
    boolean needsChange(Object value);

    boolean isOptional();

    void setOptional(boolean bOptional);

    Object getConstraint(String key);

    Class<?> getConstraintClass(String key);

    void setConstraint(String key, Object constraint);

    String[] getConstraintKeys();

    DynamicType getDynamicType();

    Attribute[] ATTRIBUTE_ARRAY = new Attribute[0];

    void setDefaultValue(Object value);

}
