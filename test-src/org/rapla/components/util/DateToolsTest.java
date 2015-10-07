package org.rapla.components.util;

import java.util.Date;

import org.rapla.components.util.DateTools.DateWithoutTimezone;
import org.rapla.entities.tests.Day;
import org.rapla.entities.tests.Time;

import junit.framework.TestCase;

public class DateToolsTest extends TestCase
{
    public DateToolsTest(String name) {
        super( name);
    }

    public void testCutDate1() 
    {
    	Day day = new Day(2000,1,1);
    	Date gmtDate = day.toGMTDate();
    	Date gmtDateOneHour = new Date(gmtDate.getTime() + DateTools.MILLISECONDS_PER_HOUR);
    	assertEquals(DateTools.cutDate( gmtDateOneHour),  gmtDate);
   
    }
    
    public void testJulianDayConverter() 
    {
    	Day day = new Day(2013,2,28);
    	long raplaDate = DateTools.toDate(day.getYear(), day.getMonth(), day.getDate());
    	Date gmtDate = day.toGMTDate();
    	assertEquals(gmtDate,  new Date(raplaDate));
    	DateWithoutTimezone date = DateTools.toDate(raplaDate);
    	assertEquals(  day.getYear(), date.year);
    	assertEquals(  day.getMonth(), date.month );
    	assertEquals( day.getDate(), date.day );
    }
    
    public void testCutDate2() 
    {
    	Day day = new Day(1,1,1);
    	Date gmtDate = day.toGMTDate();
    	Date gmtDateOneHour = new Date(gmtDate.getTime() + DateTools.MILLISECONDS_PER_HOUR);
    	assertEquals(DateTools.cutDate( gmtDateOneHour),  gmtDate);
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
