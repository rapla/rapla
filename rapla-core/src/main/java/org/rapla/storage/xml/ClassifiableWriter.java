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

import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.internal.DynamicTypeImpl;
import org.rapla.framework.RaplaException;

import java.io.IOException;
import java.util.Collection;

class ClassifiableWriter extends RaplaXMLWriter {    
    public ClassifiableWriter(RaplaXMLContext sm) throws RaplaException {
        super(sm);
    }

    protected void printClassification(Classification classification) throws IOException,RaplaException {
        if (classification == null)
            return;
        DynamicType dynamicType = classification.getType();
        boolean internal = ((DynamicTypeImpl)dynamicType).isInternal();
		String namespacePrefix = internal ? "ext:" : "dynatt:";
		String elementKey = dynamicType.getKey();
		if ( internal )
		{
			if (!elementKey.startsWith("rapla:"))
			{
				throw new RaplaException("keys for internal type must start with rapla:");
			}
			elementKey = elementKey.substring("rapla:".length() );
		}
		else
		{
			if (elementKey.startsWith("rapla:"))
			{
				throw new RaplaException("keys for non internal type can't start with rapla:");
			}
		}
		String elementName = namespacePrefix + elementKey;
        
		Attribute[] attributes = classification.getAttributes();
		if ( attributes.length == 0)
		{
			openTag(elementName);
		}
		else
		{
			openElement(elementName);
		}
        for (int i=0;i<attributes.length;i++) {
            Attribute attribute = attributes[i];
            Collection<?> values = classification.getValues(attribute);
            for ( Object value:values)
            {
            	String attributeName = namespacePrefix + attribute.getKey();
            	openElementOnLine(attributeName);
	            
            	printAttributeValue(attribute, value);
            	closeElementOnLine(attributeName);

            	println();
            }
        }
		if ( attributes.length == 0)
		{
			closeElementTag();
		}
		else
		{
			closeElement(elementName);
		}
	}
    

}


