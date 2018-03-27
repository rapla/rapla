/*--------------------------------------------------------------------------*
 | Copyright (C) 2014 Christopher Kohlhaas                                  |
 |                                                                          |
 | This program is free software; you can redistribute it and/or modify     |
 | it under the terms of the GNU General Public License as published by the |
 | Free Software Foundation. A copy of the license has been included with   |
 | these distribution in the COPYING file, if not go to www.fsf.org .       |
 |                                                                          |
 | As a special exception, you are granted the permissions to link this     |
 | program with every library, which license fulfills the Open Source       |
 | Definition as published by the Open Source Initiative (OSI).             |
 *--------------------------------------------------------------------------*/
package org.rapla.entities.domain.internal;

import org.rapla.components.util.Assert;
import org.rapla.components.util.DateTools;
import org.rapla.components.util.TimeInterval;
import org.rapla.entities.User;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.entities.domain.AppointmentStartComparator;
import org.rapla.entities.domain.Repeating;
import org.rapla.entities.domain.RepeatingType;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.entities.storage.internal.SimpleEntity;
import org.rapla.facade.RaplaComponent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

public final class AppointmentImpl extends SimpleEntity implements Appointment
{
	private Date start;
    private Date end;
    private RepeatingImpl repeating;
    private boolean isWholeDaysSet = false;
    /** set DE (DebugDisabled) to false for debuging output. You must change in code
        because this flag is final for efficience reasons.*/
    public final static boolean DE = true;
    public final static String BUG = null;
    public static String DD = null;

    @Override public Class<Appointment> getTypeClass()
    {
        return Appointment.class;
    }

    transient ReservationImpl parent;
    
    
    public AppointmentImpl() {
    }

    public AppointmentImpl(Date start,Date end) {
        this.start = start;
        this.end = end;
        if ( start != null && end!= null && DateTools.cutDate( start ).equals( start) && DateTools.cutDate( end).equals(end))
        {
            isWholeDaysSet = true;
        }
    }

    public AppointmentImpl(Date start,Date end, RepeatingType type, int repeatingDuration) {
        this(start,end);
        this.repeating = new RepeatingImpl(type,this);
        repeating.setAppointment( this );
        repeating.setNumber(repeatingDuration);
    }
    

    public void setParent(ReservationImpl parent) {
    	this.parent = parent;
    	if (repeating != null)
    	{
    		repeating.setAppointment( this );
    	}
    }

    public void removeParent()
    {
    	this.parent = null;
    }

    public Date getStart() { return start;}
    public Date getEnd() { return end;}

    public void setReadOnly() {
        super.setReadOnly( );
        if ( repeating != null )
            repeating.setReadOnly(  );
    }

    public void moveTo(Date newStart) {
        long diff = this.end.getTime() - this.start.getTime();
        move(newStart, new Date(newStart.getTime() + diff));
    }

    public void move(Date start,Date end) {
        checkWritable();
        this.start = start;
        this.end = end;
        if ( isWholeDaysSet)
        {
            if (start.getTime() != DateTools.cutDate(start.getTime()) || end.getTime() != DateTools.cutDate(end.getTime()))
            {
                isWholeDaysSet = false;
            }
        }
    }

    public String toString() {
        if (start != null && end != null)
            return f(start.getTime(),end.getTime()) +
                ((repeating != null) ? (" [" + repeating.toString()) + "]": "");
        else
            return start + "-" + end;
    }

    public Reservation getReservation() {
        return parent;
    }

    public boolean isWholeDaysSet() {
        return isWholeDaysSet;
    }
    
    @Override
    public Iterable<ReferenceInfo> getReferenceInfo() {
        return Collections.emptyList();
    }


