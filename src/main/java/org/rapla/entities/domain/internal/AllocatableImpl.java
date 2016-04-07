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
package org.rapla.entities.domain.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.rapla.components.util.TimeInterval;
import org.rapla.components.util.iterator.IterableChain;
import org.rapla.components.util.iterator.NestedIterable;
import org.rapla.entities.IllegalAnnotationException;
import org.rapla.entities.RaplaObject;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Permission;
import org.rapla.entities.domain.PermissionContainer;
import org.rapla.entities.domain.ResourceAnnotations;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.entities.dynamictype.internal.ClassificationImpl;
import org.rapla.entities.internal.ModifiableTimestamp;
import org.rapla.entities.storage.CannotExistWithoutTypeException;
import org.rapla.entities.storage.DynamicTypeDependant;
import org.rapla.entities.storage.EntityResolver;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.entities.storage.internal.SimpleEntity;

public final class AllocatableImpl extends SimpleEntity implements Allocatable,DynamicTypeDependant, ModifiableTimestamp {
    
    private ClassificationImpl classification;
    private List<PermissionImpl> permissions = new ArrayList<PermissionImpl>();
    private Date lastChanged;
    private Date createDate;
    private Map<String,String> annotations;
    
    AllocatableImpl() {
        this (null, null);
    }
    
    public AllocatableImpl(Date createDate, Date lastChanged ) {
// No create date should be possible and time should always be set through storage operators as they now the timezone settings
//        if (createDate == null) {
//        	Calendar calendar = Calendar.inject();
//            this.createDate = calendar.getTime();
//       }
//       else
        this.createDate = createDate;
        this.lastChanged = lastChanged;
        if (lastChanged == null)
            this.lastChanged = this.createDate;
    }
    
    public void setResolver( EntityResolver resolver) {
        super.setResolver( resolver);
        if ( classification != null)
        {
        	classification.setResolver( resolver);
        }
        for (PermissionImpl p:permissions)
        {
             p.setResolver( resolver);
        }
    }

    public void setReadOnly() {
        super.setReadOnly( );
        classification.setReadOnly( );
        Iterator<PermissionImpl> it = permissions.iterator();
        while (it.hasNext()) {
            it.next().setReadOnly();
        }
    }

    public Date getLastChanged() {
        return lastChanged;
    }
    
    public Date getCreateDate() {
        return createDate;
    }

    @Override public void setCreateDate(Date date)
    {
        checkWritable();
        this.createDate = date;
    }

    public void setLastChanged(Date date) {
        checkWritable();
        lastChanged = date;
    }
    
    @Override public Class<Allocatable> getTypeClass()
    {
        return Allocatable.class;
    }

    // Implementation of interface classifiable
    public Classification getClassification() { return classification; }
    public void setClassification(Classification classification) {
        this.classification = (ClassificationImpl) classification;
    }

    public String getName(Locale locale) {
        Classification c = getClassification();
        if (c == null)
            return "";
        return c.getName(locale);
    }

    public boolean isPerson() {
    	final Classification classification2 = getClassification();
    	if ( classification2 == null)
    	{
    	    return false;
    	}
        final String annotation = classification2.getType().getAnnotation(DynamicTypeAnnotations.KEY_CLASSIFICATION_TYPE);
        return annotation != null && annotation.equals( DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_PERSON);
    }
    
    
    public TimeInterval getAllocateInterval( User user, Date today) {
        return PermissionContainer.Util.getInterval(permissions,user, today, Permission.ALLOCATE);
    }

   
    
   
    
    @Deprecated
    public boolean isHoldBackConflicts()
    {
		String annotation = getAnnotation(ResourceAnnotations.KEY_CONFLICT_CREATION);
        return annotation != null && annotation.equals(ResourceAnnotations.VALUE_CONFLICT_CREATION_IGNORE);
    }
    
    public void addPermission(Permission permission) {
        checkWritable();
        permissions.add((PermissionImpl)permission);
    }

