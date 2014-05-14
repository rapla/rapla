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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.rapla.components.util.iterator.IteratorChain;
import org.rapla.components.util.iterator.NestedIterator;
import org.rapla.entities.Entity;
import org.rapla.entities.IllegalAnnotationException;
import org.rapla.entities.MultiLanguageName;
import org.rapla.entities.RaplaObject;
import org.rapla.entities.RaplaType;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.Classifiable;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.ClassificationFilter;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.entities.dynamictype.internal.ParsedText.EvalContext;
import org.rapla.entities.dynamictype.internal.ParsedText.Function;
import org.rapla.entities.dynamictype.internal.ParsedText.ParseContext;
import org.rapla.entities.internal.ModifiableTimestamp;
import org.rapla.entities.storage.EntityResolver;
import org.rapla.entities.storage.ParentEntity;
import org.rapla.entities.storage.internal.SimpleEntity;

final public class DynamicTypeImpl extends SimpleEntity implements DynamicType, ParentEntity, ModifiableTimestamp
{
    private Date lastChanged;
    private Date createDate;

    // added an attribute array for performance reasons
	List<AttributeImpl> attributes = new ArrayList<AttributeImpl>();
    MultiLanguageName name  = new MultiLanguageName();
    String key = "";
    //Map<String,String> unparsedAnnotations = new HashMap<String,String>();
    Map<String,ParsedText> annotations = new HashMap<String,ParsedText>();
    transient DynamicTypeParseContext parseContext = new DynamicTypeParseContext(this);
    transient Map<String,AttributeImpl> attributeIndex;
    public DynamicTypeImpl() {
    	this( new Date(),new Date());
    }
    
    public DynamicTypeImpl(Date createDate, Date lastChanged) {
    	this.createDate = createDate;
    	this.lastChanged = lastChanged;
    }

    public void setResolver( EntityResolver resolver) {
        super.setResolver( resolver);
        for (AttributeImpl child:attributes)
        {
        	child.setParent( this);
        }
    	for ( ParsedText annotation: annotations.values())
    	{
    		try {
				annotation.init(parseContext);
			} catch (IllegalAnnotationException e) {
			}
    	}
    }

    public RaplaType<DynamicType> getRaplaType() {return TYPE;}
    
    public boolean isInternal()
    {
    	boolean result =key.startsWith("rapla:");
    	return result;
    }
    
    public Classification newClassification() {
    	return newClassification( true );
    }
    
    public Classification newClassification(boolean useDefaults) {
    	if ( !isReadOnly()) {
    		throw new IllegalStateException("You can only create Classifications from a persistant Version of DynamicType");
    	}
        final ClassificationImpl classification = new ClassificationImpl(this);
        if ( resolver != null)
        {
        	classification.setResolver( resolver);
        }
        // Array could not be up todate
        final Attribute[] attributes2 = getAttributes();
        if ( useDefaults)
        {
	        for ( Attribute att: attributes2)
	        {
	            final Object defaultValue = att.defaultValue();
	            if ( defaultValue != null)
	            {
	                classification.setValue(att, defaultValue);
	            }   
	        }
        }
        return classification;
    }
    
   

    public Classification newClassification(Classification original) {
        if ( !isReadOnly()) {
            throw new IllegalStateException("You can only create Classifications from a persistant Version of DynamicType");
        }
        final ClassificationImpl newClassification = (ClassificationImpl) newClassification(true);
        {
            Attribute[] attributes = original.getAttributes();
            for (int i=0;i<attributes.length;i++) {
                Attribute originalAttribute = attributes[i];
                String attributeKey = originalAttribute.getKey();
                Attribute newAttribute = newClassification.getAttribute( attributeKey );
                Object defaultValue = originalAttribute.defaultValue();
                Object originalValue = original.getValue( attributeKey );
                if ( newAttribute != null  && newAttribute.getType().equals( originalAttribute.getType())) 
                {
                	Object newDefaultValue = newAttribute.defaultValue();
                	// If the default value of the new type differs from the old one and the value is the same as the old default then use the new default
                	if (  newDefaultValue != null && ((defaultValue == null && originalValue == null )|| (defaultValue != null && originalValue != null && !newDefaultValue.equals(defaultValue) && (originalValue.equals( defaultValue)))))
                	{
                		newClassification.setValue( newAttribute, newDefaultValue);
                	}
                	else
                	{
                		newClassification.setValue( newAttribute, newAttribute.convertValue( originalValue ));
                	}
                }
            }
            return newClassification;
        }
    }

