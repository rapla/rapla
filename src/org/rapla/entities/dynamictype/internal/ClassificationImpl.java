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
import java.util.Iterator;
import java.util.Locale;
import java.util.NoSuchElementException;

import org.rapla.entities.EntityNotFoundException;
import org.rapla.entities.ReadOnlyException;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.AttributeAnnotations;
import org.rapla.entities.dynamictype.AttributeType;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.entities.dynamictype.internal.ParsedText.EvalContext;
import org.rapla.entities.storage.CannotExistWithoutTypeException;
import org.rapla.entities.storage.DynamicTypeDependant;
import org.rapla.entities.storage.EntityReferencer;
import org.rapla.entities.storage.EntityResolver;
import org.rapla.entities.storage.RefEntity;
import org.rapla.entities.storage.internal.ReferenceHandler;
import org.rapla.entities.storage.internal.SimpleIdentifier;

/** Use the method <code>newClassification()</code> of class <code>DynamicType</code> to
 *  create a classification. Once created it is not possible to change the
 *  type of a classifiction. But you can replace the classification of an
 *  object implementing <code>Classifiable</code> with a new one.
 *  @see DynamicType
 *  @see org.rapla.entities.dynamictype.Classifiable
 */
public class ClassificationImpl implements Classification,DynamicTypeDependant, EntityReferencer {

    boolean readOnly = false;

    transient String nameString;
    transient ParsedText lastParsedAnnotation;

    /** stores the nonreference values like integers,boolean and string.*/
    HashMap<Comparable,Object> attributeValueMap = new HashMap<Comparable,Object>(1);

    /** stores the references to the dynamictype and the reference values */
    ReferenceHandler referenceHandler = new ReferenceHandler();


    ClassificationImpl(DynamicTypeImpl dynamicType) {
        referenceHandler.put("parent",dynamicType);
    }

