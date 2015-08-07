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
import java.util.Set;


/** This class contains the configuration options for the calendar views
like Worktimes and dates configuration is done in the calendar option menu.
Hours belonging to the worktime get a different color in the
weekview. This is also the minimum interval that will be used for
printing.<br>

Excluded Days are only visible, when there is an appointment to
display.<br>
 */

public interface CalendarOptions {
	/** return the worktimeStart in hours
	 * @deprecated use {@link #getWorktimeStartMinutes()} instead*/
	@Deprecated
    int getWorktimeStart();
    int getRowsPerHour();
	/** return the worktimeEnd in hours
	 * @deprecated use {@link #getWorktimeEndMinutes()} instead*/
    @Deprecated
    int getWorktimeEnd();
    Set<Integer> getExcludeDays();
    
    int getDaysInWeekview();
	int getFirstDayOfWeek();

    boolean isExceptionsVisible();
    boolean isCompactColumns();
    boolean isResourceColoring();
    boolean isEventColoring();
	int getMinBlockWidth();
    int getWorktimeStartMinutes();
    int getWorktimeEndMinutes();
	boolean isNonFilteredEventsVisible();
	boolean isWorktimeOvernight();
}
