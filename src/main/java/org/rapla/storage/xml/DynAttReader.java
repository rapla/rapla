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
import org.rapla.components.util.xml.RaplaSAXParseException;
import org.rapla.entities.EntityNotFoundException;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.Classifiable;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.internal.AttributeImpl;
import org.rapla.entities.dynamictype.internal.ClassificationImpl;
import org.rapla.entities.dynamictype.internal.DynamicTypeImpl;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.framework.RaplaException;

class DynAttReader extends RaplaXMLReader {
    Classifiable classifiable;
    ClassificationImpl classification;
    Attribute attribute;
    public DynAttReader(RaplaXMLContext context) throws RaplaException {
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
            ReferenceInfo<DynamicType> refInfo;
            if ( id== null) {
                String typeName =  Namespaces.EXTENSION_NS.equals(namespaceURI) ? "rapla:" + localName : localName;
                if ( typeName.equals("rapla:crypto"))
                {
                    return;
                }
                refInfo = getKeyAndPathResolver().getIdForDynamicType(typeName);
                if (refInfo == null)
                {
                    throw createSAXParseException("Dynanic type with name  '" + typeName + "' not found.");
                }
            }
            else {
                refInfo = getId(DynamicType.class, id);
            }
            try
            {
                dynamicType = store.resolve(refInfo);
            }
            catch (EntityNotFoundException e)
            {
                throw createSAXParseException(e.getMessage(), e);
            }
        	Classification newClassification = ((DynamicTypeImpl)dynamicType).newClassificationWithoutCheck(false);
            classification = (ClassificationImpl)newClassification;
            classifiable.setClassification(classification);
            classification.setResolver( store);
       }

        if (level > entryLevel) {
            String id = atts.getValue("idref");
            if ( id != null) {
                attribute = resolve(Attribute.class,id);
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
                parseContent(content);
            }
        }
    }

    private void parseContent(String content) throws RaplaSAXParseException {
        try
        {
            if ( attribute.getRefType() != null)
            {
                final ReferenceInfo id = AttributeImpl.parseRefType(attribute, content, getKeyAndPathResolver());
                if ( id != null)
                {
                    classification.addRefValue(attribute, id);
                }
            }
            else
            {
                Object value = AttributeImpl.parseAttributeValueWithoutRef(attribute, content);
                if ( value != null)
                {
                    classification.addValue( attribute, value);
                }
            }

        }
        catch (RaplaException ex)
        {
            throw createSAXParseException( ex.getMessage(), ex);
        }

    }
    
}