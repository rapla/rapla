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

import org.rapla.components.util.xml.RaplaSAXAttributes;
import org.rapla.components.util.xml.RaplaSAXParseException;
import org.rapla.entities.EntityNotFoundException;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.ClassificationFilter;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.entities.dynamictype.internal.AttributeImpl;
import org.rapla.entities.dynamictype.internal.DynamicTypeImpl;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.framework.RaplaException;

import java.util.ArrayList;
import java.util.Collection;

class ClassificationFilterReader extends RaplaXMLReader {
    
    DynamicType dynamicType;
    ClassificationFilter filter;
    Attribute attribute;
    String operator;
    Collection<ClassificationFilter> filterList = new ArrayList<>();
    Collection<Object[]> conditions = new ArrayList<>();
    int ruleCount;
    private boolean defaultResourceTypes = true;
   

    private boolean defaultEventTypes = true;
  

    public ClassificationFilterReader(RaplaXMLContext sm) throws RaplaException {
        super(sm);
    }

    public void clear() {
        filterList.clear();
        defaultResourceTypes = true;
        defaultEventTypes = true;
    }

    public ClassificationFilter[] getFilters() {
        return 
            filterList.toArray(ClassificationFilter.CLASSIFICATIONFILTER_ARRAY);
    }

    @Override
    public void processElement(String namespaceURI,String localName,RaplaSAXAttributes atts)
        throws RaplaSAXParseException
    {
        if (localName.equals("classificationfilter"))
        {
            String id = atts.getValue("dynamictypeidref");
            ReferenceInfo<DynamicType> refInfo;
            if ( id== null) {
                String typeName = getString(atts,"dynamictype");
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
                dynamicType =  store.resolve(refInfo);
            }
            catch(EntityNotFoundException ex)
            {
                throw createSAXParseException(ex.getMessage(), ex);
            }

            final String annotation = dynamicType.getAnnotation(DynamicTypeAnnotations.KEY_CLASSIFICATION_TYPE);
            boolean eventType = annotation != null && annotation.equals( DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION);
            if (eventType )
            {
                defaultEventTypes = false;
            }
            else
            {
                defaultResourceTypes = false;
            }
            filter = ((DynamicTypeImpl)dynamicType).newClassificationFilterWithoutCheck();
            ruleCount = 0;
            filterList.add(filter);
        }

        if (localName.equals("rule"))
        {
            String id = atts.getValue("attributeidref");
            if ( id != null) {
                attribute = resolve(Attribute.class, id);
            } else {
                String attributeName = getString(atts,"attribute");
                attribute = dynamicType.getAttribute(attributeName);
                if (attribute == null) {
                	getLogger().error("Error reading filter with " + dynamicType +" Attribute: " + attributeName,null);
                	return;
                }
            }
            conditions.clear();
        }

        if (localName.equals("orCond"))
        {
            operator = getString(atts,"operator");
            startContent();
        }
    }

    @Override
    public void processEnd(String namespaceURI,String localName)
        throws RaplaSAXParseException
    {

        if (localName.equals("rule") && filter != null)
        {
            final Object[][] array = conditions.toArray(new Object[][] {}  );
            filter.setRule(ruleCount ++
                           ,attribute
                           ,array
                           );
        }

        if (localName.equals("orCond") && attribute!= null)
        {
            final String trim = readContent().trim();
            Object value;
            if ( attribute.getRefType() != null)
            {
                try
                {
                    final ReferenceInfo referenceInfo = AttributeImpl.parseRefType(attribute, trim, getKeyAndPathResolver());
                    value = referenceInfo;
                }
                catch(RaplaException ex)
                {
                    throw createSAXParseException(ex.getMessage(), ex);
                }
            }
            else
            {
                try
                {
                    value = AttributeImpl.parseAttributeValueWithoutRef( attribute, trim);
                }
                catch(RaplaException ex)
                {
                    throw createSAXParseException(ex.getMessage(), ex);
                }
            }
            conditions.add(new Object[] {operator,value});
        }

    }
    
    public boolean isDefaultResourceTypes() 
    {
        return defaultResourceTypes;
    }

    public boolean isDefaultEventTypes() 
    {
        return defaultEventTypes;
    }

}

