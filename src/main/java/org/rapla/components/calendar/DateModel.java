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

package org.rapla.components.calendar;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;

/**
 * The model of the obligatory MVC approach is a wrapper arround an
 * Calendar object.
 */
final class DateModel {
    private Calendar m_calendar;
    private int m_daysMonth;
    private int m_daysLastMonth;
    private int m_firstWeekday;
    private Locale m_locale;
    private DateFormat m_yearFormat;
    private DateFormat m_currentDayFormat;

    ArrayList<DateChangeListener> listenerList = new ArrayList<>();

    public DateModel(Locale locale,TimeZone timeZone) {
        m_locale = locale;
        m_calendar = Calendar.getInstance(timeZone,m_locale);
        trim(m_calendar);
        m_yearFormat = new SimpleDateFormat("yyyy", m_locale);
        m_yearFormat.setTimeZone(timeZone);
        m_currentDayFormat = DateFormat.getDateInstance(DateFormat.MEDIUM, m_locale);
        m_currentDayFormat.setTimeZone(timeZone);
        m_calendar.setLenient(true);
        recalculate();
    }

    public boolean sameDate(Date date) {
        Calendar calendar2 = Calendar.getInstance(m_locale);
        TimeZone timeZone = getTimeZone();
		calendar2.setTimeZone(timeZone);
        calendar2.setTime(date);
        trim(calendar2);
        Date trimedDate = getDate();
		Date date2 = calendar2.getTime();
		return date2.equals(trimedDate);
    }

    public void addDateChangeListener(DateChangeListener listener) {
        listenerList.add(listener);
    }

    public void removeDateChangeListener(DateChangeListener listener) {
        listenerList.remove(listener);
    }

    public Locale getLocale() {return m_locale; }
    public int getDay() {  return m_calendar.get(Calendar.DATE);  }
    public int getMonth() {  return m_calendar.get(Calendar.MONTH) + 1;   }
    public int getYear() {   return  m_calendar.get(Calendar.YEAR);   }

    /** return the number of days of the selected month */
    public int daysMonth() {  return m_daysMonth;   }
    /** return the number of days of the month before the selected month. */
    public int daysLastMonth() { return m_daysLastMonth;   }
    /** return the first weekday of the selected month (1 - 7). */
    public int firstWeekday() { return m_firstWeekday;   }

    /** calculates the weekday from the passed day. */
    public int getWeekday(int day) {
        // calculate the weekday, consider the index shift
        return (((firstWeekday() - 1) + (day - 1))  % 7 ) + 1;
    }

    public Date getDate() {
        return m_calendar.getTime();
    }

    // #TODO Property change listener for TimeZone
    public void setTimeZone(TimeZone timeZone) {
        m_calendar.setTimeZone(timeZone);
        m_yearFormat.setTimeZone(timeZone);
        m_currentDayFormat.setTimeZone(timeZone);
        recalculate();
    }

    public TimeZone getTimeZone() {
        return m_calendar.getTimeZone();
    }

    public String getDateString() {
        return m_currentDayFormat.format(getDate());
    }

    public String getCurrentDateString() {
        return m_currentDayFormat.format(new Date());
    }

    public void addMonth(int count) {
        m_calendar.add(Calendar.MONTH,count);
        recalculate();
    }

    public void addYear(int count) {
        m_calendar.add(Calendar.YEAR,count);
        recalculate();
    }

    public void addDay(int count) {
        m_calendar.add(Calendar.DATE,count);
        recalculate();
    }

    public void setDay(int day) {
        m_calendar.set(Calendar.DATE,day);
        recalculate();
    }

    public void setMonth(int month) {
        m_calendar.set(Calendar.MONTH,month);
        recalculate();
    }
    public String getYearString() {
        DateFormat format;
        if (m_calendar.get(Calendar.ERA)!=GregorianCalendar.AD)
            format = new SimpleDateFormat("yyyy GG", getLocale());
        else
            format = m_yearFormat;
        return format.format(getDate());
    }

    public void setYear(int year) {
        m_calendar.set(Calendar.YEAR,year);
        recalculate();
    }

    public void setDate(int day,int month,int year) {
        m_calendar.set(Calendar.DATE,day);
        m_calendar.set(Calendar.MONTH,month -1);
        m_calendar.set(Calendar.YEAR,year);
        trim(m_calendar);
        recalculate();
   }

    public void setDate(Date date) {
        m_calendar.setTime(date);
        trim(m_calendar);
        recalculate();
    }

    private void trim(Calendar calendar) {
        calendar.set(Calendar.HOUR_OF_DAY,0);
        calendar.set(Calendar.MINUTE,0);
        calendar.set(Calendar.SECOND,0);
        calendar.set(Calendar.MILLISECOND,0);
    }

    // 18.02.2004 CK: Workaround for bug in JDK 1.5.0 .Replace add with roll
    private void recalculate() {
        Calendar calendar =  Calendar.getInstance(getTimeZone(), getLocale());
        Date date = getDate();
		calendar.setTime(date);
        // calculate the number of days of the selected month
        calendar.add(Calendar.MONTH,1);
        calendar.set(Calendar.DATE,1);
        calendar.add(Calendar.DAY_OF_YEAR,-1);
        calendar.getTime();
        m_daysMonth = calendar.get(Calendar.DAY_OF_MONTH);

        // calculate the number of days of the month before the selected month
        calendar.set(Calendar.DATE,1);
        m_firstWeekday = calendar.get(Calendar.DAY_OF_WEEK);
        calendar.add(Calendar.DAY_OF_YEAR,-1);
        m_daysLastMonth = calendar.get(Calendar.DAY_OF_MONTH);

        //        System.out.println("Calendar Recalculate:  " + getDay() + "." + getMonth() + "." + getYear());
        fireDateChanged();
    }

    public DateChangeListener[] getDateChangeListeners() {
        return listenerList.toArray(new DateChangeListener[]{});
    }

    protected void fireDateChanged() {
        DateChangeListener[] listeners = getDateChangeListeners();
        Date date = getDate();
		DateChangeEvent evt = new DateChangeEvent(this,date);
        for (int i = 0;i<listeners.length; i++) {
            listeners[i].dateChanged(evt);
        }
    }

}



