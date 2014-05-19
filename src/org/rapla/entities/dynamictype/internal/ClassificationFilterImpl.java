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

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.rapla.components.util.Assert;
import org.rapla.components.util.iterator.IterableChain;
import org.rapla.components.util.iterator.NestedIterable;
import org.rapla.entities.ReadOnlyException;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.ClassificationFilter;
import org.rapla.entities.dynamictype.ClassificationFilterRule;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.storage.CannotExistWithoutTypeException;
import org.rapla.entities.storage.DynamicTypeDependant;
import org.rapla.entities.storage.EntityReferencer;
import org.rapla.entities.storage.EntityResolver;
import org.rapla.entities.storage.UnresolvableReferenceExcpetion;

public final class ClassificationFilterImpl
    implements
        ClassificationFilter
        ,DynamicTypeDependant
        ,EntityReferencer
        ,java.io.Serializable
{
    // Don't forget to increase the serialVersionUID when you change the fields
    private static final long serialVersionUID = 1;
    private String typeId;
    
    transient boolean readOnly;
    List<ClassificationFilterRuleImpl> list = new LinkedList<ClassificationFilterRuleImpl>();
    transient boolean arrayUpToDate = false;
    transient ClassificationFilterRuleImpl[] rulesArray;
    transient EntityResolver resolver;
    ClassificationFilterImpl() {
	}
    
    ClassificationFilterImpl(DynamicTypeImpl dynamicType) {
        typeId = dynamicType.getId();
    }

    public void setResolver( EntityResolver resolver)  {
        this.resolver = resolver;
        for (Iterator<ClassificationFilterRuleImpl> it=list.iterator();it.hasNext();)
        {
             it.next().setResolver( resolver );
        }
    }

    public DynamicType getType() {
        DynamicType type = resolver.tryResolve(typeId, DynamicType.class);
        if ( type == null)
        {
            throw new UnresolvableReferenceExcpetion(typeId);
        }
        //DynamicType dynamicType = (DynamicType) referenceHandler.getEntity("parent");
		return type;
    }

    @Override
    public Iterable<ReferenceInfo> getReferenceInfo() {
        return new IterableChain<ReferenceInfo>
            (
             Collections.singleton( new ReferenceInfo(typeId, DynamicType.class))
             ,new NestedIterable<ReferenceInfo,ClassificationFilterRuleImpl>( list ) {
                     public Iterable<ReferenceInfo> getNestedIterable(ClassificationFilterRuleImpl obj) {
                         return obj.getReferenceInfo();
                     }
                 }
             );
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
		if ( resolver != null)
		{
			rule.setResolver( resolver);
		}
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
        ClassificationFilterRuleImpl[] rules = getRules();
        for (int i=0;i<rules.length;i++) {
            ClassificationFilterRuleImpl rule = rules[i];
			Attribute attribute = rule.getAttribute();
			if ( attribute != null)
			{
				Collection<Object> values = classification.getValues(attribute);
				if ( values.size() == 0)
				{
			        if (!rule.matches( null))
			        {
			            return false;
			        }
		                    
				}
				else
				{
    				boolean matchesOne= false;
    				for (Object value: values)
    				{
    					if (rule.matches( value))
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

    boolean hasType(DynamicType type) {
        return getType().equals( type);
    }

    public boolean needsChange(DynamicType newType) {
        if (!hasType( newType ))
            return false;
        
        if ( !newType.getKey().equals( getType().getKey()))
        	return true;

        ClassificationFilterRuleImpl[] rules = getRules();
        for (int i=0;i<rules.length;i++) {
            ClassificationFilterRuleImpl rule = rules[i];
            Attribute attribute = rule.getAttribute();
            if ( attribute == null )
            {
            	return true;
            }

            String id = attribute.getId();
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
			Object id = attribute.getId();
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

    public ClassificationFilterImpl clone() {
        ClassificationFilterImpl clone = new ClassificationFilterImpl((DynamicTypeImpl)getType());
        clone.resolver = resolver;
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
    		buf.append(getType().getKey() +": ");
    		for ( ClassificationFilterRule rule: getRules())
    		{
    			buf.append(rule.toString());
    			buf.append(", ");
    		}
    		return buf.toString();
    }
   

    
}

