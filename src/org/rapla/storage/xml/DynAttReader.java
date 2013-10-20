/*--------------------------------------------------------------------------*
  | Copyright (C) 2006 Christopher Kohlhaas                                  |
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

import java.util.ArrayList;
import java.util.Collection;

import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.AttributeAnnotations;
import org.rapla.entities.dynamictype.Classifiable;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

class DynAttReader extends RaplaXMLReader {
    Classifiable classifiable;
    Classification classification;
    Attribute attribute;

    public DynAttReader(RaplaContext context) throws RaplaException {
        super(context);
    }

    public void setClassifiable(Classifiable classifiable) {
        this.classifiable = classifiable;
    }

    public void processElement(String namespaceURI,String localName,String qName,Attributes atts)
        throws SAXException
    {
        if (level == entryLevel) {
            DynamicType dynamicType;
            String id = atts.getValue("idref");
            if ( id!= null) {
                dynamicType = resolve(DynamicType.TYPE,id);
            } else {
                dynamicType = getDynamicType(localName);
            }
            if (dynamicType == null)
                throwEntityNotFound(localName,null);
            @SuppressWarnings("null")
            Classification newClassification = dynamicType.newClassification(false);
            classification = newClassification;
            classifiable.setClassification(classification);
        }

        if (level > entryLevel) {
            String id = atts.getValue("idref");
            if ( id != null) {
                attribute = resolve(Attribute.TYPE,id);
            } else {
                attribute = classification.getAttribute(localName);
            }

            if (attribute == null) //ignore attributes not found in the classification
                return;

            startContent();
        }
    }

    public void processEnd(String namespaceURI,String localName,String qName)
        throws SAXException
    {
        if (level > entryLevel) {
            String content = readContent();
            if (content != null) {
                Object value = parseAttributeValue(attribute, content);
            	String multiSelect = attribute.getAnnotation(AttributeAnnotations.KEY_MULTI_SELECT);
            	if ( multiSelect != null && Boolean.valueOf(multiSelect))
				{
					Collection<Object> values = classification.getValues(attribute);
					Collection<Object> newCollection = new ArrayList<Object>(values);
					newCollection.add( value);
					classification.setValues(attribute, newCollection);
                }
				else
				{
					classification.setValue(attribute, value);
				}
            }
        }
    }

}




