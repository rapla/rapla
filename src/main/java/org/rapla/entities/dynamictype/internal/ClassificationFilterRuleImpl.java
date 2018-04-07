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
package org.rapla.entities.dynamictype.internal;

import org.rapla.components.util.ParseDateException;
import org.rapla.components.util.SerializableDateTimeFormat;
import org.rapla.entities.Category;
import org.rapla.entities.Entity;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.AttributeType;
import org.rapla.entities.dynamictype.ClassificationFilterRule;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.entities.storage.internal.ReferenceHandler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;

public final class ClassificationFilterRuleImpl extends ReferenceHandler
    implements
        ClassificationFilterRule
        ,java.io.Serializable
{
    // Don't forget to increase the serialVersionUID when you change the fields
    private static final long serialVersionUID = 1;
    
    String[] operators;
    String[] ruleValues;
    String attributeKey;
    String attributeId;
    
    ClassificationFilterRuleImpl()
    {
    	
    }
    
    ClassificationFilterRuleImpl(Attribute attribute, String[] operators,Object[] ruleValues) {
		attributeId = attribute.getId();
		this.attributeKey = attribute.getKey();
		DynamicType type = attribute.getDynamicType();
		if ( type== null)
		{
			throw new IllegalArgumentException("Attribute type cannot be null");
		}
		putEntity("dynamictype",type);
        this.operators = operators;
        this.ruleValues = new String[ruleValues.length];
        Class<? extends Entity> refType = attribute.getRefType();
        for (int i=0;i<ruleValues.length;i++) {
            Object ruleValue = ruleValues[i];
            if (refType != null)
            {
                final String key = String.valueOf(i);
                if (ruleValue instanceof Entity)
			    {
    			    putEntity(key, (Entity) ruleValue);
                    //unresolvedRuleValues[i] = ((Entity)ruleValues[i]).getId();
                }
                else if (ruleValue instanceof ReferenceInfo)
                {
                    putId(key,((ReferenceInfo)ruleValue).getId());
                }
                else if ( ruleValue == null)
                {
                    removeId(key);
                }
                else
                {
                    throw new IllegalStateException("Unsuppoted type for reference " + ruleValue);
                }
            }
            else
            {
            	setValue(i, ruleValue);
            }
        }
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
         
        }
    }

    /** find the attribute of the given type that matches the id */
    private Attribute findAttributeById(DynamicType type,Object id) {
        Attribute[] typeAttributes = type.getAttributes();
        for (int i=0; i<typeAttributes.length; i++) {
            if (typeAttributes[i].getId().equals(id)) {
                return typeAttributes[i];
            }
        }
        return null;
    }

    private Attribute findAttributeByKey(DynamicType type,String key) {
        Attribute[] typeAttributes = type.getAttributes();
        for (int i=0; i<typeAttributes.length; i++) {
            if (typeAttributes[i].getKey().equals(key)) {
                return typeAttributes[i];
            }
        }
        return null;
    }

    public Attribute getAttribute() {
        DynamicType dynamicType = getDynamicType();
        Attribute attribute;
        if ( attributeId != null)
        {
            attribute = findAttributeById(dynamicType, attributeId);
        }
        if ( attributeKey != null)
        {
            attribute = findAttributeByKey( dynamicType, attributeKey);
        }
        else
        {
            throw new IllegalStateException("neither attribute Key nor Attribute Id is set in filter rule "+ toString());
        }
		return attribute;
    }
    
    public DynamicType getDynamicType() {
        return getEntity("dynamictype", DynamicType.class);
    }
    
    @Override
    protected Class<? extends Entity> getInfoClass(String key) {
        if ( key.equals("dynamictype"))
        {
            return DynamicType.class;
        }
        Attribute attribute = getAttribute();
        if ( key.length() > 0 && Character.isDigit(key.charAt(0)))
        {
            //int index = Integer.parseInt(key);
            AttributeType type = attribute.getType();
            if (type == AttributeType.CATEGORY )
            {
                return Category.class;
            }
            else if ( type == AttributeType.ALLOCATABLE)
            {
                return Allocatable.class;
            }
        }
        return null;
    }

    public String[] getOperators() {
        return this.operators;
    }

    public Object[] getValues() {
        Object[] result = new Object[operators.length];
		Attribute attribute = getAttribute();
    	for (int i=0;i<operators.length;i++) {
			Object value = getValue(attribute,i);
    		result[i] = value;
        }
        return result;
    }
    
	private Object getValue(Attribute attribute, int index) 
	{
		 AttributeType type = attribute.getType();
        final String key = String.valueOf(index);
        if (type == AttributeType.CATEGORY )
		 {
			 return getEntity(key, Category.class);
		 }
		 else if (type == AttributeType.ALLOCATABLE)
		 {
		     return getEntity(key, Allocatable.class);
		 }
		 String stringValue =  ruleValues[index];
		 if ( stringValue == null)
		 {
			 return null;
		 }
		 if (type == AttributeType.STRING)
		 {
			 return stringValue;
		 }
		 else if (type == AttributeType.BOOLEAN)
		 {
			 return Boolean.parseBoolean( stringValue);
		 }
		 else if (type ==  AttributeType.INT )
		 {
			 return Long.parseLong(stringValue);
		 }
		 else if (type == AttributeType.DATE)
		 {
			 try {
				return SerializableDateTimeFormat.INSTANCE.parseTimestamp( stringValue);
			} catch (ParseDateException e) {
				return null;
			}
		 }
		 else
		 {
			 throw new IllegalStateException("Attributetype " + type + " not supported in filter");
		 }

	}

    
	
    private void setValue(int i, Object ruleValue)
    {
    	String newValue;
        final String key = String.valueOf(i);
        if (ruleValue instanceof Entity)
    	{
    		putEntity(key, (Entity)ruleValue);
    		newValue = null;
    	}
        else if (ruleValue instanceof ReferenceInfo)
        {
            putId(key, ((ReferenceInfo) ruleValue).getId());
            newValue = null;
        }
    	else if ( ruleValue instanceof Date)
    	{
    		Date date = (Date) ruleValue;
    		newValue= SerializableDateTimeFormat.INSTANCE.formatTimestamp(date);
    	}
    	else
    	{
    		newValue = ruleValue != null ? ruleValue.toString() : null;
        	removeId(key);
    	}
    	ruleValues[i] = newValue;

    }
    
    boolean matches(Object value) {
        //String[] ruleOperators = getOperators();
		Attribute attribute = getAttribute();
        for (int i=0;i<operators.length;i++) {
        	String operator = operators[i];
            if (matches(attribute,operator,i,value))
                return true;
        }
        return false;
    }
    
    private boolean matches(Attribute attribute,String operator,int index,Object value) {
        AttributeType type = attribute.getType();
        Object ruleValue = getValue(attribute, index);
        if (type == AttributeType.CATEGORY)
        {
            Category category = (Category) ruleValue;
            if (category == null)
            {
                return (value == null);
            }
            if ( operator.equals("=")  ) 
            {
                return value != null && category.isIdentical((Category)value);
            } 
            else if ( operator.equals("is") )
            {
                return value != null && (category.isIdentical((Category)value)
                        || category.isAncestorOf((Category)value));
            }
        }
        else if (type == AttributeType.ALLOCATABLE)
        {
        	Allocatable allocatable = (Allocatable) ruleValue;
            if (allocatable == null)
            {
                return (value == null);
            }
            if ( operator.equals("=")  ) 
            {
                return value != null && allocatable.isIdentical((Allocatable)value);
            } 
            else if ( operator.equals("is") )
            {
                return value != null && (allocatable.isIdentical((Allocatable)value) );
                   //     || category.isAncestorOf((Category)value));
            }
        }
        else if (type == AttributeType.STRING)
        {
            if (ruleValue == null)
            {
                return (value == null);
            }
            if ( operator.equals("is") || operator.equals("=")) 
            {
                return  value != null && value.equals( ruleValue );
            } 
            else if ( operator.equals("contains") )
            {
                String string = ((String)ruleValue).toLowerCase();
                if (string == null)
                    return true;
                string = string.trim();
                if (value == null)
                    return string.length() == 0;
                return (((String) value).toLowerCase().contains(string));
            }
            else if ( operator.equals("starts") )
            {
                String string = ((String)ruleValue).toLowerCase();
                if (string == null)
                    return true;
                string = string.trim();
                if (value == null)
                    return string.length() == 0;
                return (((String)value).toLowerCase().startsWith(string));
            }
        }
        else if (type ==  AttributeType.BOOLEAN)
        {
            Boolean boolean1 = (Boolean)ruleValue;
            Boolean boolean2 = (Boolean)value;
            if (boolean1 == null)
            {
                return (boolean2 == null || boolean2.booleanValue());
            }
            if (boolean2 == null)
            {
                return !boolean1.booleanValue();
            }
            return (boolean1.equals(boolean2));
        }
        else if (type == AttributeType.INT || type ==AttributeType.DATE)
        {
            if(ruleValue == null) {
                if (operator.equals("<>"))
                    return value != null;
                else if (operator.equals("="))
                    return value == null;
                else
                return false;
            }

            if(value == null) 
                return false;
            
            long long1 = type == AttributeType.INT ? ((Long) value).longValue()     : ((Date) value).getTime();
            long long2 = type == AttributeType.INT ? ((Long) ruleValue).longValue() : ((Date) ruleValue).getTime();

            if (operator.equals("<")) 
            {
                return long1 < long2;
            }
            else if (operator.equals("=")) 
            {
                return long1 ==  long2;
            }
            else if (operator.equals(">")) 
            {
                return long1 >  long2;
            }
            else if (operator.equals(">=")) 
            {
                return long1 >=  long2;
            }
            else if (operator.equals("<=")) 
            {
                return long1 >=  long2;
            }
            else if (operator.equals("<>")) 
            {
                return long1 !=  long2;
            }
        }
        
        return false;
    }

   
	public String toString()
    {
    	StringBuilder buf = new StringBuilder();
        buf.append(attributeKey);
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
	
	@Override
	public void replace(ReferenceInfo origId, ReferenceInfo newId)
	{
	    super.replace(origId, newId);
        if (attributeId != null && getAttribute().getRefType() == Allocatable.class)
        {
            final ArrayList<String> arrayList = new ArrayList<>(Arrays.asList(ruleValues));
            final String origIdString = origId.getId();
            if (arrayList.contains(origIdString))
            {
                final int indexOf = arrayList.indexOf(origIdString);
                arrayList.remove(indexOf);
                if (!arrayList.contains(newId.getId()))
                {
                    arrayList.add(indexOf, newId.getId());
                }
                ruleValues = arrayList.toArray(new String[arrayList.size()]);
            }
        }
	}
}

