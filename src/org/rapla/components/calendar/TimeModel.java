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

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** The model is a wrapper arround an Calendar object.
 */
final class TimeModel {
    Calendar m_calendar;
    Locale m_locale;
	private Date durationStart;
	ArrayList<DateChangeListener> m_listenerList = new ArrayList<DateChangeListener>();

    public TimeModel(Locale locale,TimeZone timeZone) {
        m_locale = locale;
        m_calendar = Calendar.getInstance(timeZone, m_locale);
        trim(m_calendar);
        m_calendar.setLenient(true);
    }

    public void addDateChangeListener(DateChangeListener listener) {
        m_listenerList.add(listener);
    }

    public void removeDateChangeListener(DateChangeListener listener) {
        m_listenerList.remove(listener);
    }

    public Locale getLocale() {return m_locale; }
    public Date getTime() {
        return m_calendar.getTime();
    }

    public Date getDurationStart() 
    {
		return durationStart;
	}

	public void setDurationStart(Date durationStart) 
	{
		if ( durationStart == null)
		{
			this.durationStart = durationStart;
			return;
		}
		Calendar clone = Calendar.getInstance(m_calendar.getTimeZone(), m_locale);
		clone.setTime( durationStart);
		trim(clone);
		this.durationStart = clone.getTime();
	}

    // #TODO Property change listener for TimeZone
    public void setTimeZone(TimeZone timeZone) {
        m_calendar.setTimeZone(timeZone);
    }

    public TimeZone getTimeZone() {
        return m_calendar.getTimeZone();
    }

    public void setTime(int hours,int minutes) {
        m_calendar.set(Calendar.HOUR_OF_DAY,hours);
        m_calendar.set(Calendar.MINUTE,minutes);
        trim(m_calendar);
        fireDateChanged();
   }

    public void setTime(Date date) {
        m_calendar.setTime(date);
        trim(m_calendar);
        fireDateChanged();
   }

    public boolean sameTime(Date date) {
        Calendar calendar = Calendar.getInstance(getTimeZone(), getLocale());
        calendar.setTime(date);
        trim(calendar);
        return calendar.getTime().equals(getTime());
    }

    private void trim(Calendar calendar) {
    	int hours = calendar.get(Calendar.HOUR_OF_DAY);
    	int minutes = calendar.get(Calendar.MINUTE);
    	calendar.setTimeInMillis(0);
    	calendar.set(Calendar.HOUR_OF_DAY, hours);
    	calendar.set(Calendar.MINUTE, minutes);
    }

    public DateChangeListener[] getDateChangeListeners() {
        return m_listenerList.toArray(new DateChangeListener[]{});
    }

    protected void fireDateChanged() {
        DateChangeListener[] listeners = getDateChangeListeners();
        DateChangeEvent evt = new DateChangeEvent(this,getTime());
        for (int i = 0;i<listeners.length; i++) {
            listeners[i].dateChanged(evt);
        }
    }
}



