package org.rapla.components.util;

import java.util.Date;

import org.rapla.components.util.DateTools.DateWithoutTimezone;
import org.rapla.entities.tests.Day;

import junit.framework.TestCase;

public class DateToolsTest extends TestCase
{
    public DateToolsTest(String name)
    {
        super(name);
    }

    public void testCutDate1()
    {
        Day day = new Day(2000, 1, 1);
        Date gmtDate = day.toGMTDate();
        Date gmtDateOneHour = new Date(gmtDate.getTime() + DateTools.MILLISECONDS_PER_HOUR);
        assertEquals(DateTools.cutDate(gmtDateOneHour), gmtDate);

    }

    public void testJulianDayConverter()
    {
        Day day = new Day(2013, 2, 28);
        long raplaDate = DateTools.toDate(day.getYear(), day.getMonth(), day.getDate());
        Date gmtDate = day.toGMTDate();
        assertEquals(gmtDate, new Date(raplaDate));
        DateWithoutTimezone date = DateTools.toDate(raplaDate);
        assertEquals(day.getYear(), date.year);
        assertEquals(day.getMonth(), date.month);
        assertEquals(day.getDate(), date.day);
    }

    public void testCutDate2()
    {
        Day day = new Day(1, 1, 1);
        Date gmtDate = day.toGMTDate();
        Date gmtDateOneHour = new Date(gmtDate.getTime() + DateTools.MILLISECONDS_PER_HOUR);
        assertEquals(DateTools.cutDate(gmtDateOneHour), gmtDate);
    }

    public void testWeeknumberIso2016()
    {
        {
            Date date = new Day(2016, 1, 3).toGMTDate();
            final int week = DateTools.getWeekInYearIso(date);
            assertEquals(53, week);
        }
        {
            Date date = new Day(2015, 12, 28).toGMTDate();
            final int week = DateTools.getWeekInYearIso(date);
            assertEquals(53, week);
        }
    }

    public void testWeeknumberIso2014()
    {
        {
            Date date = new Day(2014, 1, 1).toGMTDate();
            final int week = DateTools.getWeekInYearIso(date);
            assertEquals(1, week);
        }
        {
            Date date = new Day(2013, 12, 30).toGMTDate();
            final int week = DateTools.getWeekInYearIso(date);
            assertEquals(1, week);
        }
    }

    public void testWeeknumberUs2015()
    {
        {
            Date date = new Day(2015, 1, 1).toGMTDate();
            final int week = DateTools.getWeekInYearUs(date);
            assertEquals(1, week);
        }
        {
            Date date = new Day(2015, 1, 3).toGMTDate();
            final int week = DateTools.getWeekInYearUs(date);
            assertEquals(1, week);
        }
        {
            Date date = new Day(2015, 1, 4).toGMTDate();
            final int week = DateTools.getWeekInYearUs(date);
            assertEquals(2, week);
        }
        {
            Date date = new Day(2014, 12, 31).toGMTDate();
            final int week = DateTools.getWeekInYearUs(date);
            assertEquals(53, week);
        }
    }

    public void testWeeknumberUs2011()
    {
        {
            Date date = new Day(2011, 1, 1).toGMTDate();
            final int week = DateTools.getWeekInYearUs(date);
            assertEquals(1, week);
        }
        {
            Date date = new Day(2011, 1, 2).toGMTDate();
            final int week = DateTools.getWeekInYearUs(date);
            assertEquals(2, week);
        }
        {
            Date date = new Day(2011, 1, 9).toGMTDate();
            final int week = DateTools.getWeekInYearUs(date);
            assertEquals(3, week);
        }
    }

    public void testWeeknumberUs2012()
    {
        {
            Date date = new Day(2012, 1, 1).toGMTDate();
            final int week = DateTools.getWeekInYearUs(date);
            assertEquals(1, week);
        }
        {
            Date date = new Day(2012, 1, 2).toGMTDate();
            final int week = DateTools.getWeekInYearUs(date);
            assertEquals(1, week);
        }
        {
            Date date = new Day(2012, 1, 3).toGMTDate();
            final int week = DateTools.getWeekInYearUs(date);
            assertEquals(1, week);
        }
        {
            Date date = new Day(2012, 1, 4).toGMTDate();
            final int week = DateTools.getWeekInYearUs(date);
            assertEquals(1, week);
        }
        {
            Date date = new Day(2012, 1, 5).toGMTDate();
            final int week = DateTools.getWeekInYearUs(date);
            assertEquals(1, week);
        }
        {
            Date date = new Day(2012, 1, 6).toGMTDate();
            final int week = DateTools.getWeekInYearUs(date);
            assertEquals(1, week);
        }
        {
            Date date = new Day(2012, 1, 7).toGMTDate();
            final int week = DateTools.getWeekInYearUs(date);
            assertEquals(1, week);
        }
        {
            Date date = new Day(2012, 1, 8).toGMTDate();
            final int week = DateTools.getWeekInYearUs(date);
            assertEquals(2, week);
        }
    }
    
    public void testFormat()
    {
        long date = DateTools.toDate( 2000, 1, 1);
        long time = DateTools.toTime(12, 30, 0);
        Date dateTime = new Date(date + time);
        final String format = DateTools.formatDateTime( dateTime);
        assertEquals("2000-01-01 12:30:00", format);
    }
}