    public ClassificationFilter newClassificationFilter() {
    	if ( !isReadOnly()) {
    		throw new IllegalStateException("You can only create ClassificationFilters from a persistant Version of DynamicType");
    	}
        ClassificationFilterImpl classificationFilterImpl = new ClassificationFilterImpl(this);
		if ( resolver != null)
		{
			classificationFilterImpl.setResolver( resolver);
		}
        return classificationFilterImpl;
    }

    public MultiLanguageName getName() {
        return name;
    }

    public void setReadOnly() {
        super.setReadOnly();
        name.setReadOnly( );
    }

    public String getName(Locale locale) {
    	if ( locale == null)
    	{
    		return name.getName( null);
    	}
        String language = locale.getLanguage();
		return name.getName(language);
    }

    public String getAnnotation(String key) {
    	ParsedText parsedAnnotation = annotations.get(key);
        if ( parsedAnnotation != null) 
        {
			return parsedAnnotation.getExternalRepresentation(parseContext);
        } 
        else 
        {
            return null;
        }
    }
    
    @Deprecated
    public Date getLastChangeTime() {
        return lastChanged;
    }
    
    @Override
    public Date getLastChanged() {
    	return lastChanged;
    }

    public Date getCreateTime() {
        return createDate;
    }

    public void setLastChanged(Date date) {
        checkWritable();
    	lastChanged = date;
    }
    
    @Override
    public Iterable<ReferenceInfo> getReferenceInfo() 
    {
        return new IteratorChain<ReferenceInfo>(super.getReferenceInfo(), 
                new NestedIterator<ReferenceInfo,AttributeImpl>( attributes ) 
        {
            public Iterable<ReferenceInfo> getNestedIterator(AttributeImpl obj) {
                return obj.getReferenceInfo();
            }
        }
                );
    }

    @Override
    public void addEntity(Entity entity) 
    {
    	Attribute attribute = (Attribute) entity;
    	attributes.add((AttributeImpl) attribute);
        if (attribute.getDynamicType() != null
            && !this.isIdentical(attribute.getDynamicType()))
            throw new IllegalStateException("Attribute '" + attribute
                                            + "' belongs to another dynamicType :"
                                            + attribute.getDynamicType());
        ((AttributeImpl) attribute).setParent(this);
    }


    public String getAnnotation(String key, String defaultValue) {
    	String annotation = getAnnotation( key );
        return annotation != null ? annotation : defaultValue;
    }

    public void setAnnotation(String key,String annotation) throws IllegalAnnotationException {
        checkWritable();
        if (annotation == null) {
        	annotations.remove(key);
            return;
        }
        ParsedText parsedText = new ParsedText(annotation);
        parsedText.init(parseContext);
        annotations.put(key,parsedText);
    }

    public String[] getAnnotationKeys() {
        return annotations.keySet().toArray(RaplaObject.EMPTY_STRING_ARRAY);
    }

    @Deprecated
    public void setElementKey(String elementKey) {
        setKey(elementKey);
    }

    public void setKey(String key) {
        checkWritable();
        this.key = key;
        for ( ParsedText text:annotations.values())
		{
			text.updateFormatString(parseContext);
		}
    }

    public String getElementKey()
    {
        return getKey();
    }
    
    public String getKey()
    {
        return key;
    }

    /** exchange the two attribute positions */
    public void exchangeAttributes(int index1, int index2) {
        checkWritable();
        Attribute[] attribute = getAttributes();
        Attribute attribute1 = attribute[index1];
        Attribute attribute2 = attribute[index2];
		List<AttributeImpl> newMap = new ArrayList<AttributeImpl>();
        for (int i=0;i<attribute.length;i++) {
        	Attribute att;
        	if (i == index1)
                att = attribute2;
            else if (i == index2)
                att = attribute1;
            else
                att = attribute[i];
            newMap.add((AttributeImpl) att);
        }
        attributes = newMap;
    }

	/** find an attribute in the dynamic-type that equals the specified attribute. */

    public Attribute findAttributeForId(Object id) {
        Attribute[] typeAttributes = getAttributes();
        for (int i=0; i<typeAttributes.length; i++) {
            if (((Entity)typeAttributes[i]).getId().equals(id)) {
                return typeAttributes[i];
            }
        }
        return null;
    }


