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
package org.rapla.entities.domain.internal;

import java.util.Arrays;
import java.util.Date;
import java.util.Set;
import java.util.TreeSet;

import org.rapla.components.util.Assert;
import org.rapla.components.util.DateTools;
import org.rapla.components.util.DateTools.DateWithoutTimezone;
import org.rapla.entities.ReadOnlyException;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Repeating;
import org.rapla.entities.domain.RepeatingType;

final class RepeatingImpl implements Repeating,java.io.Serializable {
    // Don't forget to increase the serialVersionUID when you change the fields
    private static final long serialVersionUID = 1;
    
    transient private boolean readOnly = false;

    private int interval = 1;
    private boolean isFixedNumber;
    private int number = -1;
    private Date end;
    private RepeatingType repeatingType;
    private Set<Date> exceptions;
    transient private Date[] exceptionArray;
    transient private boolean arrayUpToDate = false;
    transient private Appointment appointment;
    private int frequency;
    boolean monthly;
    boolean yearly;

    RepeatingImpl()
    {
    	if ( repeatingType != null)
    	{
    		setType( repeatingType);
    	}
    }
    
    RepeatingImpl(RepeatingType type,Appointment appointment) {
        setType(type);
        setAppointment(appointment);
        setNumber( 1) ;
    }

    public void setType(RepeatingType repeatingType) {
    	if ( repeatingType == null )
    	{
    		throw new IllegalStateException("Repeating type cannot be null");
    	}
        checkWritable();
        this.repeatingType = repeatingType;
        monthly = false;
        yearly = false;
        if (repeatingType.equals( RepeatingType.WEEKLY ))
        {
            frequency = 7 ;
        }
        else if (repeatingType.equals( RepeatingType.MONTHLY))
        {
            frequency = 7;
            monthly = true;
        } 
        else if (repeatingType.equals( RepeatingType.DAILY))
        {    
            frequency = 1;
        }
        else if (repeatingType.equals( RepeatingType.YEARLY))
        {    
            frequency = 1;
            yearly = true;
        }
        else
        {
            throw new UnsupportedOperationException(" repeatingType " + repeatingType + " not supported");
        }
    }

    public RepeatingType getType() {
        return repeatingType;

    }

    void setAppointment(Appointment appointment) {
        this.appointment = appointment;
    }

