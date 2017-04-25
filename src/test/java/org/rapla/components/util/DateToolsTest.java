package org.rapla.components.util;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.rapla.components.util.DateTools.DateWithoutTimezone;

import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

@RunWith(JUnit4.class)
public class DateToolsTest
{

    @Test
    public void testCutDate1()
    {
        Day day = new Day(2000, 1, 1);
        Date gmtDate = day.toGMTDate();
        Date gmtDateOneHour = new Date(gmtDate.getTime() + DateTools.MILLISECONDS_PER_HOUR);
        Assert.assertEquals(DateTools.cutDate(gmtDateOneHour), gmtDate);

    }
    @Test
    public void testJulianDayConverter()
    {
        Day day = new Day(2013, 2, 28);
        long raplaDate = DateTools.toDate(day.getYear(), day.getMonth(), day.getDate());
        Date gmtDate = day.toGMTDate();
        Assert.assertEquals(gmtDate, new Date(raplaDate));
        DateWithoutTimezone date = DateTools.toDate(raplaDate);
        Assert.assertEquals(day.getYear(), date.year);
        Assert.assertEquals(day.getMonth(), date.month);
        Assert.assertEquals(day.getDate(), date.day);
    }
    @Test
    public void testCutDate2()
    {
        Day day = new Day(1, 1, 1);
        Date gmtDate = day.toGMTDate();
        Date gmtDateOneHour = new Date(gmtDate.getTime() + DateTools.MILLISECONDS_PER_HOUR);
        Assert.assertEquals(DateTools.cutDate(gmtDateOneHour), gmtDate);
    }
    @Test
    public void testWeeknumberIso2016()
    {
        {
            Date date = new Day(2016, 1, 3).toGMTDate();
            final int week = DateTools.getWeekInYearIso(date);
            Assert.assertEquals(53, week);
        }
        {
            Date date = new Day(2015, 12, 28).toGMTDate();
            final int week = DateTools.getWeekInYearIso(date);
            Assert.assertEquals(53, week);
        }
    }
    @Test
    public void testWeeknumberIso2014()
    {
        {
            Date date = new Day(2014, 1, 1).toGMTDate();
            final int week = DateTools.getWeekInYearIso(date);
            Assert.assertEquals(1, week);
        }
        {
            Date date = new Day(2013, 12, 30).toGMTDate();
            final int week = DateTools.getWeekInYearIso(date);
            Assert.assertEquals(1, week);
        }
    }
    @Test
    public void testWeeknumberUs2015()
    {
        {
            Date date = new Day(2015, 1, 1).toGMTDate();
            final int week = DateTools.getWeekInYearUs(date);
            Assert.assertEquals(1, week);
        }
        {
            Date date = new Day(2015, 1, 3).toGMTDate();
            final int week = DateTools.getWeekInYearUs(date);
            Assert.assertEquals(1, week);
        }
        {
            Date date = new Day(2015, 1, 4).toGMTDate();
            final int week = DateTools.getWeekInYearUs(date);
            Assert.assertEquals(2, week);
        }
        {
            Date date = new Day(2014, 12, 31).toGMTDate();
            final int week = DateTools.getWeekInYearUs(date);
            Assert.assertEquals(53, week);
        }
    }
    @Test
    public void testWeeknumberUs2011()
    {
        {
            Date date = new Day(2011, 1, 1).toGMTDate();
            final int week = DateTools.getWeekInYearUs(date);
            Assert.assertEquals(1, week);
        }
        {
            Date date = new Day(2011, 1, 2).toGMTDate();
            final int week = DateTools.getWeekInYearUs(date);
            Assert.assertEquals(2, week);
        }
        {
            Date date = new Day(2011, 1, 9).toGMTDate();
            final int week = DateTools.getWeekInYearUs(date);
            Assert.assertEquals(3, week);
        }
    }
    @Test
    public void testWeeknumberUs2012()
    {
        {
            Date date = new Day(2012, 1, 1).toGMTDate();
            final int week = DateTools.getWeekInYearUs(date);
            Assert.assertEquals(1, week);
        }
        {
            Date date = new Day(2012, 1, 2).toGMTDate();
            final int week = DateTools.getWeekInYearUs(date);
            Assert.assertEquals(1, week);
        }
        {
            Date date = new Day(2012, 1, 3).toGMTDate();
            final int week = DateTools.getWeekInYearUs(date);
            Assert.assertEquals(1, week);
        }
        {
            Date date = new Day(2012, 1, 4).toGMTDate();
            final int week = DateTools.getWeekInYearUs(date);
            Assert.assertEquals(1, week);
        }
        {
            Date date = new Day(2012, 1, 5).toGMTDate();
            final int week = DateTools.getWeekInYearUs(date);
            Assert.assertEquals(1, week);
        }
        {
            Date date = new Day(2012, 1, 6).toGMTDate();
            final int week = DateTools.getWeekInYearUs(date);
            Assert.assertEquals(1, week);
        }
        {
            Date date = new Day(2012, 1, 7).toGMTDate();
            final int week = DateTools.getWeekInYearUs(date);
            Assert.assertEquals(1, week);
        }
        {
            Date date = new Day(2012, 1, 8).toGMTDate();
            final int week = DateTools.getWeekInYearUs(date);
            Assert.assertEquals(2, week);
        }
    }
    @Test
    public void testFormat()
    {
        long date = DateTools.toDate( 2000, 1, 1);
        long time = DateTools.toTime(12, 30, 0);
        Date dateTime = new Date(date + time);
        final String format = DateTools.formatDateTime( dateTime);
        org.junit.Assert.assertEquals("2000-01-01 12:30:00", format);
    }