    public void setWholeDays(boolean enable) {
        checkWritable();
        if (enable) {
        	long cutStartTime = DateTools.cutDate(start.getTime());
            if (start.getTime() != cutStartTime)
            {	
                this.start = new Date(cutStartTime);
            }
            long cutEndTime = DateTools.cutDate(end.getTime());
			if (end.getTime() != cutEndTime)
			{
                this.end = DateTools.fillDate(this.end);
			}
            if ( end.getTime() <= start.getTime())
            {
                this.end = DateTools.fillDate(this.start);
            }
            if ( repeating != null && repeating.getType() == RepeatingType.DAILY)
            {
            	this.end = DateTools.fillDate( this.start);
            }
        }
        isWholeDaysSet  = enable;
    }

    public int compareTo(Appointment a2) {
        Date start2 = a2.getStart();
        Date end2 = a2.getEnd();
        if (start.before( start2))
            return -1;
        if (start.after( start2))
            return 1;
        if (getEnd().before( end2))
            return -1;
        if (getEnd().after( end2))
            return 1;
        if ( a2 == this)
        {
            return 0;
        }
        
        Comparable id1 =  getId();
        Comparable id2 = a2.getId();
        if ( id1 == null || id2 == null)
        {
            return hashCode() < a2.hashCode() ? -1 : 1;
        }
        @SuppressWarnings("unchecked")
		int compareTo = id1.compareTo( id2);
		return compareTo;
    }

    transient Date maxDate;

    /** returns the largest date that covers the appointment
        and null if the appointments repeats forever.
    */
    public Date getMaxEnd() {
        long end = (this.end!= null) ? this.end.getTime():0;
        Repeating repeating = getRepeating();
        if  (repeating != null)
            if (repeating.getEnd() != null)
                end = Math.max(end
                               ,repeating.getEnd().getTime());
            else
                end = 0;
        if (end == 0)
            return null;

        // cache max date object
        if (maxDate == null || maxDate.getTime() != end)
            maxDate = new Date(end);
        return maxDate;
    }

    public RepeatingImpl getRepeating() {
        if ( repeating != null && repeating.getAppointment() == null)
        {
            repeating.setAppointment( this);
        }
        return repeating;
    }

    public void setRepeatingEnabled(boolean enableRepeating) {
        checkWritable();
        if (this.repeating == null) {
            if (enableRepeating) {
                this.repeating = new RepeatingImpl(Repeating.WEEKLY,this);
                this.repeating.setAppointment( this);
            }
        } else {
            if (!enableRepeating) {
                this.repeating = null;
            }
        }
    }

    public boolean isRepeatingEnabled() {
        return repeating != null;
    }

    public Date getFirstDifference( Appointment a2, Date maxDate ) {
        List<AppointmentBlock> blocks1 = new ArrayList<>();
        createBlocks( start, maxDate, blocks1);
        List<AppointmentBlock> blocks2 = new ArrayList<>();
        a2.createBlocks(a2.getStart(), maxDate, blocks2);
        //        System.out.println("block sizes " + blocks1.size() + ", " + blocks2.size() );
        int i=0;
        for ( AppointmentBlock block:blocks1) {
            long a1Start = block.getStart();
            long a1End = block.getEnd();
            if ( i >= blocks2.size() ) {
                return new Date( a1Start );
            }
            long a2Start = blocks2.get( i ).getStart();
            long a2End = blocks2.get( i ).getEnd();
            //System.out.println("a1Start " + a1Start + " a1End " + a1End);
            //System.out.println("a2Start " + a2Start + " a2End " + a2End);
            if ( a1Start != a2Start )
                return new Date( Math.min ( a1Start, a2Start ) );

            if ( a1End != a2End )
                return new Date( Math.min ( a1End, a2End ) );
            i++;
        }
        if ( blocks2.size() > blocks1.size() ) {
            return new Date( blocks2.get( blocks1.size() ).getStart() );
        }
        return null;
    }

