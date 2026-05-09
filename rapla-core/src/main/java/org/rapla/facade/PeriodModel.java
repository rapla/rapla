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
package org.rapla.facade;

import org.rapla.components.util.TimeInterval;
import org.rapla.entities.Category;
import org.rapla.entities.domain.Period;
import org.rapla.framework.RaplaException;

import java.util.List;

import java.time.LocalDateTime;
import java.time.LocalDate;
/** ListModel that contains all periods. Updates the list automatically if a period is added, changed or deleted.
 * */
public interface PeriodModel
{
    static Category getPeriodsCategory(Category superCategory) {
        Category category = superCategory.getCategory("periods");
        if ( category == null) {
            category = superCategory.getCategory("timetables");
        }
        return category;
    }

    static PeriodModel getHoliday(RaplaFacade raplaFacade) throws RaplaException {
       PeriodModel model = raplaFacade.getPeriodModelFor("holiday");
       if ( model == null ) {
           model = raplaFacade.getPeriodModelFor("feiertag");
       }
       return model;
    }

    /** returns the first matching period or null if no period matches.*/
    Period getPeriodFor(LocalDateTime date);

    /** {@code LocalDate} variant — date-only. */
    default Period getPeriodFor(java.time.LocalDate date) {
        return getPeriodFor(date == null ? null : date);
    }
    Period getNearestPeriodForDate(LocalDateTime date);
    Period getNearestPeriodForStartDate(LocalDateTime date);
    Period getNearestPeriodForStartDate(TimeInterval interval);
    Period getNearestPeriodForEndDate(LocalDateTime date);

    /** return all matching periods.*/
    List<Period> getPeriodsFor(LocalDateTime date);
    List<Period> getPeriodsFor(TimeInterval interval);

    int getSize();
    Period[] getAllPeriods();


}



