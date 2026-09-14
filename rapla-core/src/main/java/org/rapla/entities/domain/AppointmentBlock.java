/*--------------------------------------------------------------------------*
 | Copyright (C) 2011 Christopher Kohlhaas                                  |
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

import java.time.LocalDateTime;
/**
 * This class represents a time block of an appointment.
 *
 * <p>PRD 014 Phase 8 (Group E flip, 2026-05-10): primary storage is now
 * {@link LocalDateTime}. The legacy {@link #getStart()} / {@link #getEnd()}
 * accessors return long-millis via {@link DateTools#toMilli(LocalDateTime)}
 * so view-layer pixel math (HTMLWeekView, SwingMonthView, etc.) keeps
 * working unchanged. New code should prefer {@link #getStartDateTime()} /
 * {@link #getEndDateTime()}.
 *
 * @since Rapla 1.4
 */
public class AppointmentBlock implements Comparable<AppointmentBlock>
{
    LocalDateTime start;
    LocalDateTime end;
    boolean isException;
    private final Appointment	appointment;

	/**
	 * Long-millis constructor (legacy). Retained because processBlocks emission
	 * still has the millis on hand from its inner-loop math.
	 */
	public AppointmentBlock(long start, long end, Appointment appointment, boolean isException)
	{
		this(DateTools.toLocalDateTime(start), DateTools.toLocalDateTime(end), appointment, isException);
	}

	/** LocalDateTime constructor — preferred for new call sites. */
	public AppointmentBlock(LocalDateTime start, LocalDateTime end, Appointment appointment, boolean isException)
	{
		this.start = start;
		this.end = end;
		this.appointment = appointment;
		this.isException = isException;
	}

	static public AppointmentBlock create(Appointment appointment)
	{
		return new AppointmentBlock(appointment);
	}

	protected AppointmentBlock(Appointment appointment)
	{
	    this(appointment.getStart(), appointment.getEnd(), appointment, false);
	}

	public boolean includes(AppointmentBlock a2)
	{
	    return !start.isAfter(a2.start) && !end.isBefore(a2.end);
	}

	public boolean intersects(AppointmentBlock a2)
    {
        return start.isBefore(a2.end) && end.isAfter(a2.start);
    }


	/**
	 * Returns the start date of this block as long-millis (UTC epoch).
	 * Kept on the long-millis API so view-layer pixel math doesn't churn.
	 * For LocalDateTime, use {@link #getStartDateTime()}.
	 */
	public long getStart()
	{
		return DateTools.toMilli(start);
	}

	/** End as long-millis. See {@link #getStart()}. */
	public long getEnd()
	{
		return DateTools.toMilli(end);
	}

	public LocalDateTime getStartDateTime() {
		return start;
	}

	public LocalDateTime getEndDateTime() {
		return end;
	}


	/**
     * Returns if the block is an exception from the appointment rule
     *
     */
    public boolean isException()
    {
        return isException;
    }
	/**
	 * Returns the appointment to which this block belongs
	 *
	 * @return Appointment
	 */
	public Appointment getAppointment()
	{
		return appointment;
	}

	/**
     * This method is used to compare two appointment blocks by their start dates
     */
	public int compareTo(AppointmentBlock other)
	{
        int startCmp = start.compareTo(other.start);
        if (startCmp != 0) return startCmp;
        int endCmp = end.compareTo(other.end);
        if (endCmp != 0) return endCmp;
        if ( other == this)
        {
            return 0;
        }
        @SuppressWarnings("unchecked")
		int compareTo = appointment.compareTo(other.appointment);
		return compareTo;
    }

	public boolean equals( Object obj)
	{
	    if ( obj == this)
	    {
	        return true;
	    }
	    AppointmentBlock other = (AppointmentBlock) obj;
	    if ( !start.equals(other.start) || !end.equals(other.end))
	    {
	        return false;
	    }
	    return appointment.equals( other.appointment);
	}


	public String toString()
	{
        return DateTools.formatDateTime(start) + " - " + DateTools.formatDateTime(end);
	}

	public TimeInterval toInterval()
	{
		return new TimeInterval(start, end);
	}


}
