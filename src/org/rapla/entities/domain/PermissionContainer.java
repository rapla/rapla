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
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.rapla.components.util.TimeInterval;
import org.rapla.entities.Category;
import org.rapla.entities.User;
import org.rapla.entities.domain.Permission.AccessLevel;
import org.rapla.entities.domain.internal.PermissionImpl;
import org.rapla.entities.dynamictype.Classifiable;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.DynamicType;


public interface PermissionContainer 
{
    // adds a permission. Permissions are stored in a hashset so the same permission can't be added twice
    void addPermission( Permission permission );
    
    boolean removePermission( Permission permission );
    
    Permission newPermission();

    Collection<Permission> getPermissionList();
    

    class Util
    {
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
            return hasAccess(permissions,user, accessLevel, null, null, null, false);
        }

        /** returns if the user has the permission to read the information and the allocations of this resource.*/
        static public boolean canModify(PermissionContainer container,User user) {
            return hasAccess( container,user, Permission.EDIT);
        }
        
        static public boolean canAdmin(PermissionContainer container,User user) {
            return hasAccess( container,user, Permission.ADMIN);
        }

        /** returns if the user has the permission to modify the allocatable (and also its permission-table).*/
        static public boolean canRead(PermissionContainer container,User user) 
        {
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

        public static boolean canReadType(Classifiable classifiable, User user) {
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
            boolean result = matchesAccessLevel( permissionList, user, Permission.CREATE);
            return result;
        }
        
        public static boolean canRead(DynamicType type, User user) {
            Collection<Permission> permissionList = type.getPermissionList();
            boolean result = matchesAccessLevel( permissionList, user, Permission.READ_TYPE, Permission.CREATE);
            return result;
        }

        static public boolean matchesAccessLevel(Iterable<? extends Permission> permissions, User user, AccessLevel... accessLevels ) {
            if ( user == null || user.isAdmin() )
                return true;
          
            Collection<Category> groups = getGroupsIncludingParents(user);
            for ( Permission p:permissions ) 
            {
                for ( AccessLevel accessLevel:accessLevels)
                {
                    if (p.getAccessLevel() == accessLevel)
                    {
                        int effectLevel = ((PermissionImpl)p).getUserEffect(user, groups);
                        if ( effectLevel > PermissionImpl.NO_PERMISSION)
                        {
                            return true;
                        }
                    }
                }
            }
            return false;
        }

        
        static public boolean hasAccess(Iterable<? extends Permission> permissions, User user, Permission.AccessLevel accessLevel, Date start, Date end, Date today, boolean checkOnlyToday ) {
            if ( user == null || user.isAdmin() )
                return true;
          
            AccessLevel maxAccessLevel = AccessLevel.DENIED;
            int maxEffectLevel = PermissionImpl.NO_PERMISSION;
            Collection<Category> groups = getGroupsIncludingParents(user);
            for ( Permission p:permissions ) {
                int effectLevel = ((PermissionImpl)p).getUserEffect(user, groups);

                if ( effectLevel >= maxEffectLevel && effectLevel > PermissionImpl.NO_PERMISSION)
                {
                    if ( p.hasTimeLimits() && accessLevel.includes( Permission.ALLOCATE) && today!= null)
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
        
        static public TimeInterval getInterval(Iterable<? extends Permission> permissionList,User user,Date today,  Permission.AccessLevel requestedAccessLevel ) {
            if ( user == null || user.isAdmin() )
                return new TimeInterval( null, null);
          
            TimeInterval interval = null;
            int maxEffectLevel = PermissionImpl.NO_PERMISSION;
            for ( Permission p:permissionList) 
            {
                int effectLevel = ((PermissionImpl)p).getUserEffect(user);
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

        private static Collection<Category> getGroupsIncludingParents(User user) {
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
            return groups;
        }
        
        static public void addDifferences(Set<Permission> invalidatePermissions, PermissionContainer oldContainer, PermissionContainer newContainer) {
            Collection<Permission> oldPermissions = oldContainer.getPermissionList();
            Collection<Permission> newPermissions = newContainer.getPermissionList();
            addDifferences(invalidatePermissions, oldPermissions, newPermissions);
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

        
    }
}
