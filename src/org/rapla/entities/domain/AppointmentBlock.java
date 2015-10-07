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

import java.util.Date;

import org.rapla.components.util.DateTools;



/**
 * This class represents a time block of an appointment.
 * @since Rapla 1.4
 */
public class AppointmentBlock implements Comparable<AppointmentBlock>
{
    long start;
    long end;
    boolean isException;
    private Appointment	appointment;
	
	/**
	 * Basic constructor
	 */
	public AppointmentBlock(long start, long end, Appointment appointment, boolean isException)
	{
		this.start = start;
		this.end = end;
		this.appointment = appointment;
		this.isException = isException;
	}
	
	public AppointmentBlock(Appointment appointment)
	{
		this.start = appointment.getStart().getTime();
		this.end = appointment.getEnd().getTime();
		this.appointment = appointment;
		this.isException = false;
	}
	
	public boolean includes(AppointmentBlock a2)
	{
	    return start <= a2.start  &&  end>= a2.end;
	}
	
	public boolean intersects(AppointmentBlock a2)
    {
        return start < a2.end  &&  end> a2.start;
    }
    
	
	/**
	 * Returns the start date of this block
	 * 
	 * @return Date
	 */
	public long getStart()
	{
		return start;
	}
	
	/**
	 * Returns the end date of this block
	 * 
	 * @return Date
	 */
	public long getEnd()
	{
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
	public int compareTo(AppointmentBlock a2) 
	{
        if ( a2 == this)
        {
            return 0;
        }
        AppointmentBlock a1 =this ;
        if (a2.start > a1.start)
            return -1;
        if (a2.start < a1.start) 
            return 1;
        if (a2.end > a1.end)
            return 1;
        if (a2.end < a1.end)
            return -1;
        int compareTo = appointment.getId().compareTo(a2.appointment.getId());
		return compareTo;
    }
	
	public boolean equals( Object obj)
	{
	    if ( obj == this)
	    {
	        return true;
	    }
	    AppointmentBlock other = (AppointmentBlock) obj;
	    if ( other.start != start || other.end != end)
	    {
	        return false;
	    }
	    return appointment.equals( other.appointment);
	}
	
	@Override
	public int hashCode()
	{
	    if ( appointment == null)
	    {
	        return super.hashCode();
	    }
	    return appointment.hashCode();
	}
	
	
	public String toString()
	{
		final String startDate = DateTools.formatDateTime(new Date(start));
        final String endDate = DateTools.formatDateTime(new Date(end));
        return startDate + " - " + endDate;
	}

    
}
