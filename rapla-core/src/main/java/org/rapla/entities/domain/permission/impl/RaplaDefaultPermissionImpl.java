package org.rapla.entities.domain.permission.impl;

import org.rapla.entities.Category;
import org.rapla.entities.Entity;
import org.rapla.entities.User;
import org.rapla.entities.domain.Permission;
import org.rapla.entities.domain.Permission.AccessLevel;
import org.rapla.entities.domain.PermissionContainer;
import org.rapla.entities.domain.internal.PermissionImpl;
import org.rapla.entities.domain.permission.PermissionExtension;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.internal.UserImpl;
import org.rapla.storage.PermissionController;

import org.springframework.beans.factory.annotation.Autowired;
import java.util.Collection;
import java.time.LocalDateTime;
public class RaplaDefaultPermissionImpl implements PermissionExtension
{

    @Autowired
    public RaplaDefaultPermissionImpl()
    {
    }

    @Override
    public boolean hasAccess(Entity entity, User user, Permission.AccessLevel accessLevel, LocalDateTime start, LocalDateTime end, java.time.LocalDate today,
            boolean checkOnlyToday)
    {
        if (user == null || user.isAdmin())
            return true;

        Class<? extends  Entity> type = entity.getTypeClass();
        if ( type == DynamicType.class)
        {
            return user.isAdmin();
        }
        if ( type == User.class)
        {
            // only admins can set admin flags or edit admins
            if (((User) entity).isAdmin() )
            {
                return false;
            }
            if ( user == null)
            {
                return true;
            }
            User userToEdit = (User) entity;
            if ( userToEdit.isAdmin())
            {
                return false;
            }
            if (PermissionController.canAdminUser( user, userToEdit))
            {
                return true;
            }
        }
        if ( type == Category.class)
        {
            final Collection<Category> adminGroups = PermissionController.getGroupsToAdmin(user);
            Category group = (Category) entity;
            for ( Category adminGroup: adminGroups)
            {
                if ( adminGroup.equals(group) || adminGroup.isAncestorOf( group))
                {
                    return true;
                }
            }
            return false;
        }
        if ( !(entity instanceof PermissionContainer ))
        {
            return true;
        }
        PermissionContainer container = (PermissionContainer) entity;
        if (PermissionController.isOwner(container, user))
        {
            return true;
        }

        // ADR 0003 (revised 2026-06-28) / PRD 090 — purely additive resolution:
        // effective access is the HIGHEST level granted by ANY matching row
        // (user / group / world). No precedence, no subtraction — a DENIED (0)
        // row is the floor and a more-specific lower-level row can never cap a
        // broader grant downward.
        AccessLevel maxAccessLevel = AccessLevel.DENIED;
        Collection<String> groups = UserImpl.getGroupsIncludingParents(user);
        Iterable<? extends Permission> permissions = container.getPermissionList();
        for (Permission p : permissions)
        {
            int effectLevel = PermissionContainer.Util.getUserEffect(user, p, groups);
            if (effectLevel <= PermissionImpl.NO_PERMISSION)
            {
                continue; // row does not match this user
            }
            AccessLevel level = p.getAccessLevel();
            if (maxAccessLevel.includes(level))
            {
                continue; // already dominated — cannot raise the max (also skips DENIED)
            }
            if (p.hasTimeLimits() && (accessLevel.includes(Permission.ALLOCATE) || accessLevel.includes(Permission.REQUEST)) && today != null)
            {
                if (level != Permission.ADMIN)
                {
                    if (checkOnlyToday)
                    {
                        if (!((PermissionImpl) p).validInTheFuture(today))
                        {
                            continue;
                        }
                    }
                    else
                    {
                        if (!p.covers(start, end, today))
                        {
                            continue;
                        }
                    }
                }
            }
            maxAccessLevel = level;
        }
        boolean granted = maxAccessLevel.includes(accessLevel);
        return granted;
    }

    @Override
    public boolean hasAccess(Classification classification, Attribute attribute, User user, AccessLevel edit)
    {
        return true;
    }
}
