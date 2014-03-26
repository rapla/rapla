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
import java.util.Date;
import java.util.List;

import org.rapla.entities.domain.Period;

/** ListModel that contains all periods. Updates the list automatically if a period is added, changed or deleted.
 * */
public interface PeriodModel
{
    /** returns the first matching period or null if no period matches.*/
    public Period getPeriodFor(Date date);
    public Period getNearestPeriodForDate(Date date);
    public Period getNearestPeriodForStartDate(Date date);
    public Period getNearestPeriodForStartDate(Date date, Date endDate);
    public Period getNearestPeriodForEndDate(Date date);

    /** return all matching periods.*/
    public List<Period> getPeriodsFor(Date date);
    public int getSize();
    public Period[] getAllPeriods();

}



