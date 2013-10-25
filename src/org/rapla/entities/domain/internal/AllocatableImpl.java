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
import java.util.Set;

import org.rapla.components.util.TimeInterval;
import org.rapla.components.util.iterator.IteratorChain;
import org.rapla.components.util.iterator.NestedIterator;
import org.rapla.entities.Category;
import org.rapla.entities.EntityNotFoundException;
import org.rapla.entities.IllegalAnnotationException;
import org.rapla.entities.RaplaObject;
import org.rapla.entities.RaplaType;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Permission;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.entities.dynamictype.internal.ClassificationImpl;
import org.rapla.entities.internal.ModifiableTimestamp;
import org.rapla.entities.storage.CannotExistWithoutTypeException;
import org.rapla.entities.storage.DynamicTypeDependant;
import org.rapla.entities.storage.EntityResolver;
import org.rapla.entities.storage.RefEntity;
import org.rapla.entities.storage.internal.SimpleEntity;

public class AllocatableImpl extends SimpleEntity<Allocatable> implements Allocatable,DynamicTypeDependant, ModifiableTimestamp {
    
    private ClassificationImpl classification;
    private boolean holdBackConflicts;
    private Set<PermissionImpl> permissions = new LinkedHashSet<PermissionImpl>();
    private Date lastChanged;
    private Date createDate;
    private HashMap<String,String> annotations = new LinkedHashMap<String,String>();
    
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
    
    public void resolveEntities( EntityResolver resolver) throws EntityNotFoundException {
        super.resolveEntities( resolver);
        classification.resolveEntities( resolver);
        for (Iterator<PermissionImpl> it = permissions.iterator();it.hasNext();)
        {
             it.next().resolveEntities( resolver);
        }
    }

    public void setReadOnly(boolean enable) {
        super.setReadOnly( enable );
        classification.setReadOnly( enable );
        Iterator<PermissionImpl> it = permissions.iterator();
        while (it.hasNext()) {
            it.next().setReadOnly(enable);
        }
    }

    public Date getLastChangeTime() {
        return lastChanged;
    }

    public Date getCreateTime() {
        return createDate;
    }

    public void setLastChanged(Date date) {
        lastChanged = date;
    }
    
    public RaplaType<Allocatable> getRaplaType() {
    	return TYPE;
    }
    
    // Implementation of interface classifiable
    public Classification getClassification() { return classification; }
    public void setClassification(Classification classification) {
        this.classification = (ClassificationImpl) classification;
    }

    public void setHoldBackConflicts(boolean enable) {
        holdBackConflicts = enable;
    }
    public boolean isHoldBackConflicts() {
        return holdBackConflicts;
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
    
    private boolean hasAccess( User user, int accessLevel, Date start, Date end, Date today, boolean checkOnlyToday ) {
        Permission[] permissions = getPermissions();
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
        for ( int i = 0; i < permissions.length; i++ ) {
            Permission p = permissions[i];
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
    	Permission[] permissions = getPermissions();
        if ( user == null || user.isAdmin() )
            return new TimeInterval( null, null);
      
        TimeInterval interval = null;
        int maxEffectLevel = Permission.NO_PERMISSION;
        for ( int i = 0; i < permissions.length; i++ ) {
            Permission p = permissions[i];
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
        return hasAccess(user, accessLevel, null, null, null, false);
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
    
    public boolean canReadOnlyInformation(User user) 
    {
        return hasAccess( user, Permission.READ_ONLY_INFORMATION );
    }
    
    public boolean canAllocate( User user,Date today ) {
        boolean hasAccess = hasAccess(user, Permission.ALLOCATE, null, null, today, true);
        if ( !hasAccess )
        {
        	return false;
        }
        
        return true;
    }
    
    public boolean canAllocate( User user, Date start, Date end, Date today ) {
        return hasAccess(user, Permission.ALLOCATE,start, end, today, false);
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
        return new PermissionImpl();
    }

    public Permission[] getPermissions() {
        updatePermissionArray();
        return permissionArray;
    }

    private void updatePermissionArray() {
        if ( permissionArrayUpToDate )
            return;

        permissionArray = permissions.toArray(new PermissionImpl[] {});
        permissionArrayUpToDate = true;
    }

    public Iterable<RefEntity<?>> getReferences() {
        return new IteratorChain<RefEntity<?>>
            (
             classification.getReferences()
             ,new NestedIterator<RefEntity<?>>( permissions ) {
                     public Iterable<RefEntity<?>> getNestedIterator(Object obj) {
                         return ((PermissionImpl)obj).getReferences();
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
        
    public boolean isRefering(RefEntity<?> object) {
        if (super.isRefering(object))
            return true;
        if (classification.isRefering(object))
            return true;
        Permission[] permissions = getPermissions();
        for ( int i = 0; i < permissions.length; i++ ) {
            if ( ((PermissionImpl) permissions[i]).isRefering( object ) )
                return true;
        }
        return false;
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
        annotations.put(key,annotation);
    }

    public String[] getAnnotationKeys() {
        return annotations.keySet().toArray(RaplaObject.EMPTY_STRING_ARRAY);
    }

    
    static private void copy(AllocatableImpl source,AllocatableImpl dest) {
        dest.permissionArrayUpToDate = false;
        dest.classification =  (ClassificationImpl) source.classification.clone();

        dest.permissions.clear();
        Iterator<PermissionImpl> it = source.permissions.iterator();
        while ( it.hasNext() ) {
            dest.permissions.add(it.next().clone());
        }

        dest.holdBackConflicts = source.holdBackConflicts;
        dest.createDate = source.createDate;
        dest.lastChanged = source.lastChanged;
        @SuppressWarnings("unchecked")
    	HashMap<String,String> annotationClone = (HashMap<String,String>) source.annotations.clone();
        dest.annotations = annotationClone;
    }

    @SuppressWarnings("unchecked")
	public void copy(Allocatable obj) {
        super.copy((SimpleEntity<Allocatable>)obj);
        copy((AllocatableImpl)obj,this);
    }

    public Allocatable deepClone() {
        AllocatableImpl clone = new AllocatableImpl();
        super.deepClone(clone);
        copy(this,clone);
        return clone;
    }

    public String toString() {
        StringBuffer buf = new StringBuffer();
        buf.append(" [");
        buf.append(super.toString());
        buf.append(",");
        buf.append(super.getVersion());
        buf.append("] ");
        if ( getClassification() != null) {
            buf.append (getClassification().toString()) ;
        } 
        return buf.toString();
    }

}







