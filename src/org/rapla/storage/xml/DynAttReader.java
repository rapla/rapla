/*--------------------------------------------------------------------------*
  | Copyright (C) 2013 Christopher Kohlhaas                                  |
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

import org.rapla.components.util.xml.RaplaSAXAttributes;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.Classifiable;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.internal.ClassificationImpl;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;

class DynAttReader extends RaplaXMLReader {
    Classifiable classifiable;
    ClassificationImpl classification;
    Attribute attribute;
    public DynAttReader(RaplaContext context) throws RaplaException {
        super(context);
    }

    public void setClassifiable(Classifiable classifiable) {
        this.classifiable = classifiable;
    }

    @Override
    public void processElement(String namespaceURI,String localName,RaplaSAXAttributes atts)
        throws RaplaSAXParseException
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
            classification = (ClassificationImpl)newClassification;
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

    @Override
    public void processEnd(String namespaceURI,String localName)
        throws RaplaSAXParseException
    {
        if (level > entryLevel) {
            String content = readContent();
            if (content != null) {
                Object value = parseAttributeValue(attribute, content);
            	classification.addValue( attribute, value);
            }
        }
    }
    
}