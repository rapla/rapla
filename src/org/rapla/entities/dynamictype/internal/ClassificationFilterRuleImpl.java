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
package org.rapla.entities.dynamictype.internal;

import org.rapla.entities.Entity;
import org.rapla.entities.EntityNotFoundException;
import org.rapla.entities.RaplaType;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.ClassificationFilterRule;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.storage.EntityReferencer;
import org.rapla.entities.storage.EntityResolver;
import org.rapla.entities.storage.internal.ReferenceHandler;

public final class ClassificationFilterRuleImpl
    implements
        ClassificationFilterRule
        ,EntityReferencer
        ,java.io.Serializable
{
    // Don't forget to increase the serialVersionUID when you change the fields
    private static final long serialVersionUID = 1;
    
    String[] operators;
    Object[] unresolvedRuleValues;
    boolean[] valueNeedsResolving;
    transient Object[] ruleValues;
    Object attributeId;
    ReferenceHandler referenceHandler = new ReferenceHandler();

    @SuppressWarnings("unchecked")
	ClassificationFilterRuleImpl(Attribute attribute, String[] operators,Object[] ruleValues) {
		attributeId = attribute.getId();
		DynamicType type = attribute.getDynamicType();
		if ( type== null)
		{
			throw new IllegalArgumentException("Attribute type cannot be null");
		}
		referenceHandler.putEntity("dynamictype",type);
        this.operators = operators;
        this.ruleValues = ruleValues;
        unresolvedRuleValues = new Object[ruleValues.length];
        valueNeedsResolving = new boolean[ruleValues.length];
        RaplaType refType = attribute.getRefType();
        for (int i=0;i<ruleValues.length;i++) {
            Object ruleValue = ruleValues[i];
			if (ruleValue instanceof Entity)
            {
                referenceHandler.putEntity(String.valueOf(i),(Entity)ruleValue);
                //unresolvedRuleValues[i] = ((Entity)ruleValues[i]).getId();
                valueNeedsResolving[i] = true;
            }
            else if (refType != null && refType.isId(ruleValue))
            {
                referenceHandler.putId(String.valueOf(i),(String)ruleValue);
                //unresolvedRuleValues[i] = ((Entity)ruleValues[i]).getId();
                valueNeedsResolving[i] = true;
            }
            else
            {
                unresolvedRuleValues[i] = ruleValue;
                valueNeedsResolving[i] = false;
            }
        }
	}


    public void setResolver( EntityResolver resolver)  {
        referenceHandler.setResolver( resolver );
    }

    public boolean isRefering(String object) {
        return referenceHandler.isRefering(object);
    }

    public Iterable<String> getReferencedIds() {
        return referenceHandler.getReferencedIds();
    }

    public boolean needsChange(Attribute typeAttribute) {
        Object[] ruleValues = getValues();
        for (int i=0;i<ruleValues.length;i++)
            if (typeAttribute.needsChange(ruleValues[i]))
                return true;
        return false;
    }

    public void commitChange(Attribute typeAttribute) {
        Object[] ruleValues = getValues();
        for (int i=0;i<ruleValues.length;i++) {
            Object oldValue = ruleValues[i];
            Object newValue = typeAttribute.convertValue(oldValue);
            setValue(i, newValue);
            ruleValues[i] = newValue;
        }
    }

    
    /** find the attribute of the given type that matches the id */
    private Attribute findAttribute(DynamicType type,Object id) {
        Attribute[] typeAttributes = type.getAttributes();
        for (int i=0; i<typeAttributes.length; i++) {
            if (((Entity)typeAttributes[i]).getId().equals(id)) {
                return typeAttributes[i];
            }
        }
        return null;
    }
    public Attribute getAttribute() {
        DynamicType dynamicType = getDynamicType();
		return findAttribute(dynamicType, attributeId);
    }
    
    public DynamicType getDynamicType() {
        return (DynamicType)referenceHandler.getEntity("dynamictype");
    }

    public String[] getOperators() {
        return this.operators;
    }

    public Object[] getValues() {
        if (ruleValues == null)
            ruleValues = new Object[operators.length];
        for (int i=0;i<unresolvedRuleValues.length;i++) {
            if (valueNeedsResolving[i])
            {
                ruleValues[i] = referenceHandler.getEntity(String.valueOf(i));
            }
            else
            {
                ruleValues[i] = unresolvedRuleValues[i];
            }
        }
        return ruleValues;
    }
    
    private void setValue(int i, Object value)
    {
    	 if (value instanceof Entity)
         {
    		 valueNeedsResolving[i] = true;
    		 referenceHandler.putEntity(String.valueOf(i), (Entity)value);
         }
         else
         {
        	 valueNeedsResolving[i] = false;
             unresolvedRuleValues[i] = value;
         }
    }

   
    public String toString()
    {
    	StringBuilder buf = new StringBuilder();
    	buf.append(getAttribute().getKey());
    	Object[] values = getValues();
    	String[] operators = getOperators();
    	for ( int i=0;i<values.length;i++)
    	{
    		String operator = i<operators.length ? operators[i] : "=";
    		buf.append(" " + operator + " ");
    		buf.append(values[i]);
    		buf.append(", ");
    	}
    	return buf.toString();
    }
}