    public Date getLastDifference( Appointment a2, Date maxDate ) {
        List<AppointmentBlock> blocks1 = new ArrayList<>();
        createBlocks( start, maxDate, blocks1);
        List<AppointmentBlock> blocks2 = new ArrayList<>();
        a2.createBlocks(a2.getStart(), maxDate, blocks2);
        if ( blocks2.size() > blocks1.size() ) {
            return new Date( blocks2.get( blocks1.size() ).getEnd() );
        }
        if ( blocks1.size() > blocks2.size() ) {
            return new Date( blocks1.get( blocks2.size() ).getEnd() );
        }
        for ( int i = blocks1.size() - 1 ; i >= 0; i-- ) {
            long a1Start = blocks1.get( i ).getStart();
            long a1End = blocks1.get( i ).getEnd();
            long a2Start = blocks2.get( i ).getStart();
            long a2End = blocks2.get( i ).getEnd();
            if ( a1End != a2End )
                return new Date( Math.max ( a1End, a2End ) );

            if ( a1Start != a2Start )
                return new Date( Math.max ( a1Start, a2Start ) );
        }
        return null;
    }

    public boolean matches(Appointment a2) {
        if (!equalsOrBothNull(this.start, a2.getStart()))
            return false;
        
        if (!equalsOrBothNull(this.end, a2.getEnd()))
            return false;
        
        Repeating r1 = getRepeating();
        Repeating r2 = a2.getRepeating();

        // No repeatings. The two appointments match
        if (r1 == null && r2 == null) {
            return true;
        } else if (r1 == null || r2 == null) {
            // one repeating is null the other not so the appointments don't match
            return false;
        }

        if (!r1.getType().equals(r2.getType()))
            return false;

        if (r1.getInterval() != r2.getInterval())
            return false;

        if (!equalsOrBothNull(r1.getEnd(), r2.getEnd()))
            return false;

        // The repeatings match regulary, so we must test the exceptions
        Date[] e1 = r1.getExceptions();
        Date[] e2 = r2.getExceptions();
        if (e1.length != e2.length) {
            //System.out.println("Exception-length don't match");
            return false;
        }

        for (int i=0;i<e1.length;i++)
            if (!e1[i].equals(e2[i])) {
                //System.out.println("Exception " + e1[i] + " doesn't match " + e2[i]);
                return false;
            }

        // Even the repeatings match, so  we can return true
        return true;
    }

    public void createBlocks(Date start,Date end,Collection<AppointmentBlock> blocks) {
        boolean excludeExceptions = true;
        createBlocks(start,end, blocks, excludeExceptions);
    }


    public void createBlocksExcludeExceptions(Date start,Date end,Collection<AppointmentBlock> blocks)
    {
        createBlocks(start, end, blocks, true);
    }


    public void createBlocks(Date start,Date end,Collection<AppointmentBlock> blocks, boolean excludeExceptions) {
        Assert.notNull(blocks);
        Assert.notNull(start,"You must set a startDate");
        Assert.notNull(end, "You must set an endDate");
        processBlocks(start.getTime(), end.getTime(), blocks, excludeExceptions);
    }
    