    public void resolveEntities( EntityResolver resolver) throws EntityNotFoundException {
        referenceHandler.resolveEntities( resolver);
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
    
    public ReferenceHandler getReferenceHandler() {
		return referenceHandler;
	}

    public boolean isRefering(RefEntity<?> obj) {
        return referenceHandler.isRefering(obj);
    }

    public Iterable<RefEntity<?>> getReferences() {
        return referenceHandler.getReferences();
    }

    public DynamicType getType() {
        return (DynamicType) referenceHandler.get("parent");
    }

    public String getName(Locale locale) {
    	// display name = Title of event
        DynamicTypeImpl type = (DynamicTypeImpl)getType();
        ParsedText parsedAnnotation = type.getParsedAnnotation( DynamicTypeAnnotations.KEY_NAME_FORMAT );
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
        if ( !newType.getElementKey().equals( getType().getElementKey()))
        	return true;
        for (String referenceKey :referenceHandler.getReferenceKeys()) {
            RefEntity<?> attribute = ((RefEntity<?>)findAttributeByReferenceKey( getType(), referenceKey));
            if (attribute == null)
            	continue;
            
            if (((DynamicTypeImpl)getType()).hasAttributeChanged( (DynamicTypeImpl)newType , attribute.getId()))
            	return true;
        }
        for (Object id:attributeValueMap.keySet()) {
            if (((DynamicTypeImpl)getType()).hasAttributeChanged( (DynamicTypeImpl)newType , id))
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
        // update referenced values
        referenceHandler.put("parent", (RefEntity<?>) type);
        Collection<Attribute> attributes = new ArrayList<Attribute>();
        Collection<String> removedKeys = new ArrayList<String>();
        Collection<SimpleIdentifier> removedIds = new ArrayList<SimpleIdentifier>();
        for (String referenceKey : referenceHandler.getReferenceKeys())
        {
        	if ( referenceKey.equals ("parent") )
        		continue;
        	Attribute attribute = findAttributeByReferenceKey(type, referenceKey ) ;
        	if (attribute != null )
        		attributes.add( attribute);
        	else
        		removedKeys.add( referenceKey );
        }
       for  (Object id:attributeValueMap.keySet()) {
            Attribute attribute = findAttributeById(type, (SimpleIdentifier)id );
            if (attribute != null) {
            	attributes.add ( attribute );
            } else {
            	removedIds.add( (SimpleIdentifier)id );
            }
        }

        for (Attribute attribute: attributes) 
        {
			Collection<Object> convertedValues = new ArrayList<Object>();
			Collection<?> valueCollection = getValues( attribute);
            for (Object oldValue: valueCollection)
			{
    			Object newValue = attribute.convertValue(oldValue);
    			if ( newValue != null)
    			{
    				convertedValues.add( newValue);
    			}
			}
			setValues(attribute, convertedValues);
        }

        for (String key: removedKeys) {
        	referenceHandler.removeWithKey (key );
        }
        for (SimpleIdentifier id: removedIds) {
        	attributeValueMap.remove ( id );
        }
        nameString = null;
    }

    /** find the attribute of the given type that matches the id */
    private Attribute findAttributeById(DynamicType type,SimpleIdentifier id) {
        Attribute[] typeAttributes = type.getAttributes();
        for (int i=0; i<typeAttributes.length; i++) {
            if (((RefEntity<?>)typeAttributes[i]).getId().equals(id)) {
                return typeAttributes[i];
            }
        }
        return null;
    }

    /** find the attribute of the given type that matches the id */
    private static Attribute findAttributeByReferenceKey(DynamicType type,String key) {
        Attribute[] typeAttributes = type.getAttributes();
        for (int i=0; i<typeAttributes.length; i++) {
        	SimpleIdentifier id = (SimpleIdentifier)((RefEntity<?>)typeAttributes[i]).getId();
			if ((id.getKey() + "").equals(key)) {
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
    	if ( value instanceof Collection<?>)
    	{
    		setValues(attribute, (Collection<?>) value);
    		return;
    	}
    	checkWritable();
        
    	if (attribute.getType().equals(AttributeType.STRING)
            && value != null
            && value.toString().length() == 0)
    	{
            value = null;
    	}
        SimpleIdentifier attributeId = (SimpleIdentifier)((RefEntity<?>)attribute).getId();
        String attributeIdString = ""+attributeId.getKey();
    	referenceHandler.removeWithKey(attributeIdString);
		if (attribute.getType().equals(AttributeType.CATEGORY) || attribute.getType().equals( AttributeType.ALLOCATABLE)) {
			if ( value == null)
			{
				referenceHandler.removeWithKey( attributeIdString );
			}
			else if ( value instanceof RefEntity<?>)
            {
            	referenceHandler.put(attributeIdString, (RefEntity<?>)value);
            }
            else if ( !referenceHandler.isContextualizeCalled() &&  value instanceof SimpleIdentifier)
            {
            	referenceHandler.putId(attributeIdString, (SimpleIdentifier)value);
            }
            // we need to remove it from the other the other map
            // Important! The map is for attributes objects not for their string representations
            attributeValueMap.remove(attributeId);
        } else {
        	attributeValueMap.put(attributeId,value);
        }
		//isNameUpToDate = false;
		nameString = null;
    }

    
    public <T> void setValues(Attribute attribute,Collection<T> values) {
        checkWritable();
        SimpleIdentifier attributeId = (SimpleIdentifier)((RefEntity<?>)attribute).getId();
        String attributeIdString = ""+attributeId.getKey();
		if ( values.isEmpty())
        {
        	attributeValueMap.remove(attributeId);
        	referenceHandler.removeWithKey(attributeIdString);
        	return;
        }
        if ( values.size() == 1)
        {
        	Object value = values.iterator().next();
			setValue( attribute, value);
			return;
        }
    	referenceHandler.removeWithKey(attributeIdString);
        if (attribute.getType().equals(AttributeType.CATEGORY) || attribute.getType().equals( AttributeType.ALLOCATABLE)) 
        {
        	Object first = values.iterator().next();
        	if ( first instanceof RefEntity<?>)
        	{
	        	Collection<RefEntity<?>> castedArray = new ArrayList<RefEntity<?>>();
	        	for ( Object value:values)
	        	{
	        		castedArray.add( (RefEntity<?>) value);
	        	}
	        	referenceHandler.putList(attributeIdString, castedArray);
        	}
        	if ( !referenceHandler.isContextualizeCalled() && first instanceof SimpleIdentifier)
        	{
	        	Collection<Comparable> castedArray = new ArrayList<Comparable>();
	        	for ( Object value:values)
	        	{
	        		castedArray.add( (SimpleIdentifier) value);
	        	}
	        	referenceHandler.putIds(attributeIdString, castedArray);
        	}
        	// we need to remove it from the other the other map
            // Important! The map is for attributes objects not for their string representations
            attributeValueMap.remove(attributeId);
        } else {
        	attributeValueMap.put(attributeId,new ArrayList<Object>(values));
        }
        //isNameUpToDate = false;
        nameString = null;
    }
    
    @SuppressWarnings("unchecked")
	public <T> void addValue(Attribute attribute,T value) {
    	checkWritable();
    	SimpleIdentifier attributeId = (SimpleIdentifier)((RefEntity<?>)attribute).getId();
        String attributeIdString = ""+attributeId.getKey();
    	String multiSelect = attribute.getAnnotation(AttributeAnnotations.KEY_MULTI_SELECT);
    	if ( multiSelect != null && Boolean.valueOf(multiSelect))
		{
    		if (attribute.getType().equals(AttributeType.CATEGORY) || attribute.getType().equals( AttributeType.ALLOCATABLE)) 
    		{
    			if ( value instanceof RefEntity<?>)
	        	{
		        	Collection<RefEntity<?>> list = referenceHandler.getList( attributeIdString);
					Collection<RefEntity<?>> castedArray = new ArrayList<RefEntity<?>>(list);
		        	castedArray.add( (RefEntity<?>) value);
		        	referenceHandler.putList(attributeIdString, castedArray);
	        	}
    			if ( !referenceHandler.isContextualizeCalled() && value instanceof SimpleIdentifier)  
        		{
    			   	Collection<Comparable> castedArray = new ArrayList<Comparable>(referenceHandler.getIds( attributeIdString));
		        	castedArray.add( (SimpleIdentifier) value);
		        	referenceHandler.putIds(attributeIdString, castedArray);
        		}
    		}
    		else
    		{
    			Object existing = attributeValueMap.get( attributeId );
    			if ( existing == null)
    			{
    				setValue(attribute, value);
    				return;
    			}
    			Collection collection;
    			if ( existing instanceof Collection)
    			{
    				collection =  (Collection) existing; 
    			}
    			else
    			{
    				collection = new ArrayList();
    				collection.add( existing);
    			}
				attributeValueMap.put(attributeId,existing);
    		}
		}
		else
		{
			setValue(attribute, value);
		}

    }
    
    public Collection<Object> getValues(Attribute attribute) {
    	if ( attribute == null ) {
    		throw new NullPointerException("Attribute can't be null");
    	}
    	SimpleIdentifier attributeId = (SimpleIdentifier)((RefEntity<?>)attribute).getId();
        String attributeIdString = ""+attributeId.getKey();

        // first lookup in attribute map
        Object o = attributeValueMap.get(attributeId);

        // not found, then lookup in the reference map
        if ( o == null)
        	o = referenceHandler.getList(attributeIdString);

        if (o != null)
        {
        	if ( o instanceof Collection)
        	{
        		Collection<Object> unmodifiableCollection = Collections.unmodifiableCollection((Collection<?>)o);
                return unmodifiableCollection;
        	}
        	else
        	{
        		return Collections.singletonList( o);
        	}
        }
        return Collections.emptyList();
    }
    
    public Object getValue(Attribute attribute) {
    	if ( attribute == null ) {
    		throw new NullPointerException("Attribute can't be null");
    	}
    	SimpleIdentifier attributeId = (SimpleIdentifier)((RefEntity<?>)attribute).getId();
        String attributeIdString = ""+attributeId.getKey();
        // first lookup in attribute map
        Object o = attributeValueMap.get(attributeId);

        // not found, then lookup in the reference map
        if ( o == null)
        	o = referenceHandler.get(attributeIdString);

        if (o != null)
        {
        	if ( o instanceof Collection)
        	{
        		Iterator<?> it  = ((Collection<?>) o).iterator();
        		if  ( it.hasNext() )
        		{
        			return it.next();
        		}
        		else
        		{
        			return null;
        		}
        	}
        	else
        	{
        		return o;
        	}
        }
        
        if ( attribute.getType().is(AttributeType.BOOLEAN))
        {
            return Boolean.FALSE;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
	public Object clone() {
        ClassificationImpl clone = new ClassificationImpl((DynamicTypeImpl)getType());
        clone.referenceHandler = (ReferenceHandler) referenceHandler.clone();
        clone.attributeValueMap = (HashMap<Comparable,Object>) attributeValueMap.clone();
        clone.nameString = null;
        clone.readOnly = false;// clones are always writable
        return clone;
    }

     public String toString() {
         if (getType() == null) {
             return super.toString();
         }
         StringBuffer buf = new StringBuffer();
         Attribute[] atts = getAttributes();
         boolean first = true;
         for ( Attribute att:atts)
         {
        	 Collection<Object> values = getValues( att);
        	 if  ( values.size() == 0)
        	 {
        		 continue;
        	 }
        	 if ( !first)
             {
                 buf.append(", ");
             }
             else
             {
            	 first = false;
             }
             buf.append( att.getKey());
             buf.append("=");
             buf.append( values);
         }
         return buf.toString();
     }

    public void commitRemove(DynamicType type) throws CannotExistWithoutTypeException 
    {
        throw new CannotExistWithoutTypeException();
    }
}