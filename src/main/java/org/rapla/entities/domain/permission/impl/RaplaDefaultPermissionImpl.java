package org.rapla.entities.domain.permission.impl;

import java.util.Collection;
import java.util.Date;

import javax.inject.Inject;

import org.rapla.entities.Category;
import org.rapla.entities.User;
import org.rapla.entities.domain.Permission;
import org.rapla.entities.domain.Permission.AccessLevel;
import org.rapla.entities.domain.PermissionContainer;
import org.rapla.entities.domain.internal.PermissionImpl;
import org.rapla.entities.domain.permission.PermissionController;
import org.rapla.entities.domain.permission.PermissionExtension;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.inject.Extension;

@Extension(id = "org.rapla.entities.domain.permission.RaplaDefault", provides = PermissionExtension.class)
public class RaplaDefaultPermissionImpl implements PermissionExtension
{
    
    @Inject
    public RaplaDefaultPermissionImpl()
    {
    }

    @Override
    public boolean hasAccess(PermissionContainer container, User user, AccessLevel accessLevel)
    {
        Iterable<? extends Permission> permissions = container.getPermissionList();
        if (PermissionController.isOwner(container, user))
        {
            return true;
        }
        return hasAccess(permissions, user, accessLevel, null, null, null, false);
    }

    @Override
    public boolean hasAccess(Iterable<? extends Permission> permissions, User user, Permission.AccessLevel accessLevel, Date start, Date end, Date today,
            boolean checkOnlyToday)
    {
        if (user == null || user.isAdmin())
            return true;

        AccessLevel maxAccessLevel = AccessLevel.DENIED;
        int maxEffectLevel = PermissionImpl.NO_PERMISSION;
        Collection<Category> groups = PermissionContainer.Util.getGroupsIncludingParents(user);
        for (Permission p : permissions)
        {
            int effectLevel = PermissionContainer.Util.getUserEffect(user, p, groups);

            if (effectLevel >= maxEffectLevel && effectLevel > PermissionImpl.NO_PERMISSION)
            {
                if (p.hasTimeLimits() && accessLevel.includes(Permission.ALLOCATE) && today != null)
                {
                    if (p.getAccessLevel() != Permission.ADMIN)
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
                if (maxAccessLevel.excludes(p.getAccessLevel()) || effectLevel > maxEffectLevel)
                {
                    maxAccessLevel = p.getAccessLevel();
                }
                maxEffectLevel = effectLevel;
            }
        }
        boolean granted = maxAccessLevel.includes(accessLevel);
        return granted;
    }
    
    @Override
    public boolean hasAccess(Attribute attribute, User user, AccessLevel edit)
    {
        return true;
    }

}
