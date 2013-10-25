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

import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;

import org.rapla.components.util.Assert;
import org.rapla.components.util.iterator.IteratorChain;
import org.rapla.components.util.iterator.NestedIterator;
import org.rapla.entities.Category;
import org.rapla.entities.EntityNotFoundException;
import org.rapla.entities.ReadOnlyException;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.AttributeType;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.ClassificationFilter;
import org.rapla.entities.dynamictype.ClassificationFilterRule;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.storage.CannotExistWithoutTypeException;
import org.rapla.entities.storage.DynamicTypeDependant;
import org.rapla.entities.storage.EntityReferencer;
import org.rapla.entities.storage.EntityResolver;
import org.rapla.entities.storage.RefEntity;
import org.rapla.entities.storage.internal.ReferenceHandler;

public final class ClassificationFilterImpl
    implements
        ClassificationFilter
        ,DynamicTypeDependant
        ,EntityReferencer
        ,java.io.Serializable
{
    // Don't forget to increase the serialVersionUID when you change the fields
    private static final long serialVersionUID = 1;
    
    boolean readOnly;

    LinkedList<ClassificationFilterRuleImpl> list = new LinkedList<ClassificationFilterRuleImpl>();
    transient boolean arrayUpToDate = false;
    transient ClassificationFilterRuleImpl[] rulesArray;
    ReferenceHandler referenceHandler = new ReferenceHandler();

    ClassificationFilterImpl(DynamicTypeImpl dynamicType) {
        referenceHandler.put("parent",dynamicType);
    }

    public void resolveEntities( EntityResolver resolver) throws EntityNotFoundException {
        referenceHandler.resolveEntities( resolver );
        for (Iterator<ClassificationFilterRuleImpl> it=list.iterator();it.hasNext();)
        {
             it.next().resolveEntities( resolver );
        }
    }

    public DynamicType getType() {
        DynamicType dynamicType = (DynamicType) referenceHandler.get("parent");
		return dynamicType;
    }

    public boolean isRefering(RefEntity<?> object) {
        if (referenceHandler.isRefering(object))
            return true;
        ClassificationFilterRuleImpl[] rules = getRules();
        for (int i=0;i<rules.length;i++)
            if (rules[i].isRefering(object))
                return true;
        return false;
    }

    public Iterable<RefEntity<?>> getReferences() {
    	NestedIterator<RefEntity<?>> ruleIterator = new NestedIterator<RefEntity<?>>(list) {
                public Iterable<RefEntity<?>> getNestedIterator(Object obj) {
                    return ((ClassificationFilterRuleImpl) obj).getReferences();
                }
            };
        return new IteratorChain<RefEntity<?>>(referenceHandler.getReferences(), ruleIterator);
    }


    public void setReadOnly(boolean enable) {
        this.readOnly = enable;
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    public void checkWritable() {
        if ( readOnly )
            throw new ReadOnlyException( this );
    }
    
    public void addRule(String attributeName, Object[][] conditions) {
        setRule(ruleSize(), attributeName, conditions);
    }
    
    public void setRule(int index, String attributeName,Object[][] conditions) {
        setRule( index, getType().getAttribute( attributeName), conditions);
    }
    
    public void setRule(int index, Attribute attribute,Object[][] conditions) {
        checkWritable();
        Assert.notNull( attribute );
        String[] operators = new String[conditions.length];
        Object[] ruleValues = new Object[conditions.length];
        for (int i=0;i<conditions.length;i++) {
            operators[i] = conditions[i][0].toString().trim();
            checkOperator(operators[i]);
            ruleValues[i] = conditions[i][1];
        }
        ClassificationFilterRuleImpl rule = new ClassificationFilterRuleImpl( attribute, operators, ruleValues);

        //      System.out.println("Rule " + index + " for '" + dynamicType + "' added. " + " Attribute " + rule.attribute  + " Params: " + rule.params[0]);
        if (index < list.size() )
            list.set(index, rule);
        else
            list.add(index, rule);
        arrayUpToDate = false;
    }

    
    private void checkOperator(String operator) {
        if (operator.equals("<")) 
            return;
        if (operator.equals(">"))
            return;
        if (operator.equals("="))
            return;
       
        if (operator.equals("contains"))
            return;
        if (operator.equals("starts"))
            return;
        if (operator.equals("is"))
            return;
        if (operator.equals("<=")) 
            return;
        if (operator.equals(">="))
            return;
        if (operator.equals("<>"))
            return;
        throw new IllegalArgumentException("operator '" + operator + "' not supported!");
    }

    public void addEqualsRule( String attributeName, Object object )
    {
        addRule( attributeName, new Object[][] {{"=",object}});
    }
    
    public void addIsRule( String attributeName, Object object )
    {
        addRule( attributeName, new Object[][] {{"is",object}});
    }


    public int ruleSize() {
        return list.size();
    }

    public Iterator<? extends ClassificationFilterRule> ruleIterator() {
        return list.iterator();
    }

    public void removeAllRules() {
        checkWritable();
        list.clear();
        arrayUpToDate = false;
    }

    public void removeRule(int index) {
        checkWritable();
        list.remove(index);
        arrayUpToDate = false;
        //System.out.println("Rule " + index + " for '" + dynamicType + "' removed.");
    }

    private ClassificationFilterRuleImpl[] getRules() {
        if (!arrayUpToDate)
            rulesArray = list.toArray(new ClassificationFilterRuleImpl[0]);
        arrayUpToDate = true;
        return rulesArray;
    }

    public boolean matches(Classification classification) {
        if (!getType().equals(classification.getType()))
            return false;
        ClassificationFilterRule[] rules = getRules();
        for (int i=0;i<rules.length;i++) {
            ClassificationFilterRule rule = rules[i];
			Attribute attribute = rule.getAttribute();
			if ( attribute != null)
			{
				Collection<Object> values = classification.getValues(attribute);
				if ( values.size() == 0)
				{
			        if (!matches(rule, null))
			        {
			            return false;
			        }
		                    
				}
				else
				{
    				boolean matchesOne= false;
    				for (Object value: values)
    				{
    					if (matches(rule, value))
    						matchesOne = true;
    				}
    				if ( !matchesOne )
    				{
    					return false;
    				}
				}
			}
        }
        return true;
    }

    boolean matches(ClassificationFilterRule rule,Object value) {
        Object[] ruleValues = rule.getValues();
        String[] ruleOperators = rule.getOperators();
        for (int i=0;i<ruleValues.length;i++) {
            if (matches(rule.getAttribute(),ruleOperators[i],ruleValues[i],value))
                return true;
        }
        return false;
    }

    private boolean matches(Attribute attribute,String operator,Object ruleValue,Object value) {
        AttributeType type = attribute.getType();
        if (type.equals(AttributeType.CATEGORY))
        {
            Category category = (Category)ruleValue;
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
        else if (type.equals(AttributeType.ALLOCATABLE))
        {
        	Allocatable allocatable = (Allocatable)ruleValue;
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
        else if (type.equals( AttributeType.STRING))
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
                return (((String)value).toLowerCase().indexOf(string)>=0);
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
        else if (type.equals( AttributeType.BOOLEAN))
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
        else if (type.equals( AttributeType.INT) || type.equals(AttributeType.DATE))
        {
            if(ruleValue == null) {
                if (operator.equals("<>")) 
                    if(value == null) 
                        return false;
                    else
                        return true;
                else if (operator.equals("=")) 
                    if(value == null) 
                        return true;
                    else
                        return false;
                else
                return false;
            }

            if(value == null) 
                return false;
            
            long long1 = type.equals( AttributeType.INT) ? ((Long) value).longValue()     : ((Date) value).getTime();
            long long2 = type.equals( AttributeType.INT) ? ((Long) ruleValue).longValue() : ((Date) ruleValue).getTime();

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

    boolean hasType(DynamicType type) {
        return getType().equals( type);
    }

    public boolean needsChange(DynamicType newType) {
        if (!hasType( newType ))
            return false;
        
        if ( !newType.getElementKey().equals( getType().getElementKey()))
        	return true;

        ClassificationFilterRuleImpl[] rules = getRules();
        for (int i=0;i<rules.length;i++) {
            ClassificationFilterRuleImpl rule = rules[i];
            Attribute attribute = rule.getAttribute();
            if ( attribute == null )
            {
            	return true;
            }

            Object id = ((RefEntity<?>)attribute).getId();
            if (((DynamicTypeImpl)getType()).hasAttributeChanged( (DynamicTypeImpl)newType , id))
            	return true;
            Attribute newAttribute = newType.getAttribute(attribute.getKey());
            if ( newAttribute == null)
            {
            	return true;
            }
            if (rule.needsChange(newAttribute))
            {
            	return true;            	
            }
        }
        return false;
    }

    public void commitChange(DynamicType type) {
        if (!hasType(type))
            return;
        Iterator<ClassificationFilterRuleImpl> it = list.iterator();
        while (it.hasNext()) {
            ClassificationFilterRuleImpl rule = it.next();
            Attribute attribute = rule.getAttribute();
            if ( attribute == null )
            {
            	it.remove();
            	continue;
            }
			Object id = ((RefEntity<?>)attribute).getId();
            Attribute typeAttribute = ((DynamicTypeImpl)type).findAttributeForId(id );
            if (typeAttribute == null) {
                it.remove();
            } else {
                rule.commitChange(typeAttribute);
            }
        }
        arrayUpToDate = false;
    }

    public void commitRemove(DynamicType type) throws CannotExistWithoutTypeException 
    {
        throw new CannotExistWithoutTypeException();
    }

    public ClassificationFilter clone() {
        ClassificationFilterImpl clone = new ClassificationFilterImpl((DynamicTypeImpl)getType());
        clone.referenceHandler = (ReferenceHandler) referenceHandler.clone();
        clone.list = new LinkedList<ClassificationFilterRuleImpl>();
        Iterator<ClassificationFilterRuleImpl> it = list.iterator();
        while (it.hasNext()) {
            ClassificationFilterRuleImpl rule = it.next();
            Attribute attribute = rule.getAttribute();
            if ( attribute != null)
            {
	            ClassificationFilterRuleImpl clone2 =new ClassificationFilterRuleImpl(attribute,rule.getOperators(),rule.getValues());
	            clone.list.add(clone2);
            }
        }
        clone.readOnly = false;// clones are always writable
        clone.arrayUpToDate = false;
        return clone;
    }

    public ClassificationFilter[] toArray()
    {
        return new ClassificationFilter[] {this};
    }

    
    public String toString()
    {
    		StringBuilder buf = new StringBuilder();
    		buf.append(getType().getElementKey() +": ");
    		for ( ClassificationFilterRule rule: getRules())
    		{
    			buf.append(rule.toString());
    			buf.append(", ");
    		}
    		return buf.toString();
    }
   

    
}