    public boolean removePermission(Permission permission) {
        checkWritable();
        return permissions.remove(permission);
    }

    public Permission newPermission() {
        PermissionImpl permissionImpl = new PermissionImpl();
        if ( resolver != null)
        {
        	permissionImpl.setResolver( resolver);
        }
		return permissionImpl;
    }

    public Collection<Permission> getPermissionList()
    {
        Collection uncasted = permissions;
        @SuppressWarnings("unchecked")
        Collection<Permission> casted = uncasted;
        return casted;
    }
    
    @Deprecated
    public Permission[] getPermissions() {
        return permissions.toArray( new Permission[permissions.size()]);
    }

    @Override
    public Iterable<ReferenceInfo> getReferenceInfo() {
        return new IterableChain<ReferenceInfo>
            (
             super.getReferenceInfo()
             ,classification.getReferenceInfo()
             ,new NestedIterable<ReferenceInfo,PermissionImpl>( permissions ) {
                     public Iterable<ReferenceInfo> getNestedIterable(PermissionImpl obj) {
                         return obj.getReferenceInfo();
                     }
                 }
             );
    }

    public boolean needsChange(DynamicType type) {
        return classification.needsChange( type );
    }
    
    public void commitChange(DynamicType type) {
        classification.commitChange( type );
    }
    
    public void commitRemove(DynamicType type) throws CannotExistWithoutTypeException 
    {
        classification.commitRemove(type);
    }
        
    public String getAnnotation(String key) {
    	if ( annotations == null)
    	{
    		return null;
    	}
        return annotations.get(key);
    }

    public String getAnnotation(String key, String defaultValue) {
        String annotation = getAnnotation( key );
        return annotation != null ? annotation : defaultValue;
    }

    public void setAnnotation(String key,String annotation) throws IllegalAnnotationException {
        checkWritable();
        if ( annotations == null)
        {
        	annotations = new LinkedHashMap<String, String>(1);
        }
        if (annotation == null) {
            annotations.remove(key);
            return;
        }
        annotations.put(key,annotation);
    }

    public String[] getAnnotationKeys() {
    	if ( annotations == null)
    	{
    		return RaplaObject.EMPTY_STRING_ARRAY;
    	}
        return annotations.keySet().toArray(RaplaObject.EMPTY_STRING_ARRAY);
    }

    public Allocatable clone() {
        AllocatableImpl clone = new AllocatableImpl();
        super.deepClone(clone);
        clone.classification =  classification.clone();
        clone.permissions.clear();
        for (PermissionImpl perm:permissions) {
            PermissionImpl permClone = perm.clone();
            clone.permissions.add(permClone);
        }

        clone.createDate = createDate;
        clone.lastChanged = lastChanged;
        @SuppressWarnings("unchecked")
    	Map<String,String> annotationClone = (Map<String, String>) (annotations != null ?  ((HashMap<String,String>)(annotations)).clone() : null);
        clone.annotations = annotationClone;
        return clone;
    }

    public String toString() {
        StringBuffer buf = new StringBuffer();
        buf.append(getTypeClass());
        buf.append(" [");
        buf.append(super.toString());
        buf.append("] ");
        try
        {
	        if ( getClassification() != null) {
	            buf.append (getClassification().toString()) ;
	        }
        }
        catch ( NullPointerException ex)
        {
        }
        return buf.toString();
    }

    @Override
    public int compareTo(Object r2) {
        if ( ! (r2 instanceof Allocatable))
        {
            return super.compareTo( r2);
        }
        int result = SimpleEntity.timestampCompare( this,(Allocatable)r2);
        if ( result != 0)
        {
            return result;
        }
        else
        {
            return super.compareTo( r2);
        }
    }
    
    @Override
    public void replace(ReferenceInfo origId, ReferenceInfo newId)
    {
        super.replace(origId, newId);
        classification.replace(origId, newId);
    }

}


