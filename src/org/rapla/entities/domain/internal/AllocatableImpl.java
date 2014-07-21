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

import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.rapla.components.util.TimeInterval;
import org.rapla.components.util.iterator.IterableChain;
import org.rapla.components.util.iterator.NestedIterable;
import org.rapla.entities.Category;
import org.rapla.entities.IllegalAnnotationException;
import org.rapla.entities.RaplaObject;
import org.rapla.entities.RaplaType;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Permission;
import org.rapla.entities.domain.ResourceAnnotations;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.entities.dynamictype.internal.ClassificationImpl;
import org.rapla.entities.internal.ModifiableTimestamp;
import org.rapla.entities.storage.CannotExistWithoutTypeException;
import org.rapla.entities.storage.DynamicTypeDependant;
import org.rapla.entities.storage.EntityResolver;
import org.rapla.entities.storage.internal.SimpleEntity;

public final class AllocatableImpl extends SimpleEntity implements Allocatable,DynamicTypeDependant, ModifiableTimestamp {
    
    private ClassificationImpl classification;
    private Set<PermissionImpl> permissions = new LinkedHashSet<PermissionImpl>();
    private Date lastChanged;
    private Date createDate;
    private Map<String,String> annotations;
    
    transient private boolean permissionArrayUpToDate = false;
    transient private PermissionImpl[] permissionArray;

    AllocatableImpl() {
        this (null, null);
    }
    
    public AllocatableImpl(Date createDate, Date lastChanged ) {
// No create date should be possible and time should always be set through storage operators as they now the timezone settings
//        if (createDate == null) {
//        	Calendar calendar = Calendar.getInstance();
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
        for (Iterator<PermissionImpl> it = permissions.iterator();it.hasNext();)
        {
             it.next().setResolver( resolver);
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
    
    @Deprecated
    public Date getLastChangeTime() {
        return lastChanged;
    }

    public Date getCreateTime() {
        return createDate;
    }

    public void setLastChanged(Date date) {
        checkWritable();
    	lastChanged = date;
    }
    
    public void setCreateDate(Date createDate) {
        checkWritable();
        this.createDate = createDate;
    }
    
    public RaplaType<Allocatable> getRaplaType() {
    	return TYPE;
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
    
    static private boolean hasAccess(Iterable<? extends Permission> permissions, User user, int accessLevel, Date start, Date end, Date today, boolean checkOnlyToday ) {
        if ( user == null || user.isAdmin() )
            return true;
      
        int maxAccessLevel = 0;
        int maxEffectLevel = Permission.NO_PERMISSION;
        Category[] originalGroups = user.getGroups();
		Collection<Category> groups = new HashSet<Category>( Arrays.asList( originalGroups));
        for ( Category group: originalGroups)
        {
        	Category parent = group.getParent();
        	while ( parent != null)
        	{
        		if ( ! groups.contains( parent))
        		{
        			groups.add( parent);
        		}
        		if ( parent == group)
        		{
        			throw new IllegalStateException("Parent added to own child");
        		}
        		parent = parent.getParent();
        	}
        }
        for ( Permission p:permissions ) {
            int effectLevel = ((PermissionImpl)p).getUserEffect(user, groups);

            if ( effectLevel >= maxEffectLevel && effectLevel > Permission.NO_PERMISSION)
            {
            	if ( p.hasTimeLimits() && accessLevel >= Permission.ALLOCATE && today!= null)
            	{
            		if (p.getAccessLevel() != Permission.ADMIN  )
            		{
            			if  ( checkOnlyToday )
            			{
            				if (!((PermissionImpl)p).valid(today))
            				{
            					continue;
            				}
            			}
            			else
            			{
            				if (!p.covers( start, end, today ))
            				{
            					continue;
            				}
            			}
            		}
            	}
            	if ( maxAccessLevel < p.getAccessLevel() || effectLevel > maxEffectLevel)
            	{
            		maxAccessLevel = p.getAccessLevel();
            	}
            	maxEffectLevel = effectLevel;
            }
        }
        boolean granted = maxAccessLevel >= accessLevel ;
        return granted;
    }
    
    public TimeInterval getAllocateInterval( User user, Date today) {
        if ( user == null || user.isAdmin() )
            return new TimeInterval( null, null);
      
        TimeInterval interval = null;
        int maxEffectLevel = Permission.NO_PERMISSION;
        for ( Permission p:permissions) 
        {
            int effectLevel = p.getUserEffect(user);
            int accessLevel = p.getAccessLevel();
			if ( effectLevel >= maxEffectLevel && effectLevel > Permission.NO_PERMISSION && accessLevel>= Permission.ALLOCATE)
            {
				Date start;
				Date end;
        		if (accessLevel != Permission.ADMIN  )
        		{
        			start = p.getMinAllowed( today);
        			end = p.getMaxAllowed(today);
        			if ( end != null && end.before( today))
        			{
        				continue;
        			}
        		}
        		else
        		{
        			start = null;
        			end = null;
        		}
        		if ( interval == null || effectLevel > maxEffectLevel)
        		{
        			interval = new TimeInterval(start, end);
        		}
        		else
            	{
          			interval = interval.union(new TimeInterval(start, end));
            	}
            	maxEffectLevel = effectLevel;
            }
        }
        return interval;
    }
    
    private boolean hasAccess( User user, int accessLevel ) {
        return hasAccess(permissions,user, accessLevel, null, null, null, false);
    }

    public boolean canCreateConflicts( User user ) {
        return hasAccess( user, Permission.ALLOCATE_CONFLICTS);
    }
    
    public boolean canModify(User user) {
        return hasAccess( user, Permission.ADMIN);
    }

    public boolean canRead(User user) 
    {
        return hasAccess( user, Permission.READ );
    }
    
    @Deprecated
    public boolean isHoldBackConflicts()
    {
		String annotation = getAnnotation(ResourceAnnotations.KEY_CONFLICT_CREATION);
		if ( annotation != null && annotation.equals(ResourceAnnotations.VALUE_CONFLICT_CREATION_IGNORE))
		{
			return true;
		}
		return false;
    }
    
    public boolean canReadOnlyInformation(User user) 
    {
        return hasAccess( user, Permission.READ_ONLY_INFORMATION );
    }
    
    public boolean canAllocate( User user,Date today ) {
        boolean hasAccess = hasAccess(permissions,user, Permission.ALLOCATE, null, null, today, true);
        if ( !hasAccess )
        {
        	return false;
        }
        
        return true;
    }
    
    public boolean canAllocate( User user, Date start, Date end, Date today ) {
        return hasAccess(permissions,user, Permission.ALLOCATE,start, end, today, false);
    }

    public void addPermission(Permission permission) {
        checkWritable();
        permissionArrayUpToDate = false;
        permissions.add((PermissionImpl)permission);
    }

    public boolean removePermission(Permission permission) {
        checkWritable();
        permissionArrayUpToDate = false;
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
    
    public Permission[] getPermissions() {
        updatePermissionArray();
        return permissionArray;
    }

    private void updatePermissionArray() {
        if ( permissionArrayUpToDate )
            return;

         synchronized ( this) {
            permissionArray = permissions.toArray(new PermissionImpl[] {});
            permissionArrayUpToDate = true;
		}
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
        clone.permissionArrayUpToDate = false;
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
        buf.append(getRaplaType().getLocalName());
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

	public int compareTo(Allocatable o) 
	{
		return super.compareTo(o);
	}

   


}


