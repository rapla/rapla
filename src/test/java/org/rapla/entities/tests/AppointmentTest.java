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

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.junit.runners.Suite.SuiteClasses;
import org.rapla.components.util.DateTools;
import org.rapla.components.util.IOUtil;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.entities.domain.AppointmentStartComparator;
import org.rapla.entities.domain.Repeating;
import org.rapla.entities.domain.RepeatingType;
import org.rapla.entities.domain.internal.AppointmentImpl;
import org.rapla.rest.client.internal.isodate.ISODateTimeFormat;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(JUnit4.class)
@SuiteClasses(value=AppointmentTest.class)
public class AppointmentTest {
    TimeZone zone = IOUtil.getTimeZone(); //this is GMT
    Locale locale = Locale.getDefault();

    Appointment createAppointment(String date,String start,String end) {
        final ISODateTimeFormat isoDateTimeFormat = new ISODateTimeFormat();
        final Date startTime = isoDateTimeFormat.parseTimestamp(date + "T" + start);
        final Date endTime = isoDateTimeFormat.parseTimestamp(date + "T" + end);
        return new AppointmentImpl(startTime,endTime);
    }

    Date createDate(String date)
    {
        final ISODateTimeFormat isoDateTimeFormat = new ISODateTimeFormat();
        return isoDateTimeFormat.parseTimestamp( date);
    }


    @Test
    public void testOverlapAndCompareTo()  {
        Appointment a1 = createAppointment("2002-5-25","12:15","14:15");
        Appointment a2 = createAppointment("2002-5-26","13:00","15:00");
        Appointment a3 = createAppointment("2002-5-25","13:00","15:00");
        //Appointment a2 = createAppointment(new Day(2002,5,26),new Time(13,0),new Time(15,0));
        //Appointment a3 = createAppointment(new Day(2002,5,25),new Time(13,0),new Time(15,0));

        Comparator<Appointment> comp = new AppointmentStartComparator();
        // First the single Appointments
        assertTrue(comp.compare(a1,a2) == -1);
        assertTrue(comp.compare(a2,a1) == 1);
        assertTrue(comp.compare(a1,a3) == -1);
        assertTrue(comp.compare(a3,a1) == 1);
        assertTrue(a1.overlapsAppointment(a3));
        assertTrue(a3.overlapsAppointment(a1));
        assertTrue(!a2.overlapsAppointment(a3));
        assertTrue(!a3.overlapsAppointment(a2));
        assertTrue(!a1.overlapsAppointment(a2));
        assertTrue(!a2.overlapsAppointment(a1));


        // Now we test repeatings
        a1.setRepeatingEnabled(true);
        a2.setRepeatingEnabled(true);
        a3.setRepeatingEnabled(true);

        // Weekly repeating until 2002-6-2
        Repeating repeating1 = a1.getRepeating();
        repeating1.setEnd(createDate("2002-06-02"));

        // daily repeating 8x
        Repeating repeating2 = a2.getRepeating();
        repeating2.setType(Repeating.DAILY);
        repeating2.setNumber(8);

        // weekly repeating 1x
        Repeating repeating3 = a3.getRepeating();
        repeating3.setNumber(1);

        assertTrue(a1.overlaps(
                               createDate("2002-05-26")
                               ,createDate("2002-06-2")
                               )
                   );

        assertTrue(a1.overlapsAppointment(a2));
        assertTrue("overlap is not symetric",a2.overlapsAppointment(a1));

        assertTrue(a1.overlapsAppointment(a3));
        assertTrue("overlap is not symetric",a3.overlapsAppointment(a1));

        assertTrue(!a2.overlapsAppointment(a3));
        assertTrue("overlap is not symetric",!a3.overlapsAppointment(a2));

        // No appointment in first week of repeating
        repeating1.addException(createDate("2002-05-025"));

        assertTrue("appointments should not overlap, because of exception", !a1.overlapsAppointment(a3));
        assertTrue("overlap is not symetric",!a3.overlapsAppointment(a1));
    }

    @Test
    public void testWeeklyWith2Weekdays()
    {
        Appointment a1 = createAppointment("2017-04-26","10:00","12:0");
        a1.setRepeatingEnabled( true);
        final Repeating repeating = a1.getRepeating();
        repeating.setNumber(5);
        repeating.setType(RepeatingType.WEEKLY);
        Set<Integer> weekdays = new TreeSet<Integer>();
        weekdays.add(DateTools.WEDNESDAY);
        weekdays.add(DateTools.THURSDAY);
        repeating.setWeekdays(weekdays);

        Collection<AppointmentBlock> blocks = new ArrayList<>();
        Date start = a1.getStart();
        Date end = a1.getEnd();
        a1.createBlocks(start, createDate("2020-01-01"), blocks);
        assertEquals(5, blocks.size());
        final Iterator<AppointmentBlock> iterator = blocks.iterator();
        AppointmentBlock block = iterator.next();
        assertBlock( start, end, block);
        start = DateTools.addDay( start);
        end = DateTools.addDay( end);
        block = iterator.next();
        assertBlock( start, end, block);
        start = DateTools.addDays( start,6);
        end = DateTools.addDays( end, 6);
        block = iterator.next();
        assertBlock( start, end, block);
        start = DateTools.addDay( start);
        end = DateTools.addDay( end);
        block = iterator.next();
        assertBlock( start, end, block);
        start = DateTools.addDays( start,6);
        end = DateTools.addDays( end, 6);
        block = iterator.next();
        assertBlock( start, end, block);
    }

