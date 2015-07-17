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
package org.rapla.entities.tests;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.rapla.components.util.DateTools;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.entities.domain.AppointmentStartComparator;
import org.rapla.entities.domain.Repeating;
import org.rapla.entities.domain.RepeatingType;
import org.rapla.entities.domain.internal.AppointmentImpl;

public class AppointmentTest extends TestCase {
    TimeZone zone = DateTools.getTimeZone(); //this is GMT
    Locale locale = Locale.getDefault();

    public AppointmentTest(String name) {
        super(name);
    }

    public static Test suite() {
        return new TestSuite(AppointmentTest.class);
    }

    public void setUp() throws Exception {
    }

    Appointment createAppointment(Day day,Time start,Time end) {
        Calendar cal = Calendar.getInstance(zone,locale);
        cal.set(Calendar.YEAR,day.getYear());
        cal.set(Calendar.MONTH,day.getMonth() - 1);
        cal.set(Calendar.DATE,day.getDate());
        cal.set(Calendar.HOUR_OF_DAY,start.getHour());
        cal.set(Calendar.MINUTE,start.getMinute());
        cal.set(Calendar.SECOND,0);
        cal.set(Calendar.MILLISECOND,0);
        Date startTime = cal.getTime();
        cal.set(Calendar.HOUR_OF_DAY,end.getHour());
        cal.set(Calendar.MINUTE,end.getMinute());
        Date endTime = cal.getTime();
        return new AppointmentImpl(startTime,endTime);
    }

    public void testOverlapAndCompareTo()  {
        Appointment a1 = createAppointment(new Day(2002,5,25),new Time(12,15),new Time(14,15));
        Appointment a2 = createAppointment(new Day(2002,5,26),new Time(13,0),new Time(15,0));
        Appointment a3 = createAppointment(new Day(2002,5,25),new Time(13,0),new Time(15,0));

        Comparator<Appointment> comp = new AppointmentStartComparator();
        // First the single Appointments
        assertTrue(comp.compare(a1,a2) == -1);
        assertTrue(comp.compare(a2,a1) == 1);
        assertTrue(comp.compare(a1,a3) == -1);
        assertTrue(comp.compare(a3,a1) == 1);
        assertTrue(a1.overlaps(a3));
        assertTrue(a3.overlaps(a1));
        assertTrue(!a2.overlaps(a3));
        assertTrue(!a3.overlaps(a2));
        assertTrue(!a1.overlaps(a2));
        assertTrue(!a2.overlaps(a1));


        // Now we test repeatings
        a1.setRepeatingEnabled(true);
        a2.setRepeatingEnabled(true);
        a3.setRepeatingEnabled(true);

        // Weekly repeating until 2002-6-2
        Repeating repeating1 = a1.getRepeating();
        repeating1.setEnd(new Day(2002,6,2).toDate(zone));

        // daily repeating 8x
        Repeating repeating2 = a2.getRepeating();
        repeating2.setType(Repeating.DAILY);
        repeating2.setNumber(8);

        // weekly repeating 1x
        Repeating repeating3 = a3.getRepeating();
        repeating3.setNumber(1);

        assertTrue(a1.overlaps(
                               new Day(2002,5,26).toDate(zone)
                               ,new Day(2002,6,2).toDate(zone)
                               )
                   );

        assertTrue(a1.overlaps(a2));
        assertTrue("overlap is not symetric",a2.overlaps(a1));

        assertTrue(a1.overlaps(a3));
        assertTrue("overlap is not symetric",a3.overlaps(a1));

        assertTrue(!a2.overlaps(a3));
        assertTrue("overlap is not symetric",!a3.overlaps(a2));

        // No appointment in first week of repeating
        repeating1.addException(new Day(2002,5,25).toDate(zone));

        assertTrue("appointments should not overlap, because of exception", !a1.overlaps(a3));
        assertTrue("overlap is not symetric",!a3.overlaps(a1));
    }

