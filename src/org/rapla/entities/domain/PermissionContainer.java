/*--------------------------------------------------------------------------*
 | Copyright (C) 2014 Christopher Kohlhaas                                  |
 |                                                                          |
 | This program is free software; you can redistribute it and/or modify     |
 | it under the terms of the GNU General Public License as published by the |
 | Free Software Foundation. A copy of the license has been included with   |
 | these distribution in the COPYING file, if not go to www.fsf.org .       |
 |                                                                          |
 | As a special exception, you are granted the permissions to link this     |
 | program with every library, which license fulfills the Open Source       |
 | Definition as published by the Open Source Initiative (OSI).             |
 *--------------------------------------------------------------------------*/

package org.rapla.entities.domain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.rapla.components.util.DateTools;
import org.rapla.components.util.TimeInterval;
import org.rapla.entities.Category;
import org.rapla.entities.Ownable;
import org.rapla.entities.User;
import org.rapla.entities.domain.Permission.AccessLevel;
import org.rapla.entities.domain.internal.PermissionImpl;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.AttributeType;
import org.rapla.entities.dynamictype.Classifiable;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.ConstraintIds;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.internal.UserImpl;


public interface PermissionContainer extends Ownable 
{
    // adds a permission. Permissions are stored in a hashset so the same permission can't be added twice
    void addPermission( Permission permission );
    
    boolean removePermission( Permission permission );
    
    Permission newPermission();

    Collection<Permission> getPermissionList();
    

    class Util
    {
        static public void addDifferences(Set<Permission> invalidatePermissions, PermissionContainer oldContainer, PermissionContainer newContainer) {
            Collection<Permission> oldPermissions = oldContainer.getPermissionList();
            Collection<Permission> newPermissions = newContainer.getPermissionList();
            addDifferences(invalidatePermissions, oldPermissions, newPermissions);
        }

        
        public static boolean differs(Collection<Permission> oldPermissions, Collection<Permission> newPermissions) {
            HashSet<Permission> set = new HashSet<Permission>();
            addDifferences(set, oldPermissions, newPermissions);
            return set.size() > 0;
        }

        public static void replace(PermissionContainer permissionContainer, Collection<Permission> permissions) {
            Collection<Permission> permissionList = new ArrayList<Permission>(permissionContainer.getPermissionList());
            for (Permission p:permissionList)
            {
                permissionContainer.removePermission(p);
            }                
            for (Permission p:permissions)
            {
                permissionContainer.addPermission( p );
            }
        }
        
        public static void copyPermissions(DynamicType type, PermissionContainer permissionContainer) {
            Collection<Permission> permissionList = type.getPermissionList();
            for ( Permission p:permissionList)
            {
                Permission clone = p.clone();
                Permission.AccessLevel accessLevel = clone.getAccessLevel();
                if (!accessLevel.equals(Permission.CREATE) && !accessLevel.equals(Permission.READ_TYPE))
                {
                    permissionContainer.addPermission( clone);
                }
            }
        }

        static public boolean hasAccess(PermissionContainer container, User user, Permission.AccessLevel accessLevel ) {
            Iterable<? extends Permission> permissions = container.getPermissionList();
            if (isOwner(container, user))
            {
                return true;
            }
            return hasAccess(permissions,user, accessLevel, null, null, null, false);
        }

        /** returns if the user has the permission to read the information and the allocations of this resource.*/
        static public boolean canModify(PermissionContainer container,User user) {
            if ( container instanceof DynamicType)
            {
                return user.isAdmin();
            }
            return hasAccess( container,user, Permission.EDIT);
        }
        
        static public boolean canAdmin(PermissionContainer container,User user) {
            if ( container instanceof DynamicType)
            {
                return user.isAdmin();
            }
            return hasAccess( container,user, Permission.ADMIN);
        }

        /** returns if the user has the permission to modify the allocatable (and also its permission-table).*/
        static public boolean canRead(PermissionContainer container,User user) 
        {
            if ( isOwner(container, user))
            {
                return true;
            }
            if ( container instanceof Classifiable)
            {
                if (!canReadType((Classifiable)container, user))
                {
                    return false;
                }
            }
            if ( container instanceof DynamicType)
            {
                return canRead( (DynamicType) container, user);
            }
            else
            {
                return hasAccess( container,user, Permission.READ );
            }
        }

        private static boolean canReadType(Classifiable classifiable, User user) {
            Classification classification = classifiable.getClassification();
            if ( classification != null)
            {
                DynamicType type = classification.getType();
                return canRead(type, user);
            }
            return true;
        }
        
