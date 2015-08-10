/*--------------------------------------------------------------------------*
 | Copyright (C) 21006 Christopher Kohlhaas                                  |
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
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.rapla.components.util.DateTools;
import org.rapla.components.util.ParseDateException;
import org.rapla.components.util.SerializableDateTimeFormat;
import org.rapla.entities.Category;
import org.rapla.entities.Entity;
import org.rapla.entities.EntityNotFoundException;
import org.rapla.entities.IllegalAnnotationException;
import org.rapla.entities.MultiLanguageName;
import org.rapla.entities.RaplaObject;
import org.rapla.entities.RaplaType;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.AttributeType;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.ConstraintIds;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.internal.CategoryImpl;
import org.rapla.entities.storage.EntityResolver;
import org.rapla.entities.storage.internal.SimpleEntity;
import org.rapla.framework.RaplaException;

final public class AttributeImpl extends SimpleEntity implements Attribute
{
	public static final MultiLanguageName TRUE_TRANSLATION = new MultiLanguageName();
	public static final MultiLanguageName FALSE_TRANSLATION = new MultiLanguageName();
	
	static {
		TRUE_TRANSLATION.setName("en", "yes");
		FALSE_TRANSLATION.setName("en", "no");
	}
	private MultiLanguageName name = new MultiLanguageName();
    private AttributeType type;
    private String key;
    private boolean multiSelect;
    private boolean bOptional = false;
    private Map<String,String> annotations = new LinkedHashMap<String,String>();
    private String defaultValue =null;
    private transient DynamicTypeImpl parent;
    
    public final static AttributeType DEFAULT_TYPE = AttributeType.STRING;

    public AttributeImpl() {
        this.type = DEFAULT_TYPE;
    }

    public AttributeImpl(AttributeType type) {
        setType(type);
    }

    void setParent(DynamicTypeImpl parent) {
    	this.parent = parent;
    }
    
    public DynamicType getDynamicType() {
        return parent;
    }

    final public RaplaType<Attribute> getRaplaType() {return TYPE;}

    public RaplaType getRefType() {
    	if (type == null)
    	{
    		return null;
    	}
    	if ( type.equals(AttributeType.CATEGORY))
		{
			return Category.TYPE;
		}
		else if ( type.equals( AttributeType.ALLOCATABLE))
		{
			return Allocatable.TYPE;
		}
		return null;
	}

    
    public AttributeType getType() {
        return type;
    }

    public void setType(AttributeType type) 
    {
        checkWritable();
        Object oldValue = defaultValue;
        if ( type.equals( AttributeType.CATEGORY))
        {
            oldValue = getEntity("default.category", Category.class);
        }
        this.type = type;
        setDefaultValue(convertValue( oldValue));
    }

    public MultiLanguageName getName() {
        return name;
    }

    public void setReadOnly() {
        super.setReadOnly(  );
        name.setReadOnly( );
    }

    public String getName(Locale locale) {
        return name.getName(locale.getLanguage());
    }

    public String getKey() {
        return key;
    }

    public void setConstraint(String key,Object constraint) {
        checkWritable();
        setContraintWithoutWritableCheck(key, constraint);
    }

	public void setContraintWithoutWritableCheck(String key,Object constraint) {
		if ( getConstraintClass( key ) == Category.class || getConstraintClass( key ) == DynamicType.class) {
        	String refID = "constraint." + key;
        	if ( constraint == null)
        	{
        		removeWithKey( refID);
        	} 
        	else if ( constraint instanceof Entity)
        	{
        		putEntity(refID,(Entity)constraint);
        	}
        	else if ( constraint instanceof String)
        	{
        		putId(refID,(String)constraint);
        	}
        }
		if ( key.equals( ConstraintIds.KEY_MULTI_SELECT))
		{
		    multiSelect = constraint != null && "true".equalsIgnoreCase( constraint.toString());
		}
	}
	
	@Override
	protected Class<? extends Entity> getInfoClass(String key) 
	{
	    Class<? extends Entity> infoClass = super.getInfoClass(key);
	    if ( infoClass == null)
	    {
	        if ( key.equals( "default.category"))
	        {
	            return Category.class;
	        }
	        if ( key.startsWith( "constraint."))
            {
	            String constraintKey = key.substring( "constraint.".length());
	            Class<?> constraintClass = getConstraintClass( constraintKey);
	            if ( !constraintClass.equals(String.class))
	            {
	                @SuppressWarnings("unchecked")
                    Class<? extends Entity> casted = (Class<? extends Entity>) constraintClass;
                    return casted;
	            }
            }
	    }
        return infoClass;
	}
    
    public void setDefaultValue(Object object)
    {
        checkWritable();
        if ( type.equals( AttributeType.CATEGORY))
        {
            putEntity("default.category",(Entity)object);
            defaultValue = null;
        } 
        else 
        {
            defaultValue = (String) convertValue(object, AttributeType.STRING);
        }
    }

    public Object getConstraint(String key) {
        if ( key.equals(ConstraintIds.KEY_MULTI_SELECT))
        {
            return multiSelect;
        }
        Class<?> constraintClass = getConstraintClass( key );
        if ( constraintClass == Category.class || constraintClass == DynamicType.class) {
            @SuppressWarnings("unchecked")
            Class<? extends Entity> class1 = (Class<? extends Entity>) constraintClass;
            return getEntity("constraint." + key, class1);
        }
        return null;
    }

    public Class<?> getConstraintClass(String key) {
        if (key.equals(ConstraintIds.KEY_ROOT_CATEGORY)) {
            return Category.class;
        }
        if (key.equals(ConstraintIds.KEY_DYNAMIC_TYPE)) {
            return DynamicType.class;
        }
        if (key.equals(ConstraintIds.KEY_MULTI_SELECT)) {
            return Boolean.class;
        }
        return String.class;
    }

    public String[] getConstraintKeys() {
        if (type.equals( AttributeType.CATEGORY)) {
            return new String[] {ConstraintIds.KEY_ROOT_CATEGORY, ConstraintIds.KEY_MULTI_SELECT};
        } 
        if (type.equals( AttributeType.ALLOCATABLE)) {
            return new String[] {ConstraintIds.KEY_DYNAMIC_TYPE, ConstraintIds.KEY_MULTI_SELECT};
        } else {
            return new String[0];
        }
    }

    public void setKey(String key) {
        checkWritable();
        this.key = key;
        if ( parent != null)
        {
        	parent.keyChanged( this, key);
        }
    }

//    public  boolean isValid(Object obj) {
//        if ( type.equals( AttributeType.CATEGORY))
//        {
//            if ( !(obj instanceof Category))
//            {
//                return false;
//            }
//            final Category rootCategory = (Category)getConstraint(ConstraintIds.KEY_ROOT_CATEGORY);
//            if (rootCategory != null)
//            {
//                final Category cat = (Category)obj;
//                rootCategory.isAncestorOf( cat);
//            }
//        }
//        return true;
//    }

    public boolean isOptional() {
        return bOptional;
    }

    public void setOptional(boolean bOptional) {
        checkWritable();
        this.bOptional = bOptional;
    }

    public Object defaultValue() 
    {
        Object value;
        if ( type.equals( AttributeType.CATEGORY))
        {
            value = getEntity("default.category", Category.class);
        }
        else
        {
            value = convertValue(defaultValue);
        }
        return value;
    }

    public boolean needsChange(Object value) {
        if (value == null)
            return false;

        if (type.equals( AttributeType.STRING )) {
            return !(value instanceof String);
        }
        if (type.equals( AttributeType.INT )) {
            return !(value instanceof Long);
        }
        if (type.equals( AttributeType.DATE )) {
            return !(value instanceof Date);
        }
        if (type.equals( AttributeType.BOOLEAN )) {
            return !(value instanceof Boolean);
        }
        if (type.equals( AttributeType.ALLOCATABLE )) {
            return !(value instanceof Allocatable);
        }
        if (type.equals( AttributeType.CATEGORY )) {
        	if (!(value instanceof Category))
                return true;

            Category temp = (Category) value;

            // look if the attribute category is a ancestor of the value category
            Category rootCategory = (Category) getConstraint(ConstraintIds.KEY_ROOT_CATEGORY);
            if ( rootCategory != null)
            {
            	boolean change =  !rootCategory.isAncestorOf( temp );
            	return change;
            }
            return false;
            
        }
        return false;
    }

    public Object convertValue(Object value) {
        return convertValue(value, type);
    }
    
    private Object convertValue(Object value, AttributeType type) {
        if (type.equals( AttributeType.STRING )) {
            if (value == null)
                return null;
            if (value instanceof Date)
            {
                return new SerializableDateTimeFormat().formatDate( (Date) value);
            }
//            if (value instanceof Category)
//            {
//            	return ((Category) value).get
//            }
            return value.toString();
        }
        if (type.equals( AttributeType.DATE )) {
            if (value == null)
                return null;
            else if (value instanceof Date)
            	return value;
            
            try {
				return new SerializableDateTimeFormat().parseDate( value.toString(), false);
			} catch (ParseDateException e) {
				return null;
			}
        }
        if (type.equals( AttributeType.INT )) {
            if (value == null)
                return null;

            if (value instanceof Boolean)
                return ((Boolean) value).booleanValue() ? new Long(1) : new Long(0);
            String str = value.toString().trim().toLowerCase();
            try {
                return new Long(str);
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        if (type.equals( AttributeType.BOOLEAN )) {
            if (value == null)
                return Boolean.FALSE;
            String str = value.toString().trim().toLowerCase();
            if (str.equals(""))
            {
                return Boolean.FALSE;
            }
            if (str.equals("0") || str.equals("false"))
                return Boolean.FALSE;
            else
                return Boolean.TRUE;
        }
        if (type.equals( AttributeType.ALLOCATABLE)) {
            // we try to convert ids
            if ( value instanceof String)
            {
                Entity result = resolver.tryResolve( (String) value);
                if ( result != null && result instanceof Allocatable)
                {
                    return (Allocatable) result;
                }
            } 
            else if ( value instanceof Allocatable )
            {
                return (Allocatable) value;
            }
        	return null;
        }
        if (type.equals( AttributeType.CATEGORY )) {
            if (value == null)
                return null;
            Category rootCategory = (Category) getConstraint(ConstraintIds.KEY_ROOT_CATEGORY);
            if (value instanceof Category) {
                Category temp = (Category) value;
                if  ( rootCategory != null  ) 
                { 
            	   if (rootCategory.isAncestorOf( temp ))
            	   {
            		   return value;
            	   }

            	   // if the category can't be found under the root then we check if we find a category path with the same keys 
            	   List<String> keyPathRootCategory = ((CategoryImpl)rootCategory).getKeyPath( null);
            	   List<String> keyPath = ((CategoryImpl)temp).getKeyPath( null);
            	   List<String> nonCommonPath = new ArrayList<String>();
            	   boolean differInKeys = false;
            	   // 
            	   for ( int i=0;i<keyPath.size();i++)
            	   {
            		   String key = keyPath.get(i);
            		   String rootCatKey = keyPathRootCategory.size() > i ? keyPathRootCategory.get( i ) : null;
            		   if ( rootCatKey == null || !key.equals(rootCatKey))
            		   {
            			   differInKeys = true;
            		   }
            		   if ( differInKeys)
            		   {
            			   nonCommonPath.add( key);
            		   }
            	   }
            	   
            	   Category parentCategory = rootCategory;
            	   Category newCategory = null;
            	   //we first check for the whole keypath  this covers root changes from b to c, when c contains the b substructure including b
            	   //     a  
            	   //    / \
            	   //  |b| |c|
            	   //  /   /
            	   // d   b
            	   //    /
            	   //   d
            	   for ( String key: nonCommonPath)
            	   {
            		   newCategory = parentCategory.getCategory( key);
            		   if ( newCategory == null)
            		   {
            			   break;
            		   }
            		   else
            		   {
            			   parentCategory = newCategory;
            		   }
            	   }
            	   //if we don't find a category we also check if a keypath that contains on less entry
            	   // covers root changes from b to c when c contains directly the b substructure but not b itself
            	   //     a  
            	   //    / \
            	   //  |b| |c|
            	   //  /   /
            	   // d   d
            	   //    
            	   if ( newCategory == null && nonCommonPath.size() > 1)
            	   {
            		   List<String> subList = nonCommonPath.subList(1, nonCommonPath.size());
            		   for ( String key: subList)
                	   {
                		   newCategory = parentCategory.getCategory( key);
                		   if ( newCategory == null)
                		   {
                			   break;
                		   }
                		   else
                		   {
                			   parentCategory = newCategory;
                		   }
                	   }
            	   }
            	   return newCategory;
               }
            }
            else if ( value instanceof String)
            {
                Entity result = resolver.tryResolve( (String) value);
                if ( result != null && result instanceof Category)
                {
                    return (Category) result;
                }
            }
            if ( rootCategory != null)
            {
	            Category category = rootCategory.getCategory(value.toString());
	            if ( category == null)
	            {
	            	return null;
	            }
	            return category;
            }
        }
        return null;
    }

    public String getAnnotation(String key) {
        return annotations.get(key);
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
        // multiselect is now a constraint so we keep this for backward compatibility with old data format
        if ( key.equals( ConstraintIds.KEY_MULTI_SELECT))
        {
            multiSelect = annotation != null && annotation.equalsIgnoreCase("true");
        }
        else
        {
            annotations.put(key,annotation);
            
        }
    }

    public String[] getAnnotationKeys() {
        return annotations.keySet().toArray(RaplaObject.EMPTY_STRING_ARRAY);
    }


    public Attribute clone() {
        AttributeImpl clone = new AttributeImpl();
        super.deepClone(clone);
        clone.name = (MultiLanguageName) name.clone();
        @SuppressWarnings("unchecked")
		HashMap<String,String> annotationClone = (HashMap<String,String>) ((HashMap<String,String>) annotations).clone();
		clone.annotations = annotationClone;
        clone.type = getType();
        clone.multiSelect = multiSelect;
        clone.setKey(getKey());
        clone.setOptional(isOptional());
        String[] constraintKeys = getConstraintKeys();
        for ( int i = 0;i < constraintKeys.length; i++) {
            String key = constraintKeys[ i ];
            clone.setConstraint( key, getConstraint(key));
        }
        clone.setDefaultValue( defaultValue());
        return clone;
    }

    public String toString() {
        MultiLanguageName name = getName();
        if (name != null) {
            return name.toString()+ " ID='" + getId() + "'";
        }  else {
            return getKey()  + " " + getId();
        }
    }

    @SuppressWarnings("deprecation")
    static public Object parseAttributeValue(Attribute attribute,String text) throws RaplaException {
        AttributeType type = attribute.getType();
        final String trim = text.trim();
        EntityResolver resolver = ((AttributeImpl)attribute).getResolver();
        if (type.equals( AttributeType.STRING )) {
            return text;
        }
        else if (type.equals( AttributeType.ALLOCATABLE)) {
            String path = trim;
            if (path.length() == 0) {
                return null;
            }
            return path;
        }
        else if (type.equals( AttributeType.CATEGORY )) {
            String path = trim;
            if (path.length() == 0) {
                return null;
            }
            if ( resolver != null)
            {
                if ( resolver.tryResolve( path) != null)
                {
                    return path;
                }
            }
            if (org.rapla.storage.OldIdMapping.isTextId(Category.TYPE,path) ) {
            	String id = org.rapla.storage.OldIdMapping.getId( Category.TYPE, path);
            	return id ;
            } else {
                CategoryImpl rootCategory = (CategoryImpl)attribute.getConstraint(ConstraintIds.KEY_ROOT_CATEGORY);
                 if (rootCategory == null) {
                   //System.out.println( attribute.getConstraintKeys());
                   throw new RaplaException("Can't find " + ConstraintIds.KEY_ROOT_CATEGORY + " for attribute " + attribute);
                }
                Category categoryFromPath; 
                try
                {
                    categoryFromPath = rootCategory.getCategoryFromPath(path);
                } 
                catch (EntityNotFoundException ex)
                {
                    while ( rootCategory.getParent() != null)
                    {
                        rootCategory = (CategoryImpl) rootCategory.getParent();
                        if ( rootCategory.isAncestorOf( rootCategory))
                        {
                            throw new IllegalStateException("Illegal category circle detected!");
                        }
                    }
                    categoryFromPath = rootCategory.getCategoryFromPath(path);
                }
                return categoryFromPath;
            }
        } else if (trim.length() == 0)
        {
            return null;
        }
        else if (type.equals(AttributeType.BOOLEAN)) {
            return trim.equalsIgnoreCase("true") || trim.equals("1")  ?
                Boolean.TRUE : Boolean.FALSE;
        } else if (type.equals( AttributeType.DATE )) {
            try {
				return new SerializableDateTimeFormat().parseDate( trim, false);
			} catch (ParseDateException e) {
				throw new RaplaException( e.getMessage(), e);
			}
        } else  if (type.equals( AttributeType.INT)) {
            try {
                return new Long( trim );
            } catch (NumberFormatException ex) {
                throw new RaplaException( ex.getMessage());
            }
        }
        
        throw new RaplaException("Unknown attribute type: " + type );
    }

    public static String attributeValueToString( Attribute attribute, Object value, boolean idOnly) throws EntityNotFoundException {
	    AttributeType type = attribute.getType();
	    if (type.equals( AttributeType.ALLOCATABLE ))
	    {
	    	return ((Entity)value).getId().toString();
	    }
	    if (type.equals( AttributeType.CATEGORY ))
	    {
	        CategoryImpl rootCategory = (CategoryImpl) attribute.getConstraint(ConstraintIds.KEY_ROOT_CATEGORY);
	        if ( idOnly) {
	            return ((Entity)value).getId().toString();
	        } else {
	           return  rootCategory.getPathForCategory((Category)value) ;
	        }
	    }
	    else if (type.equals( AttributeType.DATE ))
	    {
	        return new SerializableDateTimeFormat().formatDate( (Date)value ) ;
	    }
	    else
	    {
	        return value.toString() ;
	    }
    }

    static public class IntStrategy {
        String[] constraintKeys = new String[] {"min","max"};

        public String[] getConstraintKeys() {
            return constraintKeys;
        }

        public boolean needsChange(Object value) {
            return !(value instanceof Long);
        }

        public Object convertValue(Object value) {
            if (value == null)
                return null;

            if (value instanceof Boolean)
                return ((Boolean) value).booleanValue() ? new Long(1) : new Long(0);
            String str = value.toString().trim().toLowerCase();
            try {
                return new Long(str);
            } catch (NumberFormatException ex) {
                return null;
            }
        }
    }

    //static ThreadLocal<AtomicInteger> stackSafeCounter = new ThreadLocal<AtomicInteger>();
    
    public String getValueAsString(Locale locale,Object value)
	{
		if (value == null)
	        return "";
	    if (value instanceof Category) {
	        Category rootCategory = (Category) getConstraint(ConstraintIds.KEY_ROOT_CATEGORY);
	        return ((Category) value).getPath(rootCategory, locale);
	    }
	    if (value instanceof Allocatable) {
            Allocatable allocatable = (Allocatable) value;
	        Classification classification = allocatable.getClassification();
            if ( classification == null)
            {
                return "";
            }
            String name = classification.getName( locale );
            return name;
	    }
	    if (value instanceof Date) {
	        return DateTools.formatDate((Date) value, locale);
	    }
	     if (value instanceof Boolean) {
	    	 return getBooleanTranslation(locale, (Boolean) value);
	    } else {
	        return value.toString();
	    }	
	}

	public static String getBooleanTranslation(Locale locale, Boolean value) {
		if (locale == null)
		 {
			 locale = Locale.getDefault();
		 }
		 String language = locale.getLanguage();
		 if ( (Boolean) value)
		 {
			return TRUE_TRANSLATION.getName( language);
		 }
		 else
		 {
			 return FALSE_TRANSLATION.getName( language);
		 }
	}

	public String toStringValue( Object value) {
		String stringValue = null;
		RaplaType refType = getRefType();
		if (refType != null) 
		{
			if ( value instanceof Entity)
			{
				stringValue = ((Entity) value).getId();
			}
			else 
			{
				stringValue = value.toString();
			}
		} 
		else if (type.equals( AttributeType.DATE )) {
			return new SerializableDateTimeFormat().formatDate((Date)value);
		}
		else if ( value != null)
		{
			stringValue = value.toString();
		}
		return stringValue;
	}

	public Object fromString(EntityResolver resolver, String value) throws EntityNotFoundException {
		RaplaType refType = getRefType();
		if (refType != null) 
		{
		    Entity resolved = resolver.resolve( value );
			return resolved;
		}
		Object result;
		try {
			result = parseAttributeValue(this, value);
		} catch (RaplaException e) {
			throw new IllegalStateException("Value not parsable");
		}
		return result;
	}
    

}