    public void testOverlap1()  {
        Appointment a1 = createAppointment(new Day(2002,4,15),new Time(10,0),new Time(12,0));
        Appointment a2 = createAppointment(new Day(2002,4,15),new Time(9,0),new Time(11,0));

        a1.setRepeatingEnabled(true);
        Repeating repeating1 = a1.getRepeating();
        repeating1.setEnd(new Day(2002,7,11).toDate(zone));

        a2.setRepeatingEnabled(true);
        Repeating repeating2 = a2.getRepeating();
        repeating2.setType(Repeating.DAILY);
        repeating2.setNumber(5);

        assertTrue(a1.overlaps(a2));
        assertTrue("overlap is not symetric",a2.overlaps(a1));
    }

    public void testOverlap2() {
        Appointment a1 = createAppointment(new Day(2002,4,12),new Time(12,0),new Time(14,0));
        a1.setRepeatingEnabled(true);
        Repeating repeating1 = a1.getRepeating();
        repeating1.setEnd(new Day(2002,7,11).toDate(zone));
        assertTrue(a1.overlaps(new Day(2002,4,15).toDate(zone)
                                          , new Day(2002,4,22).toDate(zone)));
    }
    
    public void testOverlap3() {
    	final Appointment a1,a2;
    	{
    		final Appointment a = createAppointment(new Day(2012,3,9),new Time(9,0),new Time(11,0));
	        a.setRepeatingEnabled(true);
	        Repeating repeating = a.getRepeating();
	        repeating.setEnd(new Day(2012,4,15).toDate(zone));
	        repeating.addException(new Day(2012,4,6).toDate(zone));
	        a1 = a;
    	}
    	{
	    	final Appointment a = createAppointment(new Day(2012,3,2),new Time(9,0),new Time(11,0));
	        a.setRepeatingEnabled(true);
	        Repeating repeating = a.getRepeating();
	        repeating.setEnd(new Day(2012,4,1).toDate(zone));
	        repeating.addException(new Day(2012,3,16).toDate(zone));
	        a2 = a;
    	}
    	boolean overlap1 = a1.overlaps(a2);
    	boolean overlap2 = a2.overlaps(a1);
		assertTrue(overlap1);
		assertTrue(overlap2);
        
    }

    public void testBlocks() {
        Appointment a1 = createAppointment(new Day(2002,4,12),new Time(12,0),new Time(14,0));
        a1.setRepeatingEnabled(true);
        Repeating repeating1 = a1.getRepeating();
        repeating1.setEnd(new Day(2002,2,11).toDate(zone));
        List<AppointmentBlock> blocks = new ArrayList<AppointmentBlock>();
        a1.createBlocks( new Day(2002,4,5).toDate(zone)
                                , new Day(2002,5,5).toDate(zone)
                                , blocks );
        assertEquals( "Repeating end is in the past: createBlocks should only return one block", 1, blocks.size() );
    }

    public void testMatchNeverEnding()  {
        Appointment a1 = createAppointment(new Day(2002,5,25),new Time(11,15),new Time(13,15));
        Appointment a2 = createAppointment(new Day(2002,5,25),new Time(11,15),new Time(13,15));

        a1.setRepeatingEnabled(true);
        Repeating repeating1 = a1.getRepeating();
        repeating1.setType(Repeating.WEEKLY);

        a2.setRepeatingEnabled(true);
        Repeating repeating2 = a2.getRepeating();
        repeating2.setType(Repeating.WEEKLY);

        repeating1.addException(new Day(2002,4,10).toDate( zone ));
        assertTrue( !a1.matches( a2 ) );
        assertTrue( !a2.matches( a1 ) );
    }
    
