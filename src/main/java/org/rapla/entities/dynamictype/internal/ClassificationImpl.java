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

import org.rapla.components.util.SerializableDateTimeFormat;
import org.rapla.entities.Entity;
import org.rapla.entities.EntityNotFoundException;
import org.rapla.entities.ReadOnlyException;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.AttributeType;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.entities.storage.CannotExistWithoutTypeException;
import org.rapla.entities.storage.DynamicTypeDependant;
import org.rapla.entities.storage.EntityReferencer;
import org.rapla.entities.storage.EntityResolver;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.entities.storage.UnresolvableReferenceExcpetion;
import org.rapla.framework.RaplaException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Set;

/** Use the method <code>newClassification()</code> of class <code>DynamicType</code> to
 *  createInfoDialog a classification. Once created it is not possible to change the
 *  type of a classifiction. But you can replace the classification of an
 *  object implementing <code>Classifiable</code> with a new one.
 *  @see DynamicType
 *  @see org.rapla.entities.dynamictype.Classifiable
 */
public class ClassificationImpl implements Classification,DynamicTypeDependant, EntityReferencer {

	private String typeId;
	private String type;
	private Map<String,List<String>> data = new LinkedHashMap<>();
	private transient boolean readOnly = false;

	private transient TextCache name;
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
    		DynamicTypeImpl type = getType();
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
            nameString = format(locale, keyNameFormat);
            return nameString;
    	}
    }

    public ClassificationImpl()
    {

    }

    ClassificationImpl(DynamicTypeImpl dynamicType) {
        typeId = dynamicType.getId();
        type = dynamicType.getKey();
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

    @Override
    public Iterable<ReferenceInfo> getReferenceInfo() {
        List<ReferenceInfo> result = new ArrayList<>();
        String parentId = getParentId();
        result.add( new ReferenceInfo(parentId, DynamicType.class) );
        DynamicTypeImpl type = getType();
        for ( Map.Entry<String,List<String>> entry:data.entrySet())
        {
            String key = entry.getKey();
            Attribute attribute = type.getAttribute(key);
            if ( attribute == null)
            {
                continue;
            }
             Class<? extends Entity > refType = attribute.getRefType();
            if ( refType == null)
            {
                continue;
            }
            List<String> values = entry.getValue();
            if  (values != null )
            {
                for ( String value:values)
                {
                    result.add(new ReferenceInfo(value, refType) );
                }
            }
        }
        return result;
    }



	private String getParentId() {
		if  (typeId != null)
			return typeId;
		if (type == null)
		{
			throw new UnresolvableReferenceExcpetion( "type and parentId are both not set");
		}
		DynamicType dynamicType = resolver.getDynamicType( type);
		if ( dynamicType == null)
		{
			throw new UnresolvableReferenceExcpetion( type);
		}
		typeId = dynamicType.getId();
		return typeId;

	}

    public DynamicTypeImpl getType() {
    	if ( resolver == null)
    	{
    		throw new IllegalStateException("Resolver not set on classification  ");
    	}
        String parentId = getParentId();
		DynamicTypeImpl type = (DynamicTypeImpl) resolver.tryResolve( parentId, DynamicType.class);
        if ( type == null)
        {
        	throw new UnresolvableReferenceExcpetion(DynamicType.class +":" + parentId + " " +data);
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

    public String format( Locale locale, String annotationName)
    {
        DynamicTypeImpl type = getType();
        ParsedText parsedAnnotation = type.getParsedAnnotation( annotationName );
        if ( parsedAnnotation == null)
        {
            return "";
        }
        EvalContext evalContext = type.createEvalContext(locale, annotationName, this);
        String nameString = parsedAnnotation.formatName(evalContext).trim();
        return nameString;
    }


//    public String getNamePlaning(Locale locale) {
//        if ( namePlaning == null)
//        {
//            namePlaning = new TextCache();
//        }
//        return namePlaning.getNamespace(locale,  DynamicTypeAnnotations.KEY_NAME_FORMAT_PLANNING);
//    }


    public String getValueAsString(Attribute attribute,Locale locale)
    {
    	Collection values =  getValues(attribute);
        StringBuilder buf = new StringBuilder();
        boolean first = true;
        for ( Object value: values)
        {
            if (first)
            {
                first = false;
            }
            else
            {
                buf.append(", ");
            }
        	buf.append( ((AttributeImpl)attribute).getValueAsString( locale, value));
        }
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
		if ( !newType.getKey().equals( type.getKey()))
        	return true;

        for (String key:data.keySet()) {
        	Attribute attribute = getType().getAttribute(key);
        	if ( attribute == null)
        	{
        	    return true;
        	}
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

        Collection<String> removedKeys = new ArrayList<>();
        Map<Attribute,Attribute> attributeMapping = new HashMap<>();
        for  (String key:data.keySet()) {
        	Attribute attribute = getType().getAttribute(key);
        	Attribute attribute2 = type.getAttribute(key);
        	// key now longer availabe so remove it
            if ( attribute2 == null)
            {
                removedKeys.add( key );
            }
			if ( attribute == null)
			{
        		continue;
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
			Collection<Object> convertedValues = new ArrayList<>();
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
        	data.remove( key );
        }
        this.type = type.getKey();
        name = null;
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

    	setValueForAttribute( attribute,value);
    }

    public Object getValue(String key) {
    	final Attribute attribute = getAttribute( key );
    	if ( attribute == null ) {
    		throw new NoSuchElementException("No attribute found for key " + key);
    	}
    	return getValueForAttribute(attribute);
    }

    public void setValueForAttribute(Attribute attribute,Object value) {
    	checkWritable();
    	if ( value != null && !(value instanceof Collection<?>))
    	{
    		value = Collections.singleton( value);
    	}
    	setValues(attribute, (Collection<?>) value);
    }

    public <T> void setValues(String attributeKey, T[] values) {
      setValues(getAttribute(attributeKey), Arrays.asList(values));
    }

    public <T> void setValues(Attribute attribute,Collection<T> values) {
        checkWritable();
        String attributeKey = attribute.getKey();
		if ( values == null || values.isEmpty())
        {
			data.remove(attributeKey);
			name = null;
        	return;
        }
		ArrayList<String> newValues = new ArrayList<>();
		for (Object value:values)
		{
			String stringValue = toStringValue(attribute,value);
			if ( stringValue != null)
			{
				newValues.add(stringValue);
			}
        }
		data.put(attributeKey,newValues);
        //isNameUpToDate = false;
        name = null;
    }

    public void addRefValue(Attribute attribute, ReferenceInfo info) throws RaplaException
    {
        if ( info == null)
        {
            return;
        }
        if ( attribute.getRefType() != info.getType())
        {
            throw new RaplaException("Different reference type exepcted " + attribute.getRefType() + " but was " + info.getType());
        }
        String attributeKey = attribute.getKey();
        final String id = info.getId();
        addValue(attributeKey, id);
    }

    public <T> void addValue(Attribute attribute,T value) {
    	checkWritable();
    	String attributeKey = attribute.getKey();
    	String stringValue = toStringValue( attribute,value);
        if ( stringValue == null)
        {
        	return;
        }
        addValue(attributeKey, stringValue);
    }

    private void addValue(String attributeKey, String stringValue)
    {
        List<String> l = data.get(attributeKey);
        if ( l == null)
        {
            l = new ArrayList<>();
            data.put(attributeKey, l);
        }
        l.add(stringValue);
    }

    public Collection<String> getValuesUnresolvedStrings(Attribute attribute) {
        if ( attribute == null ) {
            throw new NullPointerException("Attribute can't be null");
        }
        String attributeKey = attribute.getKey();
        // first lookupDeprecated in attribute map
        List<String> list = data.get(attributeKey);
        if ( list == null || list.size() == 0)
        {
            return Collections.emptyList();
        }
        return list;
    }

    public Collection<Object> getValues(Attribute attribute) {
    	if ( attribute == null ) {
    		throw new NullPointerException("Attribute can't be null");
    	}
    	String attributeKey = attribute.getKey();
    	// first lookupDeprecated in attribute map
        List<String> list = data.get(attributeKey);
        if ( list == null || list.size() == 0)
        {
        	return Collections.emptyList();
        }
        List<Object> result = new ArrayList<>();
        for (String value:list)
        {
        	Object obj;
			try {
				obj = fromString(attribute,resolver,value);
				result.add( obj);
			} catch (EntityNotFoundException e) {
			}
        }
        return result;
    }

    /** returns the string representation of the given value. if attribute is a reference then the id of the referenced object is returned.*/
    private String toStringValue( Attribute attribute,Object value) {
        String stringValue = null;
        Class<? extends Entity> refType = attribute.getRefType();
        AttributeType attributeType = attribute.getType();
        if (refType != null)
        {
            if ( value instanceof Entity && ((Entity)value).getTypeClass() == refType)
            {
                stringValue = ((Entity) value).getId();
            }
            else
            {
                throw new IllegalArgumentException("entity expected. but id used please use addRefValue instead of addValue in reading");
            }
        }
        else if (attributeType.equals( AttributeType.DATE )) {
            return new SerializableDateTimeFormat().formatDate((Date)value);
        }
        else if ( value != null)
        {
            stringValue = value.toString();
        }
        return stringValue;
    }

    private Object fromString(Attribute attribute,EntityResolver resolver, String value) throws EntityNotFoundException, IllegalStateException {
        Class<? extends Entity> refType = attribute.getRefType();
        if (refType != null)
        {
            Entity resolved = resolver.resolve( value, refType );
            return resolved;
        }
        try
        {
            Object result = AttributeImpl.parseAttributeValueWithoutRef(attribute, value);
            return result;
        }
        catch (RaplaException exception)
        {
            throw new IllegalStateException(exception.getMessage(),exception);
        }
    }

    public String getValueUnresolvedString(Attribute attribute) {
        if ( attribute == null ) {
            throw new NullPointerException("Attribute can't be null");
        }
        String attributeKey = attribute.getKey();
        // first lookupDeprecated in attribute map
        List<String> o = data.get(attributeKey);
        if ( o == null  || o.size() == 0)
        {
            return null;
        }
        String stringRep = o.get(0);
        return stringRep;
    }

    public Object getValueForAttribute(Attribute attribute) {
    	if ( attribute == null ) {
    		throw new NullPointerException("Attribute can't be null");
    	}
    	String attributeKey = attribute.getKey();
        // first lookupDeprecated in attribute map
        List<String> o = data.get(attributeKey);
        if ( o == null  || o.size() == 0)
        {
        	return null;
        }
        String stringRep = o.get(0);
        Object fromString;
		try {
			fromString = fromString(attribute,resolver, stringRep);
			return fromString;
		} catch (EntityNotFoundException e) {
			return null;
        }
    }

	public ClassificationImpl clone() {
        ClassificationImpl clone = new ClassificationImpl(getType());
        //clone.referenceHandler = (ReferenceHandler) referenceHandler.clone((Map<String, List<String>>) ((HashMap<String, List<String>>)data).clone());
        //clone.attributeValueMap = (HashMap<String,Object>) attributeValueMap.clone();
        for ( Map.Entry<String,List<String>> entry: data.entrySet())
        {
        	String key = entry.getKey();
			List<String> value = new ArrayList<>(entry.getValue());
			clone.data.put(key, value);
        }
        clone.resolver = resolver;
        clone.typeId = getParentId();
        clone.type = type;
        clone.name = null;
        clone.readOnly = false;// clones are always writable
        return clone;
    }

     public String toString() {
         try
         {
             StringBuilder builder = new StringBuilder();
             boolean first = true;
             builder.append("{");
             for ( Attribute attribute:getAttributes())
             {
                 if ( !first)
                 {
                     builder.append(", ");
                 }
                 else
                 {
                     first = false;
                 }
                 String key = attribute.getKey();
                 String valueAsString = getValueAsString(attribute, null);
                 builder.append(key);
                 builder.append(':');
                 builder.append(valueAsString);
             }
             builder.append("}");
             return builder.toString();
         } catch (Exception ex)
         {
             return data.toString();
         }
     }

    public void commitRemove(DynamicType type) throws CannotExistWithoutTypeException
    {
        throw new CannotExistWithoutTypeException();
    }

    @Override
    public void replace(ReferenceInfo origId, ReferenceInfo newId)
    {
        final Set<Entry<String, List<String>>> entrySet = data.entrySet();
        for (Entry<String, List<String>> entry : entrySet)
        {
            final String attributeKey = entry.getKey();
            final Attribute attribute = getAttribute(attributeKey);
            if (attribute.getRefType() == Allocatable.class)
            {
                final List<String> list = entry.getValue();
                final String origIdString = origId.getId();
                if (list.contains(origIdString))
                {
                    list.remove(origIdString);
                    final String newIdString = newId.getId();
                    if (!list.contains(newIdString))
                    {
                        list.add(newIdString);
                    }

                }
            }
        }
    }
}