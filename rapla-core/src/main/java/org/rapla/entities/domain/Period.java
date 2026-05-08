/*--------------------------------------------------------------------------*
 | Copyright (C) 2014 Christopher Kohlhaas                                  |
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

import org.rapla.components.util.DateTools;
import org.rapla.components.util.TimeInterval;
import org.rapla.entities.Category;
import org.rapla.entities.Named;
import org.rapla.entities.RaplaObject;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.Set;
/**
Most universities and schools are planning for fixed periods/terms
rather than arbitrary dates. Rapla provides support for this periods.
*/
public interface Period extends RaplaObject<Period>,Comparable<Period>,Named {

    /** {@code java.time} primary: returns the period start as a {@code LocalDate} (UTC midnight). */
    LocalDate getStartAsLocalDate();
    /** {@code java.time} primary: returns the period end as a {@code LocalDate} (UTC midnight). */
    LocalDate getEndAsLocalDate();
    TimeInterval getInterval();
    int getWeeks();
    String getName();
    Set<Category> getCategories();

    /** {@code LocalDateTime} variant of {@link #contains(Date)}. UTC. */
    boolean contains(LocalDateTime dateTime);

    /** Legacy {@code Date} accessor — delegates to {@link #getStartAsLocalDate()}. */
    default Date getStart() {
        LocalDate d = getStartAsLocalDate();
        return d == null ? null : DateTools.toDate(d);
    }
    /** Legacy {@code Date} accessor — delegates to {@link #getEndAsLocalDate()}. */
    default Date getEnd() {
        LocalDate d = getEndAsLocalDate();
        return d == null ? null : DateTools.toDate(d);
    }
    /** Legacy {@code Date} accessor — delegates to {@link #contains(LocalDateTime)}. */
    default boolean contains(Date date) {
        return date != null && contains(DateTools.toLocalDateTime(date));
    }

    String toString();
    Period[] PERIOD_ARRAY = new Period[0];
}