    public void testMonthly()
    {
        Appointment a1 = createAppointment(new Day(2006,8,17),new Time(10,30),new Time(12,0));
        Date start = a1.getStart();
        a1.setRepeatingEnabled(true);
        Repeating repeating1 = a1.getRepeating();
        repeating1.setType( RepeatingType.MONTHLY);
        repeating1.setNumber( 4);
        List<AppointmentBlock> blocks = new ArrayList<AppointmentBlock>();
        a1.createBlocks( new Day(2006,8,17).toGMTDate(), new Day( 2007, 3, 30).toGMTDate(), blocks);
        assertEquals( 4, blocks.size());
        Collections.sort(blocks);
        assertEquals( start, new Date(blocks.get( 0).getStart()));
        Calendar cal = createGMTCalendar();
        cal.setTime( start );
        int weekday = cal.get( Calendar.DAY_OF_WEEK);
        int dayofweekinmonth = cal.get( Calendar.DAY_OF_WEEK_IN_MONTH);
        assertEquals( Calendar.THURSDAY,weekday );
        assertEquals( 3, dayofweekinmonth );
        assertEquals( Calendar.AUGUST, cal.get( Calendar.MONTH));
        // we expect the second wednesday in april
        cal.add( Calendar.MONTH, 1 );
        cal.set( Calendar.DAY_OF_WEEK , weekday );
        cal.set( Calendar.DAY_OF_WEEK_IN_MONTH, dayofweekinmonth);
        start = cal.getTime();
        assertEquals( start, new Date(blocks.get( 1).getStart()));
        cal.add( Calendar.MONTH, 1 );
        cal.set( Calendar.DAY_OF_WEEK , weekday );
        cal.set( Calendar.DAY_OF_WEEK_IN_MONTH, dayofweekinmonth);
        assertEquals(10, cal.get( Calendar.HOUR_OF_DAY));
        start = cal.getTime();
        assertEquals( start, new Date(blocks.get( 2).getStart()));
        cal.add( Calendar.MONTH, 1 );
        cal.set( Calendar.DAY_OF_WEEK , weekday );
        cal.set( Calendar.DAY_OF_WEEK_IN_MONTH, dayofweekinmonth);
        start = cal.getTime();
        assertEquals( start, new Date(blocks.get( 3).getStart()));

        assertEquals( start, repeating1.getEnd() );
        assertEquals( start, a1.getMaxEnd() );
        
        blocks.clear();
        a1.createBlocks( new Day(2006,1,1).toGMTDate(), new Day( 2007, 10, 20).toGMTDate(), blocks);
        assertEquals( 4, blocks.size());
        
        blocks.clear();
        a1.createBlocks( new Day(2006,10,19).toGMTDate(), new Day( 2006, 10, 20).toGMTDate(), blocks);
        assertEquals( 1, blocks.size());        
    }

