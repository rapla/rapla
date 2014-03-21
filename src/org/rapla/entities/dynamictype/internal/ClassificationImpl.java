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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;

import org.rapla.entities.EntityNotFoundException;
import org.rapla.entities.ReadOnlyException;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.entities.dynamictype.internal.ParsedText.EvalContext;
import org.rapla.entities.storage.CannotExistWithoutTypeException;
import org.rapla.entities.storage.DynamicTypeDependant;
import org.rapla.entities.storage.EntityReferencer;
import org.rapla.entities.storage.EntityResolver;
import org.rapla.entities.storage.UnresolvableReferenceExcpetion;

/** Use the method <code>newClassification()</code> of class <code>DynamicType</code> to
 *  create a classification. Once created it is not possible to change the
 *  type of a classifiction. But you can replace the classification of an
 *  object implementing <code>Classifiable</code> with a new one.
 *  @see DynamicType
 *  @see org.rapla.entities.dynamictype.Classifiable
 */
public class ClassificationImpl implements Classification,DynamicTypeDependant, EntityReferencer {

	private String parentId;
	private String type;
	private Map<String,List<String>> map = new LinkedHashMap<String,List<String>>();
	private transient boolean readOnly = false;

	private transient TextCache name;
	private transient TextCache namePlaning;
	private transient EntityResolver resolver;
    
    /** stores the nonreference values like integers,boolean and string.*/
    //HashMap<String,Object> attributeValueMap = new HashMap<String,Object>(1);
    /** stores the references to the dynamictype and the reference values */
    //transient ReferenceHandler referenceHandler = new ReferenceHandler(data);

    class TextCache
    {
        String nameString;
        ParsedText lastParsedAnnotation;
        public String getName(Locale locale, String keyNameFormat) {
    		DynamicTypeImpl type = (DynamicTypeImpl)getType();
    		ParsedText parsedAnnotation = type.getParsedAnnotation( keyNameFormat );
            if ( parsedAnnotation == null) {
                return type.toString();
            }

            if (nameString != null)
            {
                if (parsedAnnotation.equals(lastParsedAnnotation))
                    return nameString;
            }
            lastParsedAnnotation =  parsedAnnotation;
            EvalContext evalContext = new EvalContext(locale)
            {
            	public Classification getClassification()
            	{
            		return ClassificationImpl.this;
            	}
            };
    		nameString = parsedAnnotation.formatName(evalContext).trim();
            return nameString;
    	}
    }
    
    public ClassificationImpl()
    {
    	
    }

    ClassificationImpl(DynamicTypeImpl dynamicType) {
        parentId = dynamicType.getId();
        type = dynamicType.getElementKey();
    }

    public void setResolver( EntityResolver resolver)
    {
        this.resolver = resolver;
    }