    /* returns true if there is at least one block in an array. If the passed blocks array is not null it will contain all blocks
     * that overlap the start,end period after a call.*/
    private boolean processBlocks(long start,long end,Collection<AppointmentBlock> blocks, boolean excludeExceptions) {
        long c1 = start;
        long c2 = end;
        long s = this.start.getTime();
        long e = this.end.getTime();
        RepeatingImpl repeating = getRepeating();
        // if there is no repeating
        if (repeating==null) {
            if (s <c2 && e>c1) {
                // check only
                if ( blocks == null )
                {
                	return true;
                }
                else
                {
                    AppointmentBlock block = new AppointmentBlock(s,e,this, false);
                    blocks.add(block);
                }
            } 
            return false;
        }

        DD=DE?BUG: print("s = appointmentstart, e = appointmentend, c1 = intervalstart c2 = intervalend");
        DD=DE?BUG: print("s:" + n(s) + " e:" + n(e) + " c2:" + n(c2) + " c1:" + n(c1));
        if (s <c2 && e>c1  && (!repeating.isException(s) || !excludeExceptions)) {
            // check only
            if ( blocks == null) 
            {
                return true;
            } 
            else 
            {
                AppointmentBlock block = new AppointmentBlock(s,e,this, repeating.isException(s));
                blocks.add(block);
            }
        }
        
        long l = repeating.getIntervalLength( s  );
        //System.out.println( "l in days " + l / DateTools.MILLISECONDS_PER_DAY );
        Assert.isTrue(l>0);
        long timeFromStart = l ;
        if ( repeating.isFixedIntervalLength())
        {
            timeFromStart = Math.max(l,((c1-e) / l)* l);
        }
        int maxNumber = repeating.getNumber();
        long maxEnding = Long.MAX_VALUE;
        if ( maxNumber >= 0)
        {
            Date end2 = repeating.getEnd();
            maxEnding = end2.getTime();
        }
        
        DD=DE?BUG: print("l = repeatingInterval (in minutes), x = stepcount");
        DD=DE?BUG: print("Maxend " + f( maxEnding));
        long currentPos = s + timeFromStart;
        DD=DE?BUG: print( " currentPos:" + n(currentPos) + " c2-s:" + n(c2-s) + " c1-e:" + n(c1-e));
        long blockLength = Math.max(0, e - s);
        while (currentPos <= c2 && (maxNumber<0 || (currentPos<=maxEnding ))) {
            DD=DE?BUG: print(" current pos:" + f(currentPos));
            if (( currentPos + blockLength > c1  )  && ( currentPos < c2 ) && (( end!=DateTools.cutDate(end) || !repeating.isDaily() || currentPos < maxEnding))) {
                boolean isException =repeating.isException( currentPos ); 
                if ((!isException || !excludeExceptions)) {
                    // check only
                    if ( blocks == null ) 
                    {
                        return true;
                    } 
                    else 
                    {
                        AppointmentBlock block = new AppointmentBlock(currentPos,currentPos + blockLength,this, isException);
                        blocks.add( block);
                    }
                }
            }
            currentPos += repeating.getIntervalLength( currentPos) ;
            
        }
        return false;
    }
    
    public boolean overlaps(Date start,Date end) {
        return overlaps( start, end , true );
    }
    
    public boolean overlapsBlock(AppointmentBlock block)
    {
    	long end = block.getEnd();
		long start = block.getStart();
		return overlaps(start, end, true);
    }
    
    
	public boolean overlapsTimeInterval(TimeInterval interval) {
		if ( interval == null)
		{
			return false;
		}
		return overlaps( interval.getStart(), interval.getEnd());
	}


    /** Test for overlap with a period. You can specify if exceptions should be considered in the overlapping algorithm.
     * if excludeExceptions is set an overlap will return false if all dates are excluded by exceptions in the specfied start-end intervall
     @return true if the overlaps with the given period.
     */
    public boolean overlaps(Date start2,Date end2, boolean excludeExceptions) {
        if (start2 == null && end2 == null)
            return true;
        if (start2 == null)
            start2 = this.start;
        if (end2 == null)
        {
            // there must be an overlapp because there can't be infinity exceptions
            if (getMaxEnd() == null)
                return true;
            end2 = getMaxEnd();
        }

        if (getMaxEnd() != null && !start2.before(getMaxEnd()))
            return false;

        if (!this.start.before(end2))
            return false;

        boolean overlaps  = processBlocks( start2.getTime(), end2.getTime(), null,  excludeExceptions );
        return overlaps;
    }
    
    public boolean overlaps(long start,long end, boolean excludeExceptions) {
        if (getMaxEnd() != null && getMaxEnd().getTime()<start)
            return false;

        if (this.start.getTime() > end)
            return false;

        boolean overlaps  = processBlocks( start, end, null,  excludeExceptions );
        return overlaps;
    }