    public void testMonthly5ft()
    {
        Appointment a1 = createAppointment(new Day(2006,8,31),new Time(10,30),new Time(12,0));
        Date start = a1.getStart();
        a1.setRepeatingEnabled(true);
        Repeating repeating1 = a1.getRepeating();
        repeating1.setType( RepeatingType.MONTHLY);
        repeating1.setNumber( 4);
        List<AppointmentBlock> blocks = new ArrayList<AppointmentBlock>();
        a1.createBlocks( new Day(2006,8,1).toGMTDate(), new Day( 2008, 8, 1).toGMTDate(), blocks);
        assertEquals( 4, blocks.size());
        Collections.sort( blocks);
        assertEquals( start, new Date(blocks.get(0).getStart()));
        Calendar cal = createGMTCalendar();
        cal.setTime( start );
        int weekday = cal.get( Calendar.DAY_OF_WEEK);
        int dayofweekinmonth = cal.get( Calendar.DAY_OF_WEEK_IN_MONTH);
        assertEquals( Calendar.THURSDAY,weekday );
        assertEquals( 5, dayofweekinmonth );
        assertEquals( Calendar.AUGUST, cal.get( Calendar.MONTH));
        
        cal.set( Calendar.MONTH, Calendar.NOVEMBER );
        cal.set( Calendar.DAY_OF_WEEK , weekday );
        cal.set( Calendar.DAY_OF_WEEK_IN_MONTH, dayofweekinmonth);
        start = cal.getTime();
        assertEquals( start, new Date(blocks.get(1).getStart()));
        
        cal.add( Calendar.YEAR,1);
        cal.set( Calendar.MONTH, Calendar.MARCH );
        cal.set( Calendar.DAY_OF_WEEK , weekday );
        cal.set( Calendar.DAY_OF_WEEK_IN_MONTH, dayofweekinmonth);
        assertEquals(10, cal.get( Calendar.HOUR_OF_DAY));
        start = cal.getTime();
        
        assertEquals( start, new Date(blocks.get(2).getStart()));
        cal.set( Calendar.MONTH, Calendar.MAY );
        cal.set( Calendar.DAY_OF_WEEK , weekday );
        cal.set( Calendar.DAY_OF_WEEK_IN_MONTH, dayofweekinmonth);
        start = cal.getTime();
        assertEquals( start, new Date(blocks.get(3).getStart()));

        assertEquals( start, repeating1.getEnd() );
        assertEquals( start, a1.getMaxEnd() );
        
        blocks.clear();
        a1.createBlocks( new Day(2006,1,1).toGMTDate(), new Day( 2007, 10, 20).toGMTDate(), blocks);
        assertEquals( 4, blocks.size());
        
        blocks.clear();
        a1.createBlocks( new Day(2006,10,19).toGMTDate(), new Day( 2006, 11, 31).toGMTDate(), blocks);
        assertEquals( 1, blocks.size());        
    }

    public void testMonthlyNeverending()
    {
        Appointment a1 = createAppointment(new Day(2006,8,31),new Time(10,30),new Time(12,0));
        Date start = a1.getStart();
        a1.setRepeatingEnabled(true);
        Repeating repeating1 = a1.getRepeating();
        repeating1.setType( RepeatingType.MONTHLY);
        repeating1.setEnd( null );
        List<AppointmentBlock> blocks = new ArrayList<AppointmentBlock>();
        a1.createBlocks( new Day(2006,8,1).toGMTDate(), new Day( 2008, 8, 1).toGMTDate(), blocks);
        assertEquals( 9, blocks.size());
        assertEquals( start, new Date(blocks.get(0).getStart()));
        Calendar cal = createGMTCalendar();
        cal.setTime( start );
        int weekday = cal.get( Calendar.DAY_OF_WEEK);
        int dayofweekinmonth = cal.get( Calendar.DAY_OF_WEEK_IN_MONTH);
        assertEquals( Calendar.THURSDAY,weekday );
        assertEquals( 5, dayofweekinmonth );
        assertEquals( Calendar.AUGUST, cal.get( Calendar.MONTH));
        
        cal.set( Calendar.MONTH, Calendar.NOVEMBER );
        cal.set( Calendar.DAY_OF_WEEK , weekday );
        cal.set( Calendar.DAY_OF_WEEK_IN_MONTH, dayofweekinmonth);
        start = cal.getTime();
        assertEquals( start, new Date(blocks.get(1).getStart()));
        
        cal.add( Calendar.YEAR,1);
        cal.set( Calendar.MONTH, Calendar.MARCH );
        cal.set( Calendar.DAY_OF_WEEK , weekday );
        cal.set( Calendar.DAY_OF_WEEK_IN_MONTH, dayofweekinmonth);
        assertEquals(10, cal.get( Calendar.HOUR_OF_DAY));
        start = cal.getTime();
        
        assertEquals( start, new Date(blocks.get(2).getStart()));
        cal.set( Calendar.MONTH, Calendar.MAY );
        cal.set( Calendar.DAY_OF_WEEK , weekday );
        cal.set( Calendar.DAY_OF_WEEK_IN_MONTH, dayofweekinmonth);
        start = cal.getTime();
        assertEquals( start, new Date(blocks.get(3).getStart()));

        
        blocks.clear();
        a1.createBlocks( new Day(2006,1,1).toGMTDate(), new Day( 2007, 10, 20).toGMTDate(), blocks);
        assertEquals( 5, blocks.size());
    }