        public static boolean canCreate(Classifiable classifiable, User user) {
            DynamicType type = classifiable.getClassification().getType();
            return canCreate(type, user);
        }

        public static boolean canCreate(DynamicType type, User user) {
            Collection<Permission> permissionList = type.getPermissionList();
            boolean result = matchesAccessLevel( permissionList, user, Permission.CREATE,Permission.ADMIN);
            return result;
        }
        
        public static boolean canRead(DynamicType type, User user) {
            Collection<Permission> permissionList = type.getPermissionList();
            boolean result = matchesAccessLevel( permissionList, user, Permission.READ_TYPE, Permission.CREATE,Permission.ADMIN);
            return result;
        }
        
        /**
         * 
         * @return NO_PERMISSION if permission does not effect user
         * @return ALL_USER_PERMISSION if permission affects all users 
         * @return USER_PERMISSION if permission specifies the current user
         * @return if the permission affects a users group the depth of the permission group category specified 
         */
        static public int getUserEffect(User user,Permission p, Collection<Category> groups) 
        {
            User pUser = p.getUser();
            Category pGroup = p.getGroup();
            if ( pUser == null  && pGroup == null ) 
            {
                return PermissionImpl.ALL_USER_PERMISSION;
            }
            if ( pUser != null  && user.equals( pUser ) ) 
            {
                return PermissionImpl.USER_PERMISSION;
            } 
            else if ( pGroup != null ) 
            {
                if ( groups.contains(pGroup))
                {
                    return PermissionImpl.GROUP_PERMISSION;
                }
            }
            return PermissionImpl.NO_PERMISSION;
        }

        
        static public TimeInterval getInterval(Iterable<? extends Permission> permissionList,User user,Date today,  Permission.AccessLevel requestedAccessLevel ) {
            if ( user == null || user.isAdmin() )
                return new TimeInterval( null, null);
          
            TimeInterval interval = null;
            int maxEffectLevel = PermissionImpl.NO_PERMISSION;
            Collection<Category> groups = getGroupsIncludingParents( user );
            for ( Permission p:permissionList) 
            {
                int effectLevel = getUserEffect(user,p,groups);
                Permission.AccessLevel accessLevel = p.getAccessLevel();
                if ( effectLevel >= maxEffectLevel && effectLevel > PermissionImpl.NO_PERMISSION && accessLevel.includes( requestedAccessLevel))
                {
                    Date start;
                    Date end;
                    if (accessLevel!= Permission.ADMIN  )
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

        public static boolean hasPermissionToAllocate(User user, Allocatable a) {
            Collection<Category> groups = getGroupsIncludingParents(user);
            for ( Permission p: a.getPermissionList()) {
                if (!affectsUser( user, p, groups ))
                {
                    continue;
                }
                if ( p.getAccessLevel().includes(Permission.ALLOCATE))
                {
                    return true;
                }
            }
            return false;
        }
        
        public static boolean hasPermissionToAllocate( User user, Appointment appointment,Allocatable allocatable, Reservation original, Date today) {
            if ( user.isAdmin()) {
                return true;
            }
            Collection<Category> groups = getGroupsIncludingParents(user);
            
            Date start = appointment.getStart();
            Date end = appointment.getMaxEnd();
            
            for ( Permission p:allocatable.getPermissionList()) 
            {
                Permission.AccessLevel accessLevel = p.getAccessLevel();
                if ( (!affectsUser( user, p, groups )) ||  accessLevel.excludes(Permission.READ)) {
                    continue;
                }
                
                if ( accessLevel ==  Permission.ADMIN)
                {
                    // user has the right to allocate
                    return true;
                }
               
                if ( accessLevel.includes(Permission.ALLOCATE) && p.covers( start, end, today ) ) 
                {
                    return true;
                }
                if ( original == null )
                {
                    continue;
                }
        
                // We must check if the changes of the existing appointment
                // are in a permisable timeframe (That should be allowed)
        
                // 1. check if appointment is old,
                // 2. check if allocatable was already assigned to the appointment
                Appointment originalAppointment = original.findAppointment( appointment );
                if ( originalAppointment == null || !original.hasAllocated( allocatable, originalAppointment))
                {
                    continue;
                }
        
                // 3. check if the appointment has changed during
                // that time
                if ( appointment.matches( originalAppointment ) ) 
                {
                    return true;
                }
                if ( accessLevel.includes(Permission.ALLOCATE ))
                {
                    Date maxTime = DateTools.max(appointment.getMaxEnd(), originalAppointment.getMaxEnd());
                    if (maxTime == null)
                    {
                        maxTime = DateTools.addYears( today, 4);
                    }
        
                    Date minChange = appointment.getFirstDifference( originalAppointment, maxTime );
                    Date maxChange = appointment.getLastDifference( originalAppointment, maxTime );
                    //System.out.println ( "minChange: " + minChange + ", maxChange: " + maxChange );
        
                    if ( p.covers( minChange, maxChange, today ) ) {
                        return true;
                    }
                }
            }
            return false;
        }

        public static boolean canCreateConflicts(Allocatable container, User user) {
            Collection<Permission> permissions = container.getPermissionList();
            if ( !canReadType(container, user))
            {
                return false;
            }
            return hasAccess( permissions,user, Permission.ALLOCATE_CONFLICTS, null, null, null, false);
        }

        /**
         *  Checks if the user is allowed to make an allocation in the passed time.
         * @return
         */
        public static boolean canAllocate(Allocatable container, User user, Date start, Date end, Date today) {
            Collection<Permission> permissions = container.getPermissionList();
            return hasAccess(permissions,user, Permission.ALLOCATE,start, end, today, false);
        }

        /**
         *  Checks if the user is allowed to make an allocation in the future (starting with date today)
         * @param container
         * @param user
         * @param today
         * @return
         */
        public static boolean canAllocate(Allocatable container, User user, Date today) {
            Collection<Permission> permissions = container.getPermissionList();
            if ( !canReadType(container, user))
            {
                return false;
            }
            boolean hasAccess = hasAccess(permissions,user, Permission.ALLOCATE, null, null, today, true);
            if ( !hasAccess )
            {
                return false;
            }
            
            return true;
        }

        static private boolean matchesAccessLevel(Iterable<? extends Permission> permissions, User user, AccessLevel... accessLevels ) {
            if ( user == null || user.isAdmin() )
                return true;
          
            Collection<Category> groups = getGroupsIncludingParents(user);
            for ( Permission p:permissions ) 
            {
                for ( AccessLevel accessLevel:accessLevels)
                {
                    if (p.getAccessLevel() == accessLevel)
                    {
                        int effectLevel = getUserEffect(user, p, groups);
                        if ( effectLevel > PermissionImpl.NO_PERMISSION)
                        {
                            return true;
                        }
                    }
                }
            }
            return false;
        }

        
        static private boolean hasAccess(Iterable<? extends Permission> permissions, User user, Permission.AccessLevel accessLevel, Date start, Date end, Date today, boolean checkOnlyToday ) {
            if ( user == null || user.isAdmin() )
                return true;
          
            AccessLevel maxAccessLevel = AccessLevel.DENIED;
            int maxEffectLevel = PermissionImpl.NO_PERMISSION;
            Collection<Category> groups = getGroupsIncludingParents( user);
            for ( Permission p:permissions ) {
                int effectLevel = getUserEffect(user,p, groups);

                if ( effectLevel >= maxEffectLevel && effectLevel > PermissionImpl.NO_PERMISSION)
                {
                    if ( p.hasTimeLimits() && accessLevel.includes( Permission.ALLOCATE) && today!= null)
                    {
                        if (p.getAccessLevel() != Permission.ADMIN  )
                        {
                            if  ( checkOnlyToday )
                            {
                                if (!((PermissionImpl)p).validInTheFuture(today))
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
                    if ( maxAccessLevel.excludes( p.getAccessLevel()) || effectLevel > maxEffectLevel)
                    {
                        maxAccessLevel = p.getAccessLevel();
                    }
                    maxEffectLevel = effectLevel;
                }
            }
            boolean granted = maxAccessLevel.includes( accessLevel) ;
            return granted;
        }
        
       

        
        static private Collection<Category> getGroupsIncludingParents(User user) {
            Collection<Category> groups = new HashSet<Category>( );
            for ( Category group: ((UserImpl) user).getGroupList())
            {
                groups.add( group);
                Category parent = group.getParent();
                while ( parent != null)
                {
                    if ( parent == group)
                    {
                        throw new IllegalStateException("Parent added to own child");
                    }
                    if (parent == null  || parent.getParent() == null || parent.getKey().equals("user-groups"))
                    {
                        break;
                    }
                    if ( ! groups.contains( parent))
                    {
                        groups.add( parent);
                    }
                    parent = parent.getParent();
                    
                }
            }
            return groups;
        }
        
        
        private static void addDifferences(Set<Permission> invalidatePermissions, Collection<Permission> oldPermissions, Collection<Permission> newPermissions) {
            // we leave this condition for a faster equals check
            int size = oldPermissions.size();
            if  (size == newPermissions.size())
            {
                Iterator<Permission> newPermissionsIt = newPermissions.iterator();
            	for (Permission oldPermission:oldPermissions)
            	{
                    Permission newPermission = newPermissionsIt.next();
            		if (!oldPermission.equals(newPermission))
            		{
            			invalidatePermissions.add( oldPermission);
            			invalidatePermissions.add( newPermission);
            		}
            	}
            }
            else
            {
            	HashSet<Permission> newSet = new HashSet<Permission>(newPermissions);
            	HashSet<Permission> oldSet = new HashSet<Permission>(oldPermissions);
            	{
            		HashSet<Permission> changed = new HashSet<Permission>( newSet);
            		changed.removeAll( oldSet);
            		invalidatePermissions.addAll(changed);
            	}
            	{
            		HashSet<Permission> changed = new HashSet<Permission>(oldSet);
            		changed.removeAll( newSet);
            		invalidatePermissions.addAll(changed);
            	}
            }
        }



        static private boolean affectsUser(User user, Permission p, Collection<Category> groups) {
            int userEffect = getUserEffect( user, p, groups );
            return userEffect> PermissionImpl.NO_PERMISSION;
        }


        static public boolean canReadOnlyInformation(Allocatable classifiable, User user) {
            if ( !canReadType(classifiable, user))
            {
                return false;
            }
            if (isOwner(classifiable, user))
            {
                return true;
            }
            return hasAccess( classifiable,user, Permission.READ_NO_ALLOCATION );
        }


        private static boolean isOwner(Ownable classifiable, User user) {
            User owner = classifiable.getOwner();
            if ( owner != null && owner.equals( user))
            {
                return true;
            }
            return false;
        }


        /** old permission_modify should sync with new permission model for a while or until permission_modify attribute is removed
         * @deprecated 
         * @param entity
         * @param persistant
         */
        @Deprecated
        static public void processOldPermissionModify(Classifiable entity, Classifiable persistant) 
        {
            Classification classification = entity.getClassification();
            if ( classification == null)
            {
                return;
            }
            Attribute attribute = classification.getAttribute("permission_modify"); 
            if ( attribute == null || attribute.getType() != AttributeType.CATEGORY )
            {
                return;
            }
            Collection<Object> newValues = classification.getValues(attribute);
            Collection<Object> oldValues = persistant != null ? persistant.getClassification().getValues(attribute) : Collections.emptyList();
            boolean permissionModifyChanged = !newValues.equals( oldValues);
            PermissionContainer permissionContainer = (PermissionContainer) entity;
            Collection<Permission> newPermissionList = permissionContainer.getPermissionList();
            boolean permissionChanged = persistant != null && differs(newPermissionList, ((PermissionContainer)persistant).getPermissionList());
            // changed permissions take precedence over changed permission_modify
            if ( permissionChanged )
            {
                List<Category> newCategories = new ArrayList<Category>();
                for ( Permission p:newPermissionList)
                {
                    Category group = p.getGroup();
                    Permission.AccessLevel accessLevel = p.getAccessLevel();
                    if (group != null && accessLevel == Permission.AccessLevel.ADMIN)
                    {
                        Object rootCategory = attribute.getConstraint( ConstraintIds.KEY_ROOT_CATEGORY );
                        if ( rootCategory == null )
                        {
                            newCategories.add( group );
                        }
                        else if ( rootCategory instanceof Category)
                        {
                            Category root = (Category) rootCategory;
                            if ( root.isAncestorOf( group ))
                            {
                                newCategories.add( group );
                            }
                        }
                    }
                }
                classification.setValues( attribute, newCategories);
                
            }
            else if ( permissionModifyChanged )
            {
                Set<Category> existingAdminGroups = new HashSet<Category>();
                for ( Permission p:new ArrayList<Permission>(newPermissionList))
                {
                    Category group = p.getGroup();
                    Permission.AccessLevel accessLevel = p.getAccessLevel();
                    if (group != null && accessLevel == Permission.AccessLevel.ADMIN)
                    {
                        // remove permission if not in permission modify group
                        if ( !newValues.contains(group ))
                        {
                            permissionContainer.removePermission( p);
                            continue;
                        }
                        existingAdminGroups.add( group );
        
                    }
                }
                
                for (Object obj:newValues)
                {
                    Category category = (Category) obj;
                    if ( !existingAdminGroups.contains( category ))
                    {
                        Permission permission = permissionContainer.newPermission();
                        permission.setGroup(category );
                        permission.setAccessLevel( Permission.AccessLevel.ADMIN);
                        permissionContainer.addPermission(permission);
                    }
                }	 
        
            }
        
        }


        

        
    }
}