    private static Date getOverlappingEnd(Repeating r1,Repeating r2) {
        Date maxEnd = null;
        if (r1.getEnd() != null)
            maxEnd = r1.getEnd();
        if (r2.getEnd() != null)
            if (maxEnd != null && r2.getEnd().before(maxEnd))
                maxEnd = r2.getEnd();
        return maxEnd;
    }
    
    public boolean overlapsAppointment(Appointment a2) {
        if ( a2 == this)
            return true;
        Date start2 =a2.getStart(); 
        Date end2 =a2.getEnd(); 
        long s1 = this.start.getTime();
        long s2 = start2.getTime();
        long e1 = this.end.getTime();
        long e2 = a2.getEnd().getTime();
        RepeatingImpl r1 = getRepeating();
        RepeatingImpl r2 = (RepeatingImpl)a2.getRepeating();
        DD=DE?BUG: print("Testing overlap of");
        DD=DE?BUG: print(" A1: " + toString());
        DD=DE?BUG: print(" A2: " + a2.toString());

        if (r1 == null && r2 == null) {
            return !(e2 <= s1 || e1 <= s2);
        }
        if (r1 == null) {
            return a2.overlaps(this.start,this.end);
        }
        if (r2 == null) {
            return overlaps(start2,end2);
        }

        // So both appointments have a repeating

        // If r2 has no exceptions we can check if a1 overlaps the first appointment of a2
        if (overlaps(start2,end2) && !r2.isException(start2.getTime())) {
            DD=DE?BUG: print("Primitive overlap for " + getReservation() + " with " + a2.getReservation());
            return true;
        }

        // Check if appointments could overlap because of the end-dates of an repeating
        Date end = getOverlappingEnd(r1,r2);
        if (end != null && (end.getTime()<=s1 || end.getTime()<=s2))
            return false;
        end = getOverlappingEnd(r2,r1);
        if (end != null && (end.getTime()<=s1 || end.getTime()<=s2))
        // We cant compare the fixed interval length here so we have to compare the blocks
        if ( !r1.isFixedIntervalLength())
        {
            return overlapsHard( (AppointmentImpl)a2);
        }
        if ( !r2.isFixedIntervalLength())
        {
            return ((AppointmentImpl)a2).overlapsHard( this);
        }
        // O.K. we found 2 Candidates for the hard way
        long l1 = r1.getFixedIntervalLength();
        long l2 = r2.getFixedIntervalLength();
        // The greatest common divider of the two intervals
        long gcd = gcd(l1,l2);
        long startx1 = Math.max(0,(s2-e1))/l1;
        long startx2 = Math.max(0,(s1-e2))/l2;

        DD=DE?BUG: print("l? = intervalsize for A?, x? = stepcount for A? ");
        long max_x1 = l2/gcd + startx1;
        if (end!= null && (end.getTime()-s1)/l1 + startx1 < max_x1)
            max_x1 = (end.getTime()-s1)/l1 + startx1;
        long max_x2 = l1/gcd + startx2;
        if (end!= null && (end.getTime()-s2)/l2 + startx2 < max_x2)
            max_x2 = (end.getTime()-s2)/l2 + startx2;
        long x1 =startx1;
        long x2 =startx2;

        DD=DE?BUG: print(
              "l1: " + n(l1)
              + " l2: " + n(l2)
              + " gcd: " + n(gcd)
              + " start_x1: " + startx1
              + " start_x2: " + startx2
              + " max_x1: " + max_x1
              + " max_x2: " + max_x2
              );
        boolean overlaps = false;
        long current1 = x1 *l1;
        long current2 = x2 *l2;
        long maxEnd1 = max_x1*l1;
        long maxEnd2 = max_x2*l2;
        
        while (current1<=maxEnd1 && current2<=maxEnd2) {
        //    DD=DE?BUG: print("x1: " + x1 + " x2:" + x2);
            DD=DE?BUG: print(" A1: " + f(s1 + current1, e1 + current1));
            DD=DE?BUG: print(" A2: " + f(s2 + current2, e2 + current2));
            if ((s1 + current1) < (e2 + current2) &&
                (e1 + current1) > (s2 + current2)) {
                if (!hasExceptionForEveryPossibleCollisionInInterval(s1 + current1,s2 + current2,r2)) {
                    overlaps = true;
                    break;
                }
            }
            if ((s1 + current1) < (s2 + current2))
                current1+=l1;
            else
                current2+=l2;
        }
        if (overlaps)
            DD=DE?BUG: print("Appointments overlap");
        else
            DD=DE?BUG: print("Appointments don't overlap");
        return overlaps;
    }