    public void testYearly29February()
    {
        Appointment a1 = createAppointment(new Day(2004,2,29),new Time(10,30),new Time(12,0));
        Date start = a1.getStart();
        a1.setRepeatingEnabled(true);
        Repeating repeating1 = a1.getRepeating();
        repeating1.setType( RepeatingType.YEARLY);
        repeating1.setNumber( 4);
        List<AppointmentBlock> blocks = new ArrayList<AppointmentBlock>();
        a1.createBlocks( new Day(2004,1,1).toGMTDate(), new Day( 2020, 1, 1).toGMTDate(), blocks);
        assertEquals( 4, blocks.size());
        assertEquals( start, new Date(blocks.get(0).getStart()));
        Calendar cal = createGMTCalendar();
        cal.setTime( start );
        int weekday = cal.get( Calendar.DAY_OF_WEEK);
        int dayofweekinmonth = cal.get( Calendar.DAY_OF_WEEK_IN_MONTH);
        assertEquals( Calendar.SUNDAY,weekday );
        assertEquals( 5, dayofweekinmonth );
        assertEquals( Calendar.FEBRUARY, cal.get( Calendar.MONTH));
        
        cal.add( Calendar.YEAR,4);
        cal.set( Calendar.MONTH, Calendar.FEBRUARY );
        cal.set( Calendar.DAY_OF_MONTH , 29 );
        start = cal.getTime();
        assertEquals( start, new Date(blocks.get(1).getStart()));
        
        cal.add( Calendar.YEAR,4);
        cal.set( Calendar.MONTH, Calendar.FEBRUARY );
        cal.set( Calendar.DAY_OF_MONTH , 29 );
        assertEquals(10, cal.get( Calendar.HOUR_OF_DAY));
        start = cal.getTime();
        
        assertEquals( start, new Date(blocks.get(2).getStart()));
        cal.add( Calendar.YEAR,4);
        cal.set( Calendar.MONTH, Calendar.FEBRUARY );
        cal.set( Calendar.DAY_OF_MONTH , 29 );
        start = cal.getTime();
        assertEquals( start, new Date(blocks.get(3).getStart()));

        assertEquals( start, repeating1.getEnd() );
        assertEquals( start, a1.getMaxEnd() );
        
        blocks.clear();
        a1.createBlocks( new Day(2006,1,1).toGMTDate(), new Day( 2012, 10, 20).toGMTDate(), blocks);
        assertEquals( 2, blocks.size());
        
        blocks.clear();
        a1.createBlocks( new Day(2008,1,1).toGMTDate(), new Day( 2008, 11, 31).toGMTDate(), blocks);
        assertEquals( 1, blocks.size());        
    }

    public void testYearly()
    {
        Appointment a1 = createAppointment(new Day(2006,8,17),new Time(10,30),new Time(12,0));
        Date start = a1.getStart();
        a1.setRepeatingEnabled(true);
        Repeating repeating1 = a1.getRepeating();
        repeating1.setType( RepeatingType.YEARLY );
        repeating1.setNumber( 4);
        Calendar cal = createGMTCalendar();
        cal.setTime( start );
        int dayInMonth = cal.get( Calendar.DAY_OF_MONTH);
        int month = cal.get( Calendar.MONTH);
        List<AppointmentBlock> blocks = new ArrayList<AppointmentBlock>();
        a1.createBlocks( new Day(2006,8,17).toGMTDate(), new Day( 2010, 3, 30).toGMTDate(), blocks);
        assertEquals( 4, blocks.size());
        assertEquals( start, new Date(blocks.get(0).getStart()));
        cal.add( Calendar.YEAR, 1 );
        start = cal.getTime();
        assertEquals( start, new Date(blocks.get(1).getStart()));
        cal.add( Calendar.YEAR, 1 );
        start = cal.getTime();
        assertEquals( start, new Date(blocks.get(2).getStart()));
        cal.add( Calendar.YEAR, 1 );
        start = cal.getTime();
        assertEquals( dayInMonth,cal.get(Calendar.DAY_OF_MONTH));
        assertEquals( month,cal.get(Calendar.MONTH));
        assertEquals( start, new Date(blocks.get(3).getStart()));
        assertEquals( start, repeating1.getEnd() );
        assertEquals( start, a1.getMaxEnd() );
    }

