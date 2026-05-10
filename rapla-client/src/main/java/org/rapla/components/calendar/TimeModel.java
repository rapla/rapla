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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Locale;
import java.util.TimeZone;

/** State model for time-only input widgets. PRD 014 Calendar migration:
 *  the previous {@link java.util.Calendar} field is gone — {@link LocalTime}
 *  is the right type for "hour + minute" with no date/zone semantics.
 *  TimeZone parameter preserved on the public API (no-op now) for back-compat. */
final class TimeModel {
    LocalTime m_time;
    Locale m_locale;
    private TimeZone m_timeZone;
    private LocalDateTime durationStart;
    ArrayList<DateChangeListener> m_listenerList = new ArrayList<>();

    public TimeModel(Locale locale, TimeZone timeZone) {
        m_locale = locale;
        m_timeZone = timeZone;
        m_time = LocalTime.of(0, 0);
    }

    public void addDateChangeListener(DateChangeListener listener) {
        m_listenerList.add(listener);
    }

    public void removeDateChangeListener(DateChangeListener listener) {
        m_listenerList.remove(listener);
    }

    public Locale getLocale() { return m_locale; }
    public LocalTime getTime() { return m_time; }

    public LocalDateTime getDurationStart() {
        return durationStart;
    }

    public void setDurationStart(LocalDateTime durationStart) {
        if (durationStart == null) {
            this.durationStart = null;
            return;
        }
        // Trim to hour+minute precision to match the previous Calendar.trim semantic.
        this.durationStart = durationStart.withSecond(0).withNano(0);
    }

    public void setTimeZone(TimeZone timeZone) {
        m_timeZone = timeZone;
    }

    public TimeZone getTimeZone() {
        return m_timeZone;
    }

    public void setTime(int hours, int minutes) {
        m_time = LocalTime.of(hours, minutes);
        fireDateChanged();
    }

    public void setTime(LocalTime time) {
        m_time = LocalTime.of(time.getHour(), time.getMinute());
        fireDateChanged();
    }

    public boolean sameTime(LocalTime time) {
        return m_time.equals(LocalTime.of(time.getHour(), time.getMinute()));
    }

    public DateChangeListener[] getDateChangeListeners() {
        return m_listenerList.toArray(new DateChangeListener[]{});
    }

    protected void fireDateChanged() {
        DateChangeListener[] listeners = getDateChangeListeners();
        DateChangeEvent evt = new DateChangeEvent(this, LocalDate.now().atTime(m_time));
        for (int i = 0; i < listeners.length; i++) {
            listeners[i].dateChanged(evt);
        }
    }
}