    // check every block in the appointment
    private boolean overlapsHard( AppointmentImpl a2 )
    {
        Repeating r2 = a2.getRepeating();
        Collection<AppointmentBlock> array = new ArrayList<>();
        Date maxEnd =r2.getEnd();
        // overlaps will be checked two  250 weeks (5 years) from now on
        long maxCheck = System.currentTimeMillis() + DateTools.MILLISECONDS_PER_WEEK * 250;
        if ( maxEnd == null || maxEnd.getTime() > maxCheck)
        {
        	maxEnd = new Date(maxCheck); 
        }
        createBlocks( getStart(), maxEnd, array);
        for ( AppointmentBlock block:array)
        {
            long start = block.getStart();
            long end = block.getEnd();
            if (a2.overlaps( start, end, true))
            {
                return true;
            }
        }
        return false;
    }

    /** the greatest common divider of a and b (Euklids Algorithm) */
    public static long gcd(long a,long b){
        return (b == 0) ? a : gcd(b, a%b);
    }


    /* Prueft im Abstand von "gap" millisekunden das Intervall von start bis ende
       auf Ausnahmen. Gibt es fuer einen Punkt keine Ausnahme wird false zurueckgeliefert.
    */
    private boolean hasExceptionForEveryPossibleCollisionInInterval(long s1,long s2,RepeatingImpl r2) {
        RepeatingImpl r1 = getRepeating();
        Date end= getOverlappingEnd(r1,r2);
        if (end == null)
            return false;

        if ((!r1.hasExceptions() && !r2.hasExceptions()))
            return false;

        long l1 = r1.getFixedIntervalLength();
        long l2 = r2.getFixedIntervalLength();
        long gap = (l1 * l2) / gcd(l1,l2);
        Date[] exceptions1 = r1.getExceptions();
        Date[] exceptions2 = r2.getExceptions();
        DD=DE?BUG: print(" Testing Exceptions for overlapp " + f(s1) + " with " + f(s2) + " gap " + n(gap));
        int i1 = 0;
        int i2 = 0;
        long x = 0;
        if (exceptions1.length>i1)
            DD=DE?BUG: print("Exception a1: " + fe(exceptions1[i1].getTime()));
        if (exceptions2.length>i2)
            DD=DE?BUG: print("Exception a2: " + fe(exceptions2[i2].getTime()));
        long exceptionTime1 = 0;
        long exceptionTime2 = 0;
  
        while (s1 + x * gap < end.getTime()) {
            DD=DE?BUG: print("Looking for exception for gap " + x + " s1: " + fe(s1+x*gap) + " s2: " + fe(s2+x*gap));
            long pos1 = s1 + x*gap;
            long pos2 = s2 + x*gap;
           
            // Find first exception from app1 that matches gap
            while (i1<exceptions1.length)
            {
               	exceptionTime1=exceptions1[i1].getTime();
            	if ( exceptionTime1  >= pos1)
            	{
            		DD=DE?BUG: print("Exception  a1: " + fe(exceptionTime1));
            		break;
            	}
            	i1 ++;            	
            }

            // Find first exception from app2 that matches gap
            while (i2<exceptions2.length)
            {
            	exceptionTime2 = exceptions2[i2].getTime();
            	if ( exceptionTime2 >= pos1)
            	{
            		DD=DE?BUG: print("Exception a2: " + fe(exceptionTime2));
            		break;
            	}
            	i2 ++;
            }
            
            boolean matches1 = false;
            if (pos1 >= exceptionTime1 && pos1<= exceptionTime1 +  DateTools.MILLISECONDS_PER_DAY) 
            {
            	DD=DE?BUG: print("Exception from a1 matches!");
            	matches1=true;
            }
            
            boolean matches2 = false;
    	    if ((pos2 >= exceptionTime2  && pos2 <= exceptionTime2 + DateTools.MILLISECONDS_PER_DAY)) 
            { 
    	    	DD=DE?BUG: print("Exception from a2 matches!");
                matches2=true;
            }     	    
  
    	    if (!matches1 && !matches2)
            {
    	    	DD=DE?BUG: print("No matching exception found at date " + fe(pos1) + " or " + fe(pos2) );
                return false;
            }
            DD=DE?BUG: print("Exception found for gap " + x);
            x ++;
        }
        DD=DE?BUG: print("Exceptions found for every gap. No overlapping. ");
        return true;
    }