    private Calendar createGMTCalendar() {
		return Calendar.getInstance( DateTools.getTimeZone());
	}

	public void testMonthlySetEnd()
    {
        Appointment a1 = createAppointment(new Day(2006,8,17),new Time(10,30),new Time(12,0));
        Date start = a1.getStart();
        a1.setRepeatingEnabled(true);
        Repeating repeating1 = a1.getRepeating();
        repeating1.setType( RepeatingType.MONTHLY);
        repeating1.setEnd( new Day(2006,12,1).toGMTDate());
        List<AppointmentBlock> blocks = new ArrayList<AppointmentBlock>();
        {
            Date s =  new Day(2006,8,17).toGMTDate();
            Date e =  new Day( 2007, 3, 30).toGMTDate();
            a1.createBlocks( s,e , blocks);
        }
        assertEquals( 4, blocks.size());
        assertEquals( start, new Date(blocks.get(0).getStart()));
        Calendar cal = createGMTCalendar();
        cal.setTime( start );
        int weekday = cal.get( Calendar.DAY_OF_WEEK);
        int dayofweekinmonth = cal.get( Calendar.DAY_OF_WEEK_IN_MONTH);
        assertEquals( Calendar.THURSDAY,weekday );
        assertEquals( 3, dayofweekinmonth );
        assertEquals( Calendar.AUGUST, cal.get( Calendar.MONTH));
        cal.add( Calendar.MONTH, 1 );
        cal.set( Calendar.DAY_OF_WEEK , weekday );
        cal.set( Calendar.DAY_OF_WEEK_IN_MONTH, dayofweekinmonth);
        start = cal.getTime();
        assertEquals( start, new Date(blocks.get(1).getStart()));
        cal.add( Calendar.MONTH, 1 );
        cal.set( Calendar.DAY_OF_WEEK , weekday );
        cal.set( Calendar.DAY_OF_WEEK_IN_MONTH, dayofweekinmonth);
        assertEquals(10, cal.get( Calendar.HOUR_OF_DAY));
        start = cal.getTime();
        assertEquals( start, new Date(blocks.get(2).getStart()));
        cal.add( Calendar.MONTH, 1 );
        cal.set( Calendar.DAY_OF_WEEK , weekday );
        cal.set( Calendar.DAY_OF_WEEK_IN_MONTH, dayofweekinmonth);
        start = cal.getTime();
        assertEquals( start, new Date(blocks.get(3).getStart()));

        assertEquals( new Day(2006,12,1).toGMTDate(), repeating1.getEnd() );
        assertEquals( new Day(2006,12,1).toGMTDate(), a1.getMaxEnd() );
        
        blocks.clear();
        a1.createBlocks( new Day(2006,1,1).toGMTDate(), new Day( 2007, 10, 20).toGMTDate(), blocks);
        assertEquals( 4, blocks.size());
        
        blocks.clear();
        a1.createBlocks( new Day(2006,10,19).toGMTDate(), new Day( 2006, 10, 20).toGMTDate(), blocks);
        assertEquals( 1, blocks.size());        
    }

}





