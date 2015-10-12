/*--------------------------------------------------------------------------*
 | Copyright (C) 2014 Christopher Kohlhaas, Bettina Lademann                |
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
package org.rapla.client.swing.toolkit;

import java.util.Locale;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;

import org.rapla.components.calendarview.WeekdayMapper;

/** ComboBox that displays the weekdays in long format 
    @see WeekdayMapper
 */
public final class WeekdayChooser extends JComboBox {
    private static final long serialVersionUID = 1L;
    
    WeekdayMapper mapper;
    public WeekdayChooser() {
        this( Locale.getDefault() );
    }

    public WeekdayChooser(Locale locale) {
        setLocale(locale);
    }

    @SuppressWarnings("unchecked")
	public void setLocale(Locale locale) {
        super.setLocale(locale);
        if (locale == null)
            return;
        mapper = new WeekdayMapper(locale);
		DefaultComboBoxModel aModel = new DefaultComboBoxModel(mapper.getNames());
		setModel(aModel);
    }

    public void selectWeekday(int weekday) {
        setSelectedIndex(mapper.indexForDay(weekday));
    }

    /** returns the selected day or -1 if no day is selected.
        @see java.util.Calendar
    */
    public int getSelectedWeekday() {
        if (getSelectedIndex() == -1)
            return -1;
        else
            return mapper.dayForIndex(getSelectedIndex());
    }

}