    private static String print(String string) {
        if (string != null)
            System.out.println(string);
        return string;
    }

    /* cuts the milliseconds and seconds. Usefull for debugging output.*/
    private long n(long n) {
        return n / (1000 * 60);
    }

    /* Formats milliseconds as date. Usefull for debugging output.*/
    static String f(long n) {
        return DateTools.formatDateTime(new Date(n));
    }

    /* Formats milliseconds as date without time. Usefull for debugging output.*/
    static String fe(long n) {
        return DateTools.formatDate(new Date(n));
    }

    /* Formats 2 dates in milliseconds as appointment. Usefull for debugging output.*/
    static String f(long s,long e) {
        Date start = new Date(s);
        Date end = new Date(e);
        if (DateTools.isSameDay(s,e)) {
            return DateTools.formatDateTime(start) + "-" +  DateTools.formatTime(end);
        } else {
            return DateTools.formatDateTime(start) + "-" +  DateTools.formatDateTime(end);
        }
    }

    static private void copy(AppointmentImpl source,AppointmentImpl dest) {
        dest.isWholeDaysSet = source.isWholeDaysSet;
        dest.start = source.start;
        dest.end = source.end;
        dest.repeating = (RepeatingImpl)  ((source.repeating != null) ?
                                           source.repeating.clone()
                                           : null);
        if (dest.repeating != null)
            dest.repeating.setAppointment(dest);
        dest.parent = source.parent;
    }

    
	public void copy(Object obj) {
    	synchronized ( this) {
            AppointmentImpl casted = (AppointmentImpl) obj;
			copy(casted,this);			
		}
    }


    public AppointmentImpl clone() {
        AppointmentImpl clone = new AppointmentImpl();
        super.deepClone(clone);
        copy(this,clone);
        return clone;
    }
    
    /**
     * @param sortedAppointmentList the list of appointments to be searched
     * @param user the owner of the reservation
     * @param start
     * @param end
     * @param excludeExceptions
     * @return
     */
    static public SortedSet<Appointment> getAppointments(SortedSet<Appointment> sortedAppointmentList,User user,Date start,Date end, boolean excludeExceptions) {
	    SortedSet<Appointment> appointmentSet = new TreeSet<>(new AppointmentStartComparator());
	    Iterator<Appointment> it;
		if (end != null) {
	        // all appointments that start before the enddate
	        AppointmentImpl compareElement = new AppointmentImpl(end, end);
			compareElement.setId("DUMMYID");
	        SortedSet<Appointment> headSet = sortedAppointmentList.headSet(compareElement);
            it = headSet.iterator();
	        //it = appointments.values().iterator();
	    } else {
	        it = sortedAppointmentList.iterator();
	    }
	
	    while (it.hasNext()) {
	        AppointmentImpl appointment = (AppointmentImpl) it.next();
	        // test if appointment end before the start-date
	        if (end != null && appointment.getStart().after(end))
	            break;
	
	        // Ignore appointments without a reservation
	        if ( appointment.getReservation() == null)
	            continue;
	
	        if ( !appointment.overlaps(start,end, excludeExceptions))
	            continue;
	        if (user == null || user.getReference().equals(appointment.getOwnerRef()) ) {
	            appointmentSet.add(appointment);
	        }
	    }
	    return appointmentSet;
	}
	
