/*--------------------------------------------------------------------------*
 | Copyright (C) 2006 Gereon Fassbender, Christopher Kohlhaas               |
 |                                                                          |
 | This program is free software; you can redistribute it and/or modify     |
 | it under the terms of the GNU General Public License as published by the |
 | Free Software Foundation. A copy of the license has been included with   |
 | these distribution in the COPYING file, if not go to www.fsf.org         |
 |                                                                          |
 | As a special exception, you are granted the permissions to link this     |
 | program with every library, which license fulfills the Open Source       |
 | Definition as published by the Open Source Initiative (OSI).             |
 *--------------------------------------------------------------------------*/
package org.rapla.entities.domain;

import org.rapla.components.util.TimeInterval;
import org.rapla.entities.*;
import org.rapla.entities.dynamictype.Classifiable;

import java.util.Collection;
import java.util.List;

import java.time.LocalDateTime;
import java.time.LocalDate;
import org.rapla.components.util.DateTools;
/** Objects that implement allocatable can be allocated by reservations.
    @see Reservation
 */
public interface Allocatable extends EntityPermissionContainer<Allocatable>,Named,Classifiable,Ownable,Timestamp, Annotatable {

    static <T extends Entity> boolean isAllocatablesOnly(Collection<T> list) {
        return list.stream().allMatch(entity -> entity.getTypeClass() == Allocatable.class);
    }

    /** Conflicts for this allocatable should be ignored, if this flag is enabled.
     * @deprecated use getAnnotation(IGNORE_CONFLICTS) instead*/
	@Deprecated
    boolean isHoldBackConflicts();
    /** Static empty dummy Array. Mainly for using the toArray() method of the collection interface */
    Allocatable[] ALLOCATABLE_ARRAY = new Allocatable[0];

    /** @deprecated use getPermissionList instead */
    @Deprecated
    Permission[] getPermissions();
    
    /** returns the interval in which the user can allocate the resource. Returns null if the user can't allocate the resource */
    TimeInterval getAllocateInterval( User user, LocalDateTime today);

    /** {@code LocalDate} variant of {@link #getAllocateInterval(User, LocalDateTime)}. UTC. */
    default TimeInterval getAllocateInterval( User user, java.time.LocalDate today) {
        LocalDateTime d = today == null ? null : today.atStartOfDay();
        return getAllocateInterval(user, d);
    }

    /** same as  DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_PERSON.equals(allocatable.getType().getAnnotation(DynamicTypeAnnotations.KEY_CLASSIFICATION_TYPE))
     */
    boolean isPerson();

}