    public void setReadOnly() {
        this.readOnly = true;
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    public void checkWritable() {
        if ( readOnly )
            throw new ReadOnlyException( this );
    }

    public Appointment getAppointment() {
        return appointment;
    }

    public void setInterval(int interval) {
        checkWritable();
        if (interval<1)
            interval = 1;
        this.interval = interval;
    }

    public int getInterval() {
        return interval;
    }

    public boolean isFixedNumber() {
        return isFixedNumber;
    }
    
    public boolean isWeekly() {
        return RepeatingType.WEEKLY.equals( getType());
    }
    
    public boolean isDaily() {
        return RepeatingType.DAILY.equals( getType());
    }
    
    public boolean isMonthly() {
        return monthly;
    }

    public boolean isYearly() {
        return yearly;
    }

    public void setEnd(Date end) {
        checkWritable();
        isFixedNumber = false;
        number = -1;
        this.end = end;
    }

    transient Date endTime;
    public Date getEnd() {
        if (!isFixedNumber)
            return end;
        if ( this.appointment == null)
        {
            throw new IllegalStateException("Appointment not set");
        }

        if (endTime == null)
            endTime = new Date();

        if ( number < 0 )
        {
            return null;
        }
        if ( number == 0 )
        {
            return getAppointment().getStart();
        }
        
        if ( !isFixedIntervalLength())
        {
            int counts =  ((number -1) * interval) ;
            Date appointmentStart = appointment.getStart();
            Date newDate = appointmentStart;
            for ( int i=0;i< counts;i++)
            {
                long newTime;
            	if ( monthly)
                {
                    newTime = gotoNextMonth( appointmentStart,newDate);
                }
                else
                {
                    newTime = gotoNextYear( appointmentStart,newDate);
                }
            	newDate = new Date( newTime);
            }
            return newDate;
        }
        else
        {
            long intervalLength = getFixedIntervalLength();
            endTime.setTime(DateTools.fillDate( this.appointment.getStart().getTime()
                                           + (this.number -1)* intervalLength
                                           ));
        }
        return endTime;
    }

    /** returns interval-length in milliseconds.
    @see #getInterval
    */
    public long getFixedIntervalLength() {
        long intervalDays = frequency * interval;
		return intervalDays * DateTools.MILLISECONDS_PER_DAY;
    }

    public void setNumber(int number) {
        checkWritable();
        if (number>-1) {
            isFixedNumber = true;
            this.number = Math.max(number,1);
        } else {
            isFixedNumber = false;
            this.number = -1;
            setEnd(null);
        }

    }

    public boolean isException(long time) {
        if (!hasExceptions())
            return false;

        Date[] exceptions = getExceptions();
        if (exceptions.length == 0) {
            //          System.out.println("no exceptions");
            return false;
        }
        for (int i=0;i<exceptions.length;i++) {
            //System.out.println("Comparing exception " + exceptions[i] + " with " + new Date(time));
            if (exceptions[i].getTime()<=time
                && time<exceptions[i].getTime() + DateTools.MILLISECONDS_PER_DAY) {
                //System.out.println("Exception matched " + exceptions[i]);
                return true;
            }
        }
        return false;
    }

    public int getNumber() {
        if (number>-1)
            return number;
        if (end==null)
            return -1;
        //      System.out.println("End " + end.getTime() + " Start " + appointment.getStart().getTime() + " Duration " + duration);

        if ( isFixedIntervalLength() )
        {
            long duration = end.getTime()
            - DateTools.fillDate(appointment.getStart().getTime());
            if (duration<0)
                return 0;
            long intervalLength = getFixedIntervalLength();
            return (int) ((duration/ intervalLength) + 1);
        }
        else
        {
            Date appointmentStart = appointment.getStart();
            int number = 0;
            Date newDate = appointmentStart;
            do 
            {
                number ++;
                long newTime;
                if ( monthly)
                {
                    newTime = gotoNextMonth(  appointmentStart,newDate);
                }
                else
                {
                    newTime = gotoNextYear(  appointmentStart, newDate);
                }
                newDate  = new Date( newTime);
            }
            while ( newDate.before( end));
            return number;
        }            
            
    }

    public void addException(Date date) {
        checkWritable();
        if ( date == null)
        {
        	return;
        }
        if (exceptions == null)
            exceptions = new TreeSet<Date>();
        exceptions.add(DateTools.cutDate(date));
        arrayUpToDate = false;
    }

    public void removeException(Date date) {
        checkWritable();
        if (exceptions == null)
            return;
        if ( date == null)
        {
        	return;
        }
        exceptions.remove(DateTools.cutDate(date));
        if (exceptions.size()==0)
            exceptions = null;
        arrayUpToDate = false;
    }

    public void clearExceptions() {
        if (exceptions == null)
            return;
        exceptions.clear();
        exceptions = null;
        arrayUpToDate = false;
    }

    public String toString() {
        StringBuffer buf = new StringBuffer();
        buf.append("Repeating type=");
        buf.append(repeatingType);
        buf.append(" interval=");
        buf.append(interval);
        if (isFixedNumber()) {
            buf.append(" number=");
            buf.append(number);
        } else {
            if (end != null) {
                buf.append(" end-date=");
                buf.append(AppointmentImpl.fe(end.getTime()));
            }
        }
        if ( exceptions != null && exceptions.size()>0)
        {
        	buf.append(" exceptions=");
        	boolean first = true;
            for (Date exception:exceptions)
        	{
            	if (!first)
            	{
            		buf.append(", ");
            	}
            	else
            	{
            		first = false;
            	}
            	buf.append(exception);
        	}
        }
        return buf.toString();
    }

	public Object clone()
    {
        RepeatingImpl dest = new RepeatingImpl(repeatingType,appointment);
        RepeatingImpl source = this; 
        copy(source, dest);
        dest.appointment = appointment;
        dest.readOnly = false;// clones are always writable
        return dest;
    }

	private void copy(RepeatingImpl source, RepeatingImpl dest) 
	{
		dest.monthly = source.monthly;
        dest.yearly = source.yearly;
        dest.interval = source.interval;
        dest.isFixedNumber = source.isFixedNumber;
        dest.number = source.number;
        dest.end = source.end;
        dest.interval = source.interval;
        if (source.exceptions != null)
        { 
        	dest.exceptions = new TreeSet<Date>();
        	dest.exceptions.addAll(source.exceptions);
        }
        else
        {
        	dest.exceptions = null;
        }
        
	}
    
    public void setFrom(Repeating repeating)
    {
    	checkWritable();
    	RepeatingImpl dest = this;
		dest.setType(repeating.getType());
    	RepeatingImpl source = (RepeatingImpl)repeating;
		copy( source, dest);
    }

    private static Date[] DATE_ARRAY = new Date[0];
    public Date[] getExceptions() {
        if (!arrayUpToDate) {
            if (exceptions != null) {
                exceptionArray = exceptions.toArray(DATE_ARRAY);
                Arrays.sort(exceptionArray);
            }
            else
                exceptionArray = DATE_ARRAY;
            arrayUpToDate = true;
        }
        return exceptionArray;
    }
    public boolean hasExceptions() {
        return exceptions != null && exceptions.size()>0;
    }

    final public long getIntervalLength( long s )
    {
        if ( isFixedIntervalLength())
        {
            return getFixedIntervalLength();
        }
        Date appointmentStart = appointment.getStart();
        Date startDate = new Date(s);
        long newTime;
        if ( monthly)
        {
            newTime = gotoNextMonth(  appointmentStart,startDate);
        }
        else
        {
            newTime = gotoNextYear(  appointmentStart,startDate);
        }
//        Date newDate = cal.getTime();
//        long newTime = newDate.getTime(); 
        Assert.isTrue( newTime > s );
        return  newTime- s;
        
        // yearly
        
    }

    private long gotoNextMonth(  Date start,Date beginDate )
    {
        int dayofweekinmonth = DateTools.getDayOfWeekInMonth( start);
        Date newDate = DateTools.addWeeks( beginDate, 4);
        while ( DateTools.getDayOfWeekInMonth( newDate) != dayofweekinmonth )
        {
        	newDate = DateTools.addWeeks( newDate, 1);
        }
        return newDate.getTime();
    }

    private long gotoNextYear(  Date start,Date beginDate )
    {
        DateWithoutTimezone dateObj = DateTools.toDate( start.getTime());
		int dayOfMonth = dateObj.day;
		int month = dateObj.month;
		int yearAdd = 1;
		if ( month == 2 && dayOfMonth ==29)
		{
			int startYear = DateTools.toDate( beginDate.getTime()).year;
			while (!DateTools.isLeapYear( startYear + yearAdd ))
			{
				yearAdd++;
			}
		}
		Date newDate = DateTools.addYears( beginDate, yearAdd);
		return newDate.getTime();
        
//        cal.setTime( start);
//        int dayOfMonth = cal.get( Calendar.DAY_OF_MONTH);
//        int month = cal.get( Calendar.MONTH);
//        cal.setTime( beginDate);
//        cal.add( Calendar.YEAR,1);
//        while ( cal.get( Calendar.DAY_OF_MONTH) != dayOfMonth)
//        {
//            cal.add( Calendar.YEAR,1);
//            cal.set( Calendar.MONTH, month);
//            cal.set( Calendar.DAY_OF_MONTH, dayOfMonth);
//        }

    }

    final public boolean isFixedIntervalLength()
    {
        return !monthly &&!yearly;
    }

    
}

