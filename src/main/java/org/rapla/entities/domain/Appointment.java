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
import org.rapla.entities.Entity;
import org.rapla.entities.User;
import org.rapla.entities.storage.ReferenceInfo;

import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
/** The basic building blocks of reservations.
    @see Reservation
    @see Repeating*/
public interface Appointment extends Entity<Appointment>, Comparable {
    Date getStart();
    Date getEnd();
    /** <p>
        If no repeating is set this method will return the same
        as <code>getEnd()</code>.
        </p>
        <p>
        If the repeating has no end the method will return <strong>Null</strong>.
        Oterwise the maximum of getEnd() and repeating.getEnd() will be returned.
        </p>
        @see #getEnd
        @see Repeating
    */
    Date getMaxEnd();

    ReferenceInfo<User> getOwnerRef();

    /** returns the reservation that owns the appointment.
    @return the reservation that owns the appointment or null if
    the appointment does not belong to a reservation.
    */
    Reservation getReservation();

    /** @return null if the appointment has no repeating
    */
    Repeating getRepeating();

    /** Enables repeating for this appointment.
        Use getRepeating() to manipulate the repeating. */
    void setRepeatingEnabled(boolean enableRepeating);

    /** returns if the appointment has a repeating */
    boolean isRepeatingEnabled();

    /** Changes the start- and end-time of the appointment.
     */
    void move(Date start,Date end);
    /** Moves the start-time of the appointment.
        The end-time will be adjusted accordingly to the duration of the appointment.
     */
    void moveTo(Date newStart);

    /** Tests two appointments for overlap.
        Important:  Times like 13:00-14:00 and 14:00-15:00 do not overlap
        The overlap-relation must be symmetric <code>a1.overlaps(a2) == a2.overlaps(a1)</code>
        @return true if the appointment overlaps the given appointment.
    */
    boolean overlapsAppointment(Appointment appointment);

	boolean overlapsBlock(AppointmentBlock block);
    
    /** Test for overlap with a period. 
     *  same as overlaps( start, end, true)
      * @return true if the overlaps with the given period.
    */
    boolean overlaps(Date start,Date end);
    
    /** Test for overlap with a period. 
     *  same as overlaps( start, end, true)
      * @return true if the overlaps with the given period.
    */
    boolean overlapsTimeInterval(TimeInterval interval);

    /** Returns if the exceptions, repeatings, start and end dates of the Appoinemnts are the same.*/
    boolean matches(Appointment appointment);

    /** @param maxDate must not be null, specifies the last date that should be searched

    returns the first date at which the two appointments differ (dates after maxDate will not be calculated)
    */
    Date getFirstDifference( Appointment a2, Date maxDate );

    /** @param maxDate must not be null, specifies the last date that should be searched
       
    returns the last date at which the two appointments differ. (dates after maxDate will not be calculated)*/
    Date getLastDifference( Appointment a2, Date maxDate );

    /** this method will be used for future enhancements */
    boolean isWholeDaysSet();

    /** this method will be used for future enhancements */
    void setWholeDays(boolean enable);

    /** adds all Appointment-blocks in the given period to the blocks collection.
        A block is in the period if its starttime&lt;end or its endtime&gt;start. Exceptions are excluded, i.e. there is no block on an exception date. 
     */
    void createBlocks(Date start,Date end,Collection<AppointmentBlock> blocks);
    
    /** adds all Appointment-blocks in the given period to the blocks collection.
    A block is in the period if its starttime&lt;end or its endtime&gt;start. You can specify if exceptions should be excluded. If this is set no blocks are added on an exception date.
    */
    void createBlocks(Date start,Date end,Collection<AppointmentBlock> blocks, boolean excludeExceptions);

    Appointment[] EMPTY_ARRAY = new Appointment[0];
    
    class AppointmentUtil
    {
        static public Map<String,Appointment> idMap(Appointment[] appointments)
        {
            Map<String,Appointment> idMap = new LinkedHashMap<>();
            for (Appointment app: appointments)
            {
                idMap.put( app.getId(), app );
            }
            return idMap;
        }
    }
}












