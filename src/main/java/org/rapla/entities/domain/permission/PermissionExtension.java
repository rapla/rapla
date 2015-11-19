package org.rapla.entities.domain.permission;

import java.util.Date;

import org.rapla.entities.User;
import org.rapla.entities.domain.Permission;
import org.rapla.entities.domain.Permission.AccessLevel;
import org.rapla.entities.domain.PermissionContainer;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.inject.ExtensionPoint;
import org.rapla.inject.InjectionContext;

@ExtensionPoint(context = InjectionContext.all, id = "org.rapla.entities.domain.Permission")
public interface PermissionExtension
{
    boolean hasAccess(PermissionContainer container, User user, AccessLevel accessLevel);

    boolean hasAccess(Iterable<? extends Permission> permissions, User user, AccessLevel accessLevel, Date start, Date end, Date today, boolean checkOnlyToday);

    boolean hasAccess(Attribute attribute, User user, AccessLevel edit);
}