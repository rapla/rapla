package org.rapla.entities.domain.permission;

import org.rapla.entities.Entity;
import org.rapla.entities.User;
import org.rapla.entities.domain.Permission.AccessLevel;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.Classification;

import java.util.Date;


public interface PermissionExtension
{
    boolean hasAccess(Entity entity, User user, AccessLevel accessLevel, Date start, Date end, Date today, boolean checkOnlyToday);

    /** {@code LocalDateTime}/`LocalDate` variant — distinct method name. */
    default boolean hasAccessLocalDateTime(Entity entity, User user, AccessLevel accessLevel, java.time.LocalDateTime start, java.time.LocalDateTime end, java.time.LocalDate today, boolean checkOnlyToday) {
        return hasAccess(entity, user, accessLevel,
            start == null ? null : org.rapla.components.util.DateTools.toDate(start),
            end == null ? null : org.rapla.components.util.DateTools.toDate(end),
            today == null ? null : org.rapla.components.util.DateTools.toDate(today),
            checkOnlyToday);
    }

    boolean hasAccess(Classification classification, Attribute attribute, User user, AccessLevel edit);
}