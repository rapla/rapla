package org.rapla.entities.domain.permission;

import org.rapla.entities.Entity;
import org.rapla.entities.User;
import org.rapla.entities.domain.Permission.AccessLevel;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.inject.ExtensionPoint;
import org.rapla.inject.InjectionContext;

import java.util.Date;

@ExtensionPoint(context = InjectionContext.all, id = "org.rapla.entities.domain.Permission")
public interface PermissionExtension
{
    boolean hasAccess(Entity entity, User user, AccessLevel accessLevel, Date start, Date end, Date today, boolean checkOnlyToday);

    boolean hasAccess(Classification classification, Attribute attribute, User user, AccessLevel edit);
}