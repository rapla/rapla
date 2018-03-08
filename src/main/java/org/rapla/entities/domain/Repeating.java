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
import jsinterop.annotations.JsType;
import org.rapla.components.util.TimeInterval;

import java.util.Date;
import java.util.Set;

/** Encapsulates the repeating rule for an appointment.
    @see Appointment*/
@JsType
public interface Repeating {
	RepeatingType DAILY = RepeatingType.DAILY;
	RepeatingType WEEKLY = RepeatingType.WEEKLY;
    RepeatingType MONTHLY = RepeatingType.MONTHLY;
    RepeatingType YEARLY = RepeatingType.YEARLY;

    void setInterval(int interval);
    /** returns the number of intervals between two repeatings.
     * That are in the selected context:
     * <ul>
     * <li>For weekly repeatings: Number of weeks.</li>
     * <li>For dayly repeatings: Number of days.</li>
     * </ul>
     */
    int getInterval();
    /** The value returned depends which method was called last.
     *  If <code>setNumber()</code> has been called with a parameter
     *  &gt;=0 <code>fixedNumber()</code> will return true. If
     *  <code>setEnd()</code> has been called
     *  <code>fixedNumber()</code> will return false.
     *  @see #setEnd
     *  @see #setNumber
     */
    boolean isFixedNumber();
    /** Set the end of repeating.
     *  If this value is set to null and the
     *  number is set to -1 the appointment will repeat
     *  forever.
     *  @param end If not null isFixedNumber will return true.
     *  @see #setNumber
     */
    void setEnd(Date end);
    /* @return end of repeating or null if unlimited */
    Date getEnd();
    /** Set a fixed number of repeating.
     * If this value is set to -1
     * and the repeating end is set to null the appointment will
     * repeat forever.
     *  @param number If &gt;=0 isFixedNumber will return true.
     *  @see #setEnd
     *  @see #isFixedNumber
    */
    void setNumber(int number);
    /* @return number of repeating or -1 if it repeats forever. */
    int getNumber();
    /* daily,weekly, monthly */
    RepeatingType getType();
    /* daily,weekly, monthly */
    void setType(RepeatingType type);
    /* exceptions for this repeating. */
    Date[] getExceptions();
    boolean hasExceptions();

    boolean isWeekly();
    boolean isDaily();
    boolean isMonthly();
    boolean isYearly();

    /**  returns the weekdays of weekly repeating, e.g. sunday = 1, saturday = 7
     * */
    Set<Integer> getWeekdays();

    boolean hasDifferentWeekdaySelectedInRepeating();

    void setWeekdays(Set<Integer> weekdays);

    void addException(Date date);
    void removeException(Date date);
    void clearExceptions();

    /** returns the appointment of this repeating.
        @see Appointment
     */
    Appointment getAppointment();

    /** copyReservations the values from another repeating */
    void setFrom(Repeating repeating);

    /** tests if an exception is added for the given date */
    boolean isException(long date);
    Object clone();

    void addExceptions(TimeInterval interval);
}