    public void setReadOnly() {
        this.readOnly = true;
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    public void checkWritable() {
        if ( readOnly )
            throw new ReadOnlyException( this );
    }
    /*
    public ReferenceHandler getReferenceHandler() {
		return referenceHandler;
	}
	*/

    public boolean isRefering(String id) {
        String parentId = getParentId();
		return id.equals(parentId) || map.containsKey( id );
    }

    public Iterable<String> getReferencedIds() {
    	List<String> result = new ArrayList<String>();
    	String parentId = getParentId();
		result.add( parentId );
    	DynamicTypeImpl type = getType();
    	for ( Map.Entry<String,List<String>> entry:map.entrySet())
    	{
    		String key = entry.getKey();
    		Attribute attribute = type.getAttribute(key);
    		if ( attribute == null || attribute.getRefType() == null)
    		{
    			continue;
    		}
    		List<String> values = entry.getValue();
    		if  (values != null ) 
    		{
    			result.addAll(values );
    		}
    	}
    	return result;
    }

	private String getParentId() {
		if  (parentId != null)
			return parentId;
		if (type == null)
		{
			throw new UnresolvableReferenceExcpetion( "type and parentId are both not set");
		}
		DynamicType dynamicType = resolver.getDynamicType( type);
		if ( dynamicType == null)
		{
			throw new UnresolvableReferenceExcpetion( type);
		}
		parentId = dynamicType.getId();
		return parentId;
			
	}

    public DynamicTypeImpl getType() {
    	if ( resolver == null)
    	{
    		throw new IllegalStateException("Resolver not set on "+ toString());
    	}
        String parentId = getParentId();
		DynamicTypeImpl type = (DynamicTypeImpl) resolver.tryResolve( parentId);
        if ( type == null)
        {
        	throw new UnresolvableReferenceExcpetion(parentId, toString());
        }
    	return type;
    }

    public String getName(Locale locale) {
    	// display name = Title of event
    	if ( name == null)
    	{
    		name = new TextCache();
    	}
        return name.getName(locale,  DynamicTypeAnnotations.KEY_NAME_FORMAT);
    }

    public String getNamePlaning(Locale locale) {
    	// display name = Title of event
    	if ( namePlaning == null)
    	{
    		namePlaning = new TextCache();
    	}
        return namePlaning.getName(locale,  DynamicTypeAnnotations.KEY_NAME_FORMAT_PLANING);
    }
	

    public String getValueAsString(Attribute attribute,Locale locale)
    {
    	Object value =  getValue(attribute);
        StringBuilder buf = new StringBuilder();
//        for ( Object value: values)
//        {
//        	buf.append( getValueAsString(attribute, locale, value));
//        }
        buf.append( ((AttributeImpl)attribute).getValueAsString( locale, value) );
        String result = buf.toString();
        return result;
    }

    public Attribute getAttribute(String key) {
        return getType().getAttribute(key);
    }

    public Attribute[] getAttributes() {
        return getType().getAttributes();
    }

    public boolean needsChange(DynamicType newType) {
    	if ( !hasType (newType )) {
            return false;
    	}
        DynamicTypeImpl type = getType();
		if ( !newType.getElementKey().equals( type.getElementKey()))
        	return true;
		
        for (String key:map.keySet()) {
        	Attribute attribute = getType().getAttribute(key);
            String attributeId = attribute.getId();
			if (type.hasAttributeChanged( (DynamicTypeImpl)newType , attributeId))
            	return true;
        }
        return false;
    }

    boolean hasType(DynamicType type) {
        return getType().equals( type);
    }

    public void commitChange(DynamicType type) {
    	if ( !hasType (type )) {
            return;
        }
        
        Collection<String> removedKeys = new ArrayList<String>();
        Map<Attribute,Attribute> attributeMapping = new HashMap<Attribute,Attribute>();
        for  (String key:map.keySet()) {
        	Attribute attribute = getType().getAttribute(key);
			if ( attribute == null)
			{
        		continue;
			}
			// key now longer availabe so remove it
			if ( type.getAttribute(key) == null)
			{
				removedKeys.add( key );
			}
			
			String attId = attribute.getId();
			Attribute newAtt = findAttributeById(type, attId);
			if ( newAtt != null)
			{
				attributeMapping.put(attribute, newAtt);
			}
        }
        for (Attribute attribute: attributeMapping.keySet()) 
        {
			Collection<Object> convertedValues = new ArrayList<Object>();
			Collection<?> valueCollection = getValues( attribute);
            Attribute newAttribute = attributeMapping.get( attribute);
			for (Object oldValue: valueCollection)
			{
    			Object newValue = newAttribute.convertValue(oldValue);
    			if ( newValue != null)
    			{
    				convertedValues.add( newValue);
    			}
			}
			setValues(newAttribute, convertedValues);
        }
       
        for (String key:removedKeys)
        {
        	map.remove( key );
        }
        this.type = type.getElementKey();
        name = null;
        namePlaning = null;
    }

    /** find the attribute of the given type that matches the id */
    private Attribute findAttributeById(DynamicType type,String id) {
        Attribute[] typeAttributes = type.getAttributes();
        for (int i=0; i<typeAttributes.length; i++) {
            String key2 = typeAttributes[i].getId();
			if (key2.equals(id)) {
                return typeAttributes[i];
            }
        }
        return null;
    }


    public void setValue(String key,Object value) {
    	Attribute attribute = getAttribute( key );
    	if ( attribute == null ) {
    		throw new NoSuchElementException("No attribute found for key " + key);
    	}

    	setValue( attribute,value);
    }

    public Object getValue(String key) {
    	Attribute attribute = getAttribute( key );
    	if ( attribute == null ) {
    		throw new NoSuchElementException("No attribute found for key " + key);
    	}

    	return getValue(getAttribute(key));
    }

    public void setValue(Attribute attribute,Object value) {
    	checkWritable();
    	if ( value != null && !(value instanceof Collection<?>))
    	{
    		value = Collections.singleton( value);
    	}
    	setValues(attribute, (Collection<?>) value);
    }

    
    public <T> void setValues(Attribute attribute,Collection<T> values) {
        checkWritable();
        String attributeKey = attribute.getKey();
		if ( values == null || values.isEmpty())
        {
			map.remove(attributeKey);
        	return;
        }
		ArrayList<String> newValues = new ArrayList<String>();
		for (Object value:values)
		{
			String stringValue = ((AttributeImpl)attribute).toStringValue(value);
			if ( stringValue != null)
			{
				newValues.add(stringValue);
			}
        }
		map.put(attributeKey,newValues);
        //isNameUpToDate = false;
        name = null;
        namePlaning = null;
    }

    public <T> void addValue(Attribute attribute,T value) {
    	checkWritable();
    	String attributeKey = attribute.getKey();
    	String stringValue = ((AttributeImpl)attribute).toStringValue( value);
        if ( stringValue == null)
        {
        	return;
        }
    	List<String> l = map.get(attributeKey);
    	if ( l == null) 
    	{
    		l = new ArrayList<String>();
    		map.put(attributeKey, l);
    	}
    	l.add(stringValue);
    }
    
    public Collection<Object> getValues(Attribute attribute) {
    	if ( attribute == null ) {
    		throw new NullPointerException("Attribute can't be null");
    	}
    	String attributeKey = attribute.getKey();
    	// first lookup in attribute map
        List<String> list = map.get(attributeKey);
        if ( list == null || list.size() == 0)
        {
        	return Collections.emptyList();
        }
        List<Object> result = new ArrayList<Object>();
        for (String value:list)
        {
        	Object obj;
			try {
				obj = ((AttributeImpl)attribute).fromString(resolver,value);
				result.add( obj);
			} catch (EntityNotFoundException e) {
			}
        }
        return result;
    }
    
    public Object getValue(Attribute attribute) {
    	if ( attribute == null ) {
    		throw new NullPointerException("Attribute can't be null");
    	}
    	String attributeKey = attribute.getKey();
        // first lookup in attribute map
        List<String> o = map.get(attributeKey);
        if ( o == null  || o.size() == 0)
        {
        	return null;
        }
        String stringRep = o.get(0);
        Object fromString;
		try {
			fromString = ((AttributeImpl)attribute).fromString(resolver, stringRep);
			return fromString;
		} catch (EntityNotFoundException e) {
			throw new IllegalStateException(e.getMessage());
		}
    }

	public ClassificationImpl clone() {
        ClassificationImpl clone = new ClassificationImpl((DynamicTypeImpl)getType());
        //clone.referenceHandler = (ReferenceHandler) referenceHandler.clone((Map<String, List<String>>) ((HashMap<String, List<String>>)data).clone());
        //clone.attributeValueMap = (HashMap<String,Object>) attributeValueMap.clone();
        for ( Map.Entry<String,List<String>> entry: map.entrySet())
        {
        	String key = entry.getKey();
			List<String> value = new ArrayList<String>(entry.getValue());
			clone.map.put(key, value);
        }
        clone.resolver = resolver;
        clone.parentId = getParentId();
        clone.type = type;
        clone.name = null;
        clone.namePlaning = null;
        clone.readOnly = false;// clones are always writable
        return clone;
    }

     public String toString() {
         return map.toString();
     }

    public void commitRemove(DynamicType type) throws CannotExistWithoutTypeException 
    {
        throw new CannotExistWithoutTypeException();
    }
}