    private void assertBlock(Date start, Date end, AppointmentBlock block)
    {
        assertEquals( "Wrong block-start",start,new Date(block.getStart()));
        assertEquals( "Wrong block-end", end,new Date(block.getEnd()));
    }

    @Test
    public void testOverlap1()  {
        Appointment a1 = createAppointment("2002-04-15","10:00","12:0");
        Appointment a2 = createAppointment("2002-04-15","9:00","11:0");

        a1.setRepeatingEnabled(true);
        Repeating repeating1 = a1.getRepeating();
        repeating1.setEnd(createDate("2002-07-11"));

        a2.setRepeatingEnabled(true);
        Repeating repeating2 = a2.getRepeating();
        repeating2.setType(Repeating.DAILY);
        repeating2.setNumber(5);

        assertTrue(a1.overlapsAppointment(a2));
        assertTrue("overlap is not symetric",a2.overlapsAppointment(a1));
    }

    @Test
    public void testOverlap2() {
        Appointment a1 = createAppointment("2002-4-12","12:00","14:00");
        a1.setRepeatingEnabled(true);
        Repeating repeating1 = a1.getRepeating();
        repeating1.setEnd(createDate("2002-07-11"));
        assertTrue(a1.overlaps(createDate("2002-04-15")
                                          , createDate("2002-04-22")));
    }
    
    @Test
    public void testOverlap3() {
    	final Appointment a1,a2;
    	{
    		final Appointment a = createAppointment("2012-03-09","9:00","11:00");
	        a.setRepeatingEnabled(true);
	        Repeating repeating = a.getRepeating();
	        repeating.setEnd(createDate("2012-04-15"));
	        repeating.addException(createDate("2012-04-06"));
	        a1 = a;
    	}
    	{
	    	final Appointment a = createAppointment("2012-03-02","9:00","11:00");
	        a.setRepeatingEnabled(true);
	        Repeating repeating = a.getRepeating();
	        repeating.setEnd(createDate("2012-04-01"));
	        repeating.addException(createDate("2012-03-16"));
	        a2 = a;
    	}
    	boolean overlap1 = a1.overlapsAppointment(a2);
    	boolean overlap2 = a2.overlapsAppointment(a1);
		assertTrue(overlap1);
		assertTrue(overlap2);
        
    }

    @Test
    public void testBlocks() {
        Appointment a1 = createAppointment("2002-04-12","12:00","14:00");
        a1.setRepeatingEnabled(true);
        Repeating repeating1 = a1.getRepeating();
        repeating1.setEnd(createDate("2002-02-11"));
        List<AppointmentBlock> blocks = new ArrayList<AppointmentBlock>();
        a1.createBlocks( createDate("2002-04-05")
                                , createDate("2002-05-05")
                                , blocks );
        assertEquals( "Repeating end is in the past: createBlocks should only return one block", 1, blocks.size() );
    }

    @Test
    public void testMatchNeverEnding()  {
        Appointment a1 = createAppointment("2002-05-25","11:15","13:15");
        Appointment a2 = createAppointment("2002-05-25","11:15","13:15");

        a1.setRepeatingEnabled(true);
        Repeating repeating1 = a1.getRepeating();
        repeating1.setType(Repeating.WEEKLY);

        a2.setRepeatingEnabled(true);
        Repeating repeating2 = a2.getRepeating();
        repeating2.setType(Repeating.WEEKLY);

        repeating1.addException(createDate("2002-04-10"));
        assertTrue( !a1.matches( a2 ) );
        assertTrue( !a2.matches( a1 ) );
    }
    