    public final class Day implements Comparable<Day>
    {
        int date = 0;
        int month = 0;
        int year = 0;

        public Day(int year,int month,int date) {
            this.year = year;
            this.month = month;
            this.date = date;
        }

        public int getDate() {
            return date;
        }

        public int getMonth() {
            return month;
        }

        public int getYear() {
            return year;
        }

        public String toString() {
            return getYear() + "-" + getMonth() + "-" + getDate();
        }

        public Date toDate(TimeZone zone) {
            Calendar cal = Calendar.getInstance(zone);
            cal.setTime(new Date(0));
            cal.set(Calendar.YEAR,getYear());
            cal.set(Calendar.MONTH,getMonth() - 1);
            cal.set(Calendar.DATE,getDate());
            cal.set(Calendar.HOUR_OF_DAY,0);
            cal.set(Calendar.MINUTE,0);
            cal.set(Calendar.SECOND,0);
            cal.set(Calendar.MILLISECOND,0);
            return cal.getTime();
        }

        public Date toGMTDate() {
            TimeZone zone = TimeZone.getTimeZone("GMT+0");
            Calendar cal = Calendar.getInstance(zone);
            cal.setTime(new Date(0));
            cal.set(Calendar.YEAR,getYear());
            cal.set(Calendar.MONTH,getMonth() - 1);
            cal.set(Calendar.DATE,getDate());
            cal.set(Calendar.HOUR_OF_DAY,0);
            cal.set(Calendar.MINUTE,0);
            cal.set(Calendar.SECOND,0);
            cal.set(Calendar.MILLISECOND,0);
            return cal.getTime();
        }

        public int compareTo(Day day) {
            if (getYear() < day.getYear())
                return -1;
            if (getYear() > day.getYear())
                return 1;
            if (getMonth() < day.getMonth())
                return -1;
            if (getMonth() > day.getMonth())
                return 1;
            if (getDate() < day.getDate())
                return -1;
            if (getDate() > day.getDate())
                return 1;
            return 0;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + date;
            result = prime * result + month;
            result = prime * result + year;
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            Day other = (Day) obj;
            if (date != other.date)
                return false;
            if (month != other.month)
                return false;
            return year == other.year;
        }
    }

}
