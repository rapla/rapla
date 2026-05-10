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

import org.rapla.components.util.DateTools;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.Locale;
import java.util.TimeZone;

/**
 * The model of the obligatory MVC approach is now a wrapper around a
 * {@link LocalDate}. PRD 014 Calendar migration: the previous
 * {@link java.util.Calendar} field is gone — LocalDate has no time-of-day
 * or timezone, which matches this class's actual semantics (it never
 * exposed a time-of-day; {@code trim()} zeroed it on every set anyway).
 *
 * <p>The {@link TimeZone} parameter is preserved on the public API for
 * back-compat but no longer participates in any computation; it's now a
 * no-op stored only for the {@link #getTimeZone()} getter.
 */
final class DateModel {
    private LocalDate m_date;
    private int m_daysMonth;
    private int m_daysLastMonth;
    private int m_firstWeekday;
    private final Locale m_locale;
    private TimeZone m_timeZone;
    private final DateTimeFormatter m_yearFormat;
    private final DateTimeFormatter m_yearFormatWithEra;
    private final DateTimeFormatter m_currentDayFormat;

    ArrayList<DateChangeListener> listenerList = new ArrayList<>();

    public DateModel(Locale locale, TimeZone timeZone) {
        m_locale = locale;
        m_timeZone = timeZone;
        m_date = LocalDate.now();
        m_yearFormat = DateTimeFormatter.ofPattern("yyyy", m_locale);
        m_yearFormatWithEra = DateTimeFormatter.ofPattern("yyyy GG", m_locale);
        m_currentDayFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(m_locale);
        recalculate();
    }

    public boolean sameDate(LocalDate date) {
        return m_date.equals(date);
    }

    public void addDateChangeListener(DateChangeListener listener) {
        listenerList.add(listener);
    }

    public void removeDateChangeListener(DateChangeListener listener) {
        listenerList.remove(listener);
    }

    public Locale getLocale() { return m_locale; }
    public int getDay() { return m_date.getDayOfMonth(); }
    public int getMonth() { return m_date.getMonthValue(); }
    public int getYear() { return m_date.getYear(); }

    /** return the number of days of the selected month */
    public int daysMonth() { return m_daysMonth; }
    /** return the number of days of the month before the selected month. */
    public int daysLastMonth() { return m_daysLastMonth; }
    /** return the first weekday of the selected month (1 - 7, SUNDAY=1..SATURDAY=7). */
    public int firstWeekday() { return m_firstWeekday; }

    /** calculates the weekday from the passed day. */
    public int getWeekday(int day) {
        // calculate the weekday, consider the index shift
        return (((firstWeekday() - 1) + (day - 1)) % 7) + 1;
    }

    public LocalDate getDate() {
        return m_date;
    }

    public void setTimeZone(TimeZone timeZone) {
        m_timeZone = timeZone;
        // No-op for LocalDate state, but recalculate to fire the listeners
        // in case downstream cares.
        recalculate();
    }

    public TimeZone getTimeZone() {
        return m_timeZone;
    }

    public String getDateString() {
        return m_currentDayFormat.format(m_date);
    }

    public String getCurrentDateString() {
        return m_currentDayFormat.format(LocalDate.now());
    }

    public void addMonth(int count) {
        m_date = m_date.plusMonths(count);
        recalculate();
    }

    public void addYear(int count) {
        m_date = m_date.plusYears(count);
        recalculate();
    }

    public void addDay(int count) {
        m_date = m_date.plusDays(count);
        recalculate();
    }

    public void setDay(int day) {
        m_date = m_date.withDayOfMonth(day);
        recalculate();
    }

    public void setMonth(int month0Based) {
        // Calendar.MONTH was 0-based; preserve the API contract.
        m_date = m_date.withMonth(month0Based + 1);
        recalculate();
    }

    public String getYearString() {
        // Years <= 0 are BC under the Gregorian calendar; use era format then.
        DateTimeFormatter format = (m_date.getYear() < 1) ? m_yearFormatWithEra : m_yearFormat;
        return format.format(m_date);
    }

    public void setYear(int year) {
        m_date = m_date.withYear(year);
        recalculate();
    }

    public void setDate(int day, int month, int year) {
        m_date = LocalDate.of(year, month, day);
        recalculate();
    }

    public void setDate(LocalDate date) {
        m_date = date;
        recalculate();
    }

    private void recalculate() {
        // calculate the number of days of the selected month
        m_daysMonth = m_date.lengthOfMonth();

        // first weekday of the selected month
        LocalDate firstOfMonth = m_date.withDayOfMonth(1);
        m_firstWeekday = DateTools.mapDateAPIToRapla(firstOfMonth.getDayOfWeek());

        // number of days of the month before the selected month
        m_daysLastMonth = firstOfMonth.minusDays(1).lengthOfMonth();

        fireDateChanged();
    }

    public DateChangeListener[] getDateChangeListeners() {
        return listenerList.toArray(new DateChangeListener[]{});
    }

    protected void fireDateChanged() {
        DateChangeListener[] listeners = getDateChangeListeners();
        LocalDateTime date = m_date.atStartOfDay();
        DateChangeEvent evt = new DateChangeEvent(this, date);
        for (int i = 0; i < listeners.length; i++) {
            listeners[i].dateChanged(evt);
        }
    }

}
