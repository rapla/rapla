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

import org.rapla.components.util.Assert;
import org.rapla.components.util.DateTools;
import org.rapla.components.util.DateTools.DateWithoutTimezone;
import org.rapla.components.util.TimeInterval;
import org.rapla.entities.ReadOnlyException;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.entities.domain.Repeating;
import org.rapla.entities.domain.RepeatingType;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

final class RepeatingImpl implements Repeating,java.io.Serializable {
    // Don't forget to increase the serialVersionUID when you change the fields
    private static final long serialVersionUID = 1;
    
    transient private boolean readOnly = false;

    private int interval = 1;
    private boolean isFixedNumber;
    private int number = -1;
    private LocalDateTime end;
    private RepeatingType repeatingType;
    private Set<LocalDateTime> exceptions;
    private Set<Integer> weekdays;
    transient private LocalDateTime[] exceptionArray;
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
        if (repeatingType!=RepeatingType.WEEKLY )
        {
            weekdays = null;
        }

        if (repeatingType== RepeatingType.WEEKLY )
        {
            frequency = 7 ;
        }
        else if (repeatingType== RepeatingType.MONTHLY)
        {
            frequency = 7;
            monthly = true;
        } 
        else if (repeatingType == RepeatingType.DAILY)
        {    
            frequency = 1;
        }
        else if (repeatingType ==RepeatingType.YEARLY)
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
        return RepeatingType.WEEKLY == getType();
    }
    
    public boolean isDaily() {
        return RepeatingType.DAILY == getType();
    }
    
    public boolean isMonthly() {
        return monthly;
    }

    public boolean isYearly() {
        return yearly;
    }

    public Set<Integer> getWeekdays()
    {
        if (! isWeekly() )
        {
            return Collections.emptySet();
        }
        if ( weekdays != null && !weekdays.isEmpty())
        {
            return weekdays;
        }
        else
        {
            final int weekday = DateTools.getWeekday(getAppointment().getStart());
            return Collections.singleton( weekday);
        }
    }

    @Override
    public boolean hasDifferentWeekdaySelectedInRepeating()
    {
        if ( !isWeekly())
        {
            return false;
        }
        if ( weekdays == null || weekdays.isEmpty())
        {
            return false;
        }
        if ( weekdays.size() >= 2)
        {
            return true;
        }
        final int startingWeekday = DateTools.getWeekday(getAppointment().getStart());
        final Integer first = weekdays.iterator().next();
        boolean differentWeekday = ( first != startingWeekday);
        return differentWeekday;
    }

    public void setWeekdays(Set<Integer> weekdays)
    {
        if ( readOnly )
            throw new ReadOnlyException( this );
        if ( weekdays !=null)
        {
            this.weekdays = new TreeSet<>(weekdays);
        }
        else
        {
            this.weekdays = null;
        }
    }

    @Override
    public void setEnd(LocalDateTime end) {
        checkWritable();
        isFixedNumber = false;
        number = -1;
        this.end = end;
    }

    transient LocalDateTime endTime;
    public LocalDateTime getEnd() {
        if (!isFixedNumber) {
            return end;
        }
        if ( this.appointment == null)
        {
            throw new IllegalStateException("Appointment not set");
        }


        if ( number < 0 )
        {
            return null;
        }
        final LocalDateTime appointmentStart = appointment.getStart();
        if ( number == 0 )
        {
            return appointmentStart;
        }
        java.time.Duration appointmentLength = java.time.Duration.between(appointment.getStart(), appointment.getEnd());

        if ( !isFixedIntervalLength())
        {
            int counts =  ((number -1) * interval) ;
            LocalDateTime newDate = appointmentStart;
            for ( int i=0;i< counts;i++)
            {
                newDate = gotoNextStep( newDate);
            }
            return newDate.plus(appointmentLength);
        }
        else
        {
            long intervalDays = (long) frequency * interval * (this.number - 1);
            return appointmentStart.plusDays(intervalDays).plus(appointmentLength);
        }
    }

    /** returns interval-length in milliseconds.
    @see #getInterval
    */
    public long getFixedIntervalLength() {
        long intervalDays = (long) frequency * interval;
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

        LocalDateTime[] exceptions = getExceptions();
        if (exceptions.length == 0) {
            //          System.out.println("no exceptions");
            return false;
        }
        for (int i=0;i<exceptions.length;i++) {
            long exMillis = DateTools.toMilli(exceptions[i]);
            if (exMillis<=time && time<exMillis + DateTools.MILLISECONDS_PER_DAY) {
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
            java.time.Duration duration = java.time.Duration.between(DateTools.fillDate(appointment.getStart()), end);
            if (duration.isNegative())
                return 0;
            long intervalLength = getFixedIntervalLength();
            return (int) ((duration.toMillis() / intervalLength) + 1);
        }
        else
        {
            LocalDateTime appointmentStart = appointment.getStart();
            int number = 0;
            LocalDateTime newDate = appointmentStart;
            LocalDateTime localEnd   = end;
            do 
            {
                number ++;
                newDate = gotoNextStep( newDate);
            }
            while ( newDate.isBefore( localEnd));
            return number;
        }            
    }

    @Override
    public void addException(LocalDateTime date) {
        checkWritable();
        if ( date == null)
        {
        	return;
        }
        if (exceptions == null)
            exceptions = new TreeSet<>();
        exceptions.add(DateTools.cutDate(date));
        arrayUpToDate = false;
    }

    public void addExceptions(TimeInterval interval) {
        checkWritable();
        if (exceptions == null)
            exceptions = new TreeSet<>();
        final AppointmentImpl appointment = (AppointmentImpl)getAppointment();
        Collection<AppointmentBlock> blocks = new ArrayList<>();
        appointment.createBlocks(interval.getStart(),interval.getEnd(), blocks);
        for (AppointmentBlock appointmentBlock:blocks)
        {
            exceptions.add(DateTools.cutDate(appointmentBlock.getStartDateTime()));
        }
        arrayUpToDate = false;
    }

    @Override
    public void removeException(LocalDateTime date) {
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
                buf.append(AppointmentImpl.fe(DateTools.toMilli(end)));
            }
        }
        if ( exceptions != null && exceptions.size()>0)
        {
        	buf.append(" exceptions=");
        	boolean first = true;
            for (LocalDateTime exception:exceptions)
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
        if ( source.weekdays != null)
        {
            dest.weekdays = new TreeSet<>();
            dest.weekdays.addAll( source.weekdays);
        }
        else
        {
            dest.weekdays = null;
        }

        dest.interval = source.interval;
        if (source.exceptions != null)
        { 
        	dest.exceptions = new TreeSet<>();
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

    private static final LocalDateTime[] DATE_ARRAY = new LocalDateTime[0];
    public LocalDateTime[] getExceptions() {
        if (!arrayUpToDate) {
            if (exceptions != null) {
                LocalDateTime[] arr = new LocalDateTime[exceptions.size()];
                int i = 0;
                for (LocalDateTime e : exceptions) {
                    arr[i++] = e;
                }
                Arrays.sort(arr);
                exceptionArray = arr;
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

    public long getIntervalLength(long s)
    {
        if ( isFixedIntervalLength())
        {
            return getFixedIntervalLength();
        }
        LocalDateTime appointmentStart = DateTools.toLocalDateTime(s);
        LocalDateTime localDateTime = gotoNextStep( appointmentStart);
        long newTime = DateTools.toMilli(localDateTime);
        Assert.isTrue( newTime > s );
        return  newTime- s;
        // yearly
        
    }

    private LocalDateTime gotoNextStep( LocalDateTime startDate)
    {
        LocalDateTime newTime;
        if ( monthly)
        {
            newTime = gotoNextMonth( startDate);
        }
        else if ( yearly)
        {
            newTime = gotoNextYear( startDate );
        }
        else
        {
            newTime = gotoNextWeekday(  startDate);
        }
        return newTime;
    }

    private LocalDateTime gotoNextYear(  LocalDateTime beginDate ) {
        int yearAdd = 1;
        if (beginDate.getMonth().getValue() == 2 && beginDate.getDayOfMonth() == 29) {
            int startYear = beginDate.getYear();
            while (!DateTools.isLeapYear(startYear + yearAdd)) {
                yearAdd++;
            }
        }
        LocalDateTime newDate = beginDate.plusYears( yearAdd );
        return newDate;
    }



        private LocalDateTime gotoNextWeekday( LocalDateTime beginDate )
    {
        if ( weekdays.size() > 1)
        {
            LocalDateTime newDate = beginDate;
            for (int i = 0; i < 7; i++)
            {
                newDate = newDate.plusDays(1);
                DayOfWeek dayOfWeek = newDate.getDayOfWeek();
                Integer weekday = DateTools.mapDateAPIToRapla(dayOfWeek);
                if (weekdays.contains(weekday))
                {
                    return newDate;
                }
            }
        }
        return beginDate.plusDays( 7);
    }

    private LocalDateTime gotoNextMonth(   LocalDateTime beginDate )
    {
        int dayofweekinmonth = DateTools.getDayOfWeekInMonth( beginDate.toLocalDate());
        LocalDateTime newDate = beginDate.plusWeeks(4);
        while ( DateTools.getDayOfWeekInMonth( newDate.toLocalDate()) != dayofweekinmonth )
        {
            newDate = newDate.plusWeeks(1);
        }
        return newDate;
    }



    public boolean isFixedIntervalLength()
    {
        return !monthly &&!yearly && !(weekdays != null && weekdays.size() >1);
    }

    
}

