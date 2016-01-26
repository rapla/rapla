package org.rapla.entities.domain.permission.impl;

import org.rapla.entities.Category;
import org.rapla.entities.User;
import org.rapla.entities.domain.Permission;
import org.rapla.entities.domain.Permission.AccessLevel;
import org.rapla.entities.domain.PermissionContainer;
import org.rapla.entities.domain.internal.PermissionImpl;
import org.rapla.entities.domain.permission.PermissionExtension;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.internal.UserImpl;
import org.rapla.inject.Extension;
import org.rapla.storage.PermissionController;

import javax.inject.Inject;
import java.util.Collection;
import java.util.Date;

@Extension(id = "org.rapla.entities.domain.permission.RaplaDefault", provides = PermissionExtension.class)
public class RaplaDefaultPermissionImpl implements PermissionExtension
{

    @Inject
    public RaplaDefaultPermissionImpl()
    {
    }


    @Override
    public boolean hasAccess(PermissionContainer container, User user, Permission.AccessLevel accessLevel, Date start, Date end, Date today,
            boolean checkOnlyToday)
    {
        Iterable<? extends Permission> permissions = container.getPermissionList();
        if (user == null || user.isAdmin())
            return true;

        if (PermissionController.isOwner(container, user))
        {
            return true;
        }

        AccessLevel maxAccessLevel = AccessLevel.DENIED;
        int maxEffectLevel = PermissionImpl.NO_PERMISSION;
        Collection<String> groups = UserImpl.getGroupsIncludingParents(user);
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
    public boolean hasAccess(Classification classification, Attribute attribute, User user, AccessLevel edit)
    {
        return true;
    }
}
