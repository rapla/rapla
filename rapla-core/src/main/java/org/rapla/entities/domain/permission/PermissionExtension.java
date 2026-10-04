package org.rapla.entities.domain.permission;

import org.rapla.entities.Entity;
import org.rapla.entities.User;
import org.rapla.entities.domain.Permission.AccessLevel;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.Classification;

import java.time.LocalDateTime;
import java.util.Collection;
public interface PermissionExtension
{
    boolean hasAccess(Entity entity, User user, AccessLevel accessLevel, LocalDateTime start, LocalDateTime end, java.time.LocalDate today, boolean checkOnlyToday);

    /** {@code groups} = the user's groups including parents when the caller already has them (operator cache, PRD 129), else null. */
    default boolean hasAccess(Entity entity, User user, AccessLevel accessLevel, LocalDateTime start, LocalDateTime end, java.time.LocalDate today, boolean checkOnlyToday,
            Collection<String> groups)
    {
        return hasAccess(entity, user, accessLevel, start, end, today, checkOnlyToday);
    }

    boolean hasAccess(Classification classification, Attribute attribute, User user, AccessLevel edit);
}