	public static Set<Appointment> getConflictingAppointments(SortedSet<Appointment> appointmentSet, Appointment appointment, Collection<Reservation> ignoreList, boolean onlyFirstConflictingAppointment) {
		Set<Appointment> conflictingAppointments = new HashSet<>();
		Date start =appointment.getStart();
		Date end = appointment.getMaxEnd();
		// Templates don't cause conflicts
		if ( RaplaComponent.isTemplate( appointment))
		{
			return conflictingAppointments;
		}
		
		boolean excludeExceptions = true;
		SortedSet<Appointment> appointmentsToTest = AppointmentImpl.getAppointments(appointmentSet, null, start, end, excludeExceptions);
		for ( Appointment overlappingAppointment: appointmentsToTest)
		{
		    Reservation r1 = appointment.getReservation();
		    Reservation r2 = overlappingAppointment.getReservation();
		    if ( RaplaComponent.isTemplate( r1) || RaplaComponent.isTemplate( r2))
            {
                continue;
            }
		    if ( r2 != null && ignoreList.contains( r2))
		    {
		    	continue;
		    }
		    // Don't test overlapping for the same reservations
		    if ( r1 != null && r2 != null && r1.equals( r2) )
		    {
		        continue;
		    }
		    // or the same appointment
		    if (  overlappingAppointment.equals(appointment ))
		    {
		    	continue;
		    }
		    
			if ( overlappingAppointment.overlapsAppointment( appointment) )
			{
				if ( !RaplaComponent.isTemplate( overlappingAppointment))
				{
					conflictingAppointments.add( overlappingAppointment);
					if ( onlyFirstConflictingAppointment)
					{
						return conflictingAppointments;
					}
				}
			}
		}
		return conflictingAppointments;
	}


	 private static boolean equalsOrBothNull(Object o1, Object o2) {
	        if (o1 == null) {
	            if (o2 != null) {
	                return false;
	            }
	        } else if ( o2 == null) {
	            return false;
	        } else if (!o1.equals( o2 ) ) {
	            return false;
	        }
	        return true;
	    }

	 public ReferenceInfo<User> getOwnerRef()
	 {
		 Reservation reservation = getReservation();
		 if ( reservation != null)
		 {
             ReferenceInfo<User> ownerId = reservation.getOwnerRef();
             return ownerId;
		 }
		 return null;
	 }


/*
   public static List<Appointment> getAppointments(
    		Collection<Reservation> reservations,
    		Collection<Allocatable> allocatables) {
    	List<Appointment> appointments = new ArrayList<Appointment>();
        for (Reservation r:reservations)
        {
            for ( Appointment app:r.getAppointments())
            {
    			if (allocatables == null || allocatables.isEmpty())
            	{
            		appointments.add( app);
            	}
            	else 
            	{
                    // this flag is set true if one of the allocatables of a
                    // reservation matches a selected allocatable.
                    boolean allocatableMatched = false;
                	for (Allocatable alloc:r.getAllocatablesFor(app))
                	{
                		if (allocatables.contains(alloc)) {
                			allocatableMatched = true;
                			break;
                		}
                	}
                	if ( allocatableMatched )
                	{
                		appointments.add( app);
                	}
            	}
            }
        }
    	return appointments;
    }
*/
}