    @Test
    public void testMonthly()
    {
        Appointment a1 = createAppointment("2006-08-17","10:30","12:0");
        Date start = a1.getStart();
        a1.setRepeatingEnabled(true);
        Repeating repeating1 = a1.getRepeating();
        repeating1.setType( RepeatingType.MONTHLY);
        repeating1.setNumber( 4);
        List<AppointmentBlock> blocks = new ArrayList<AppointmentBlock>();
        a1.createBlocks( createDate("2006-08-17"), createDate(" 2007-03-30"), blocks);
        assertEquals( 4, blocks.size());
        Collections.sort(blocks);
        assertEquals( start, new Date(blocks.get( 0).getStart()));
        Calendar cal = createGMTCalendar();
        cal.setTime( start );
        int weekday = cal.get( Calendar.DAY_OF_WEEK);
        int dayofweekinmonth = cal.get( Calendar.DAY_OF_WEEK_IN_MONTH);
        assertEquals( DateTools.THURSDAY,DateTools.getWeekday( start) );
        assertEquals( 3, DateTools.getDayOfWeekInMonth(start) );
        assertEquals( 8, DateTools.getMonth(start));
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
        a1.createBlocks( createDate("2006-01-01"), createDate("2007-10-20"), blocks);
        assertEquals( 4, blocks.size());
        
        blocks.clear();
        a1.createBlocks( createDate("2006-10-19"), createDate("2006-10-20"), blocks);
        assertEquals( 1, blocks.size());        
    }

    @Test
    public void testMonthly5ft()
    {
        Appointment a1 = createAppointment("2006-08-31","10:30","12:00");
        Date start = a1.getStart();
        a1.setRepeatingEnabled(true);
        Repeating repeating1 = a1.getRepeating();
        repeating1.setType( RepeatingType.MONTHLY);
        repeating1.setNumber( 4);
        List<AppointmentBlock> blocks = new ArrayList<AppointmentBlock>();
        a1.createBlocks( createDate("2006-08-01"), createDate("2008-08-01"), blocks);
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
        a1.createBlocks( createDate("2006-01-01"), createDate("2007-10-20"), blocks);
        assertEquals( 4, blocks.size());
        
        blocks.clear();
        a1.createBlocks( createDate("2006-10-19"), createDate("2006-11-31"), blocks);
        assertEquals( 1, blocks.size());        
    }

    @Test
    public void testMonthlyNeverending()
    {
        Appointment a1 = createAppointment("2006-08-31","10:30","12:00");
        Date start = a1.getStart();
        a1.setRepeatingEnabled(true);
        Repeating repeating1 = a1.getRepeating();
        repeating1.setType( RepeatingType.MONTHLY);
        repeating1.setEnd( null );
        List<AppointmentBlock> blocks = new ArrayList<AppointmentBlock>();
        a1.createBlocks( createDate("2006-08-01"), createDate("2008-08-01"), blocks);
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
        a1.createBlocks( createDate("2006-01-01"), createDate("2007-10-20"), blocks);
        assertEquals( 5, blocks.size());
    }

    @Test
    public void testYearly29February()
    {
        Appointment a1 = createAppointment("2004-02-29","10:30","12:00");
        Date start = a1.getStart();
        a1.setRepeatingEnabled(true);
        Repeating repeating1 = a1.getRepeating();
        repeating1.setType( RepeatingType.YEARLY);
        repeating1.setNumber( 4);
        List<AppointmentBlock> blocks = new ArrayList<AppointmentBlock>();
        a1.createBlocks( createDate("2004-01-01"), createDate("2020-01-01"), blocks);
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
        a1.createBlocks( createDate("2006-01-01"), createDate("2012-10-20"), blocks);
        assertEquals( 2, blocks.size());
        
        blocks.clear();
        a1.createBlocks( createDate("2008-01-01"), createDate("2008-11-31"), blocks);
        assertEquals( 1, blocks.size());        
    }

    @Test
    public void testYearly()
    {
        Appointment a1 = createAppointment("2006-08-17","10:30","12:00");
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
        a1.createBlocks( createDate("2006-08-17"), createDate("2010-03-30"), blocks);
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
		return Calendar.getInstance( IOUtil.getTimeZone());
	}

    @Test
	public void testMonthlySetEnd()
    {
        Appointment a1 = createAppointment("2006-08-17","10:30","12:00");
        Date start = a1.getStart();
        a1.setRepeatingEnabled(true);
        Repeating repeating1 = a1.getRepeating();
        repeating1.setType( RepeatingType.MONTHLY);
        repeating1.setEnd( createDate("2006-12-01"));
        List<AppointmentBlock> blocks = new ArrayList<AppointmentBlock>();
        {
            Date s =  createDate("2006-08-17");
            Date e =  createDate("2007-03-30");
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

        assertEquals( createDate("2006-12-01"), repeating1.getEnd() );
        assertEquals( createDate("2006-12-01"), a1.getMaxEnd() );
        
        blocks.clear();
        a1.createBlocks( createDate("2006-01-01"), createDate("2007-10-20"), blocks);
        assertEquals( 4, blocks.size());
        
        blocks.clear();
        a1.createBlocks( createDate("2006-10-19"), createDate("2006-10-20"), blocks);
        assertEquals( 1, blocks.size());        
    }

}