    /**
	 * @param attributeImpl  
     * @param key 
	 */
    public void keyChanged(AttributeImpl attributeImpl, String key) {
		attributeIndex = null;
		for ( ParsedText text:annotations.values())
		{
			text.updateFormatString(parseContext);
		}
	}
    
    public void removeAttribute(Attribute attribute) {
        checkWritable();
        String matchingAttributeKey = findAttribute( attribute );
		if ( matchingAttributeKey == null) {
            return;
        }
        attributes.remove( attribute);
        if (this.equals(attribute.getDynamicType()))
        {
        	if (((AttributeImpl) attribute).isReadOnly())
        	{
        		throw new IllegalArgumentException("Attribute is not writable. It does not belong to the same dynamictype instance");
        	}
            ((AttributeImpl) attribute).setParent(null);
        }
    }

	public String findAttribute(Attribute attribute) {
		for ( AttributeImpl att: attributes )
        {
        	if (att.equals( attribute))
        	{
        		return att.getKey();
        	}
        }
		return null;
	}

    public void addAttribute(Attribute attribute) {
        checkWritable();
        if  ( hasAttribute(attribute))
        {
            return;
        }
        addEntity(attribute);
        attributeIndex = null;
    }

    public boolean hasAttribute(Attribute attribute) 
    {
        return attributes.contains( attribute );
    }

    public Attribute[] getAttributes() {
        return attributes.toArray(Attribute.ATTRIBUTE_ARRAY);
    }

    public AttributeImpl getAttribute(String key) {
    	if ( attributeIndex == null)
    	{
    		attributeIndex = new HashMap<String, AttributeImpl>();
        	for ( AttributeImpl att:attributes)
        	{
        		attributeIndex.put( att.getKey(), att);
        	}
    	}
    	AttributeImpl attributeImpl = attributeIndex.get( key);
		return attributeImpl;
    }

    public ParsedText getParsedAnnotation(String key) {
        return  annotations.get( key );
    }
    
    @SuppressWarnings("unchecked")
	public Collection<AttributeImpl> getSubEntities() {
    	return attributes;
    }

    public DynamicTypeImpl clone() {
        DynamicTypeImpl clone = new DynamicTypeImpl();
        super.deepClone(clone);
        clone.lastChanged = lastChanged;
        clone.createDate = createDate;
        clone.name = (MultiLanguageName) name.clone();
        clone.key = key;
        for (AttributeImpl att:clone.getSubEntities())
        {
            ((AttributeImpl)att).setParent(clone);
        }
        clone.annotations = new LinkedHashMap<String, ParsedText>();
        DynamicTypeParseContext parseContext = new DynamicTypeParseContext(clone);
        for (Map.Entry<String,ParsedText> entry: annotations.entrySet())
        {
            String annotation = entry.getKey();
            ParsedText parsedAnnotation =entry.getValue();
            String parsedValue = parsedAnnotation.getExternalRepresentation(parseContext);
            try {
                clone.setAnnotation(annotation, parsedValue);
            } catch (IllegalAnnotationException e) {
                throw new IllegalStateException("Can't parse annotation back", e);
            }
        }
        return clone;
    }

    public String toString() {
        StringBuffer buf = new StringBuffer();
        buf.append(" [");
        buf.append ( super.toString()) ;
        buf.append("] key=");
        buf.append( getKey() );
        buf.append(": ");
        if ( attributes != null ) {
            Attribute[] att = getAttributes();
            for ( int i=0;i<att.length; i++){
                if ( i> 0)
                    buf.append(", ");
                buf.append( att[i].getKey());
            }
        }
        return buf.toString();
    }

