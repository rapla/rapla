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

import org.rapla.components.util.TimeInterval;
import org.rapla.entities.Category;
import org.rapla.entities.Ownable;
import org.rapla.entities.User;
import org.rapla.entities.domain.internal.PermissionImpl;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.AttributeType;
import org.rapla.entities.dynamictype.Classifiable;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.internal.UserImpl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;


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
            HashSet<Permission> set = new HashSet<>();
            addDifferences(set, oldPermissions, newPermissions);
            return set.size() > 0;
        }

        public static void replace(PermissionContainer permissionContainer, Collection<Permission> permissions) {
            Collection<Permission> permissionList = new ArrayList<>(permissionContainer.getPermissionList());
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

        /**
         * 
         * @return NO_PERMISSION if permission does not effect user
         * @return ALL_USER_PERMISSION if permission affects all users 
         * @return USER_PERMISSION if permission specifies the current user
         * @return if the permission affects a users group the depth of the permission group category specified 
         */
        static public int getUserEffect(User user,Permission p, Collection<String> groups)
        {
            String pUserId = p.getUserId();
            String pGroup = ((PermissionImpl)p).getGroupId();
            if ( pUserId == null  && pGroup == null )
            {
                return PermissionImpl.ALL_USER_PERMISSION;
            }
            if ( pUserId != null  && user.getId().equals( pUserId ) )
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

        // TODO check if union is correct
        static public TimeInterval getInterval(Iterable<? extends Permission> permissionList,User user,Date today,  Permission.AccessLevel requestedAccessLevel ) {
            if ( user == null || user.isAdmin() )
                return new TimeInterval( null, null);
          
            TimeInterval interval = null;
            int maxEffectLevel = PermissionImpl.NO_PERMISSION;
            Collection<String> groups = UserImpl.getGroupsIncludingParents(user);
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
            	HashSet<Permission> newSet = new HashSet<>(newPermissions);
            	HashSet<Permission> oldSet = new HashSet<>(oldPermissions);
            	{
            		HashSet<Permission> changed = new HashSet<>(newSet);
            		changed.removeAll( oldSet);
            		invalidatePermissions.addAll(changed);
            	}
            	{
            		HashSet<Permission> changed = new HashSet<>(oldSet);
            		changed.removeAll( newSet);
            		invalidatePermissions.addAll(changed);
            	}
            }
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
                List<Category> newCategories = new ArrayList<>();
                for ( Permission p:newPermissionList)
                {
                    Category group = p.getGroup();
                    Permission.AccessLevel accessLevel = p.getAccessLevel();
                    if (group != null && accessLevel == Permission.AccessLevel.ADMIN)
                    {
                        newCategories.add( group );
                    }
                }
                classification.setValues( attribute, newCategories);
                
            }
            else if ( permissionModifyChanged )
            {
                Set<Category> existingAdminGroups = new HashSet<>();
                for ( Permission p: new ArrayList<>(newPermissionList))
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