	/**
	 * @param newType
	 * @param attributeId
	 */
	public boolean hasAttributeChanged(DynamicTypeImpl newType, String attributeId) {
    	Attribute oldAttribute = findAttributeForId(attributeId );
    	Attribute newAttribute = newType.findAttributeForId(attributeId );
    	if ( oldAttribute == null && newAttribute == null)
    	{
    		return false;
    	}
    	if ((newAttribute == null ) ||  ( oldAttribute == null)) {
    		return true;
    	}
		String newKey = newAttribute.getKey();
        String oldKey = oldAttribute.getKey();
		if ( !newKey.equals( oldKey )) {
			return true;
		}
		if ( !newAttribute.getType().equals( oldAttribute.getType())) {
			return true;
		}
		{
			String[] keys = newAttribute.getConstraintKeys();
			String[] oldKeys = oldAttribute.getConstraintKeys();
			if ( keys.length != oldKeys.length) {
				return true;
			}
			for ( int i=0;i< keys.length;i++) {
				if ( !keys[i].equals( oldKeys[i]) )
					return true;
				Object oldConstr = oldAttribute.getConstraint( keys[i]);
				Object newConstr = newAttribute.getConstraint( keys[i]);
				if ( oldConstr == null && newConstr == null)
					continue;
				if ( oldConstr == null || newConstr == null)
					return true;

				if ( !oldConstr.equals( newConstr))
					return true;
			}
		}
		return false;
	}

	public Attribute getFirstAttributeWithAnnotation(String annotationKey) {
        for (Attribute attribute: attributes)
        {
            String annotation = attribute.getAnnotation(annotationKey);
            if  ( annotation != null && annotation.equals("true"))
            {
                return attribute;
            }
        }
        return getAttribute(annotationKey);
    }

    public static boolean isInternalType(Classifiable classifiable) {
		boolean isRaplaType =false;
		Classification classification = classifiable.getClassification();
		if ( classification != null )
		{
			String classificationType = classification.getType().getAnnotation(DynamicTypeAnnotations.KEY_CLASSIFICATION_TYPE);
			if ( classificationType != null && classificationType.equals( DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RAPLATYPE))
			{
				isRaplaType = true;
			}
		}
		return isRaplaType;
	}


	static class DynamicTypeParseContext implements ParseContext {
		private DynamicTypeImpl type;

		DynamicTypeParseContext( DynamicType type)
		{
			this.type = (DynamicTypeImpl)type;
		}
		
		public Function resolveVariableFunction(String variableName) throws IllegalAnnotationException {
			Attribute attribute = type.getAttribute(variableName);
	        if (attribute != null) 
	        {
	        	return new AttributeFunction(attribute);
	        } 
	        else if (variableName.equals(type.getKey())) 
	        {
	        	return new TypeFunction(type);
	        }
	        return null;
		}
		
		class AttributeFunction extends ParsedText.Function
		{
			Object id;
			AttributeFunction(Attribute attribute )
			{
				super("attribute:"+attribute.getKey());
				id =attribute.getId() ;
				
			}
			
			protected String getName() {
			    Attribute attribute = findAttribute( type);
			    if  ( attribute != null)
			    {
			        return attribute.getKey();
			    }
                return name;
			}

			public Attribute eval(EvalContext context) {
				Classification classification = context.getClassification();
				DynamicTypeImpl type = (DynamicTypeImpl) classification.getType();
				return findAttribute(type);
			}

            public Attribute findAttribute(DynamicTypeImpl type) {
                Attribute attribute =  type.findAttributeForId( id );
				if ( attribute!= null) {
					return attribute;
	            }
				return null;
            }
			
			@Override
			public String getRepresentation( ParseContext context)
			{
				
		        Attribute attribute = type.findAttributeForId( id );
		        if ( attribute!= null) {
		        	return attribute.getKey();
		        }
		        return "";
			}
		}
		
		class TypeFunction extends ParsedText.Function
		{
			Object id;
			TypeFunction(DynamicType type) 
			{
				super("type:"+type.getKey());
				id = type.getId() ;
			}
			
			public String eval(EvalContext context) 
			{
				DynamicTypeImpl type = (DynamicTypeImpl) context.getClassification().getType();
				return type.getName( context.getLocale());
			}
			
			@Override
			public String getRepresentation( ParseContext context)
			{
				if ( type.getId().equals( id ) ) {
					return type.getKey();
		        }
				return "";
			}
		}
	}


	public static boolean isTransferedToClient(Classifiable classifiable) {
		if ( classifiable == null)
		{
			return false;
		}
		DynamicType type = classifiable.getClassification().getType();
		boolean result = isTransferedToClient(type);
		return result;
	}

	public static boolean isTransferedToClient(DynamicType type) {
		String annotation = type.getAnnotation( DynamicTypeAnnotations.KEY_TRANSFERED_TO_CLIENT);
		if ( annotation == null)
		{
			return true;
		}
		return !annotation.equals( DynamicTypeAnnotations.VALUE_TRANSFERED_TO_CLIENT_NEVER);
	}


	
	
}


