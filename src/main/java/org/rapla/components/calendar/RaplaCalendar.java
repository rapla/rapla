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

import java.awt.Font;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import javax.swing.JComponent;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
/** This is another ComboBox-like calendar component.
 *  It is localizable and it uses swing-components.
 *  <p>The combobox editor is a {@link DateField}. If the ComboBox-Button
 *  is pressed, a CalendarMenu will drop down.</p>
 *  @see CalendarMenu
 *  @see DateField
 *  @author Christopher Kohlhaas
 */
public final class RaplaCalendar extends RaplaComboBox {
    private static final long serialVersionUID = 1L;

    protected DateField  m_dateField;
    protected CalendarMenu m_calendarMenu;
    Collection<DateChangeListener> m_listenerList = new ArrayList<DateChangeListener>();
    protected DateModel m_model;
    private Date m_lastDate;
    DateRenderer m_dateRenderer;

    /** Create a new Calendar with the default locale. The calendarmenu
     will be accessible via a drop-down-box */
    public RaplaCalendar() {
        this(Locale.getDefault(),TimeZone.getDefault(),true);
    }
    
    public DateField getDateField()
    {
    	return m_dateField;
    }

    /** Create a new Calendar with the specified locale and timezone. The calendarmenu
     will be accessible via a drop-down-box */
    public RaplaCalendar(Locale locale,TimeZone timeZone) {
        this(locale,timeZone,true);
    }

    /** Create a new Calendar with the specified locale and timezone.  The
        isDropDown flag specifies if the calendarmenu should be
        accessible via a drop-down-box. Alternatively you can get the
        calendarmenu with getPopupComponent().
     */
    public RaplaCalendar(Locale locale,TimeZone timeZone,boolean isDropDown) {
        super(isDropDown,new DateField(locale,timeZone));
        m_model = new DateModel(locale,timeZone);
        m_dateField = (DateField) m_editorComponent;
        Listener listener = new Listener();
        m_dateField.addChangeListener(listener);
        m_model.addDateChangeListener(listener);
        m_lastDate = m_model.getDate();
        setDateRenderer(new WeekendHighlightRenderer());
    }

    class Listener implements ChangeListener,DateChangeListener {
        // Implementation of ChangeListener
        public void stateChanged(ChangeEvent evt) {
            validateEditor();
        }

        // Implementation of DateChangeListener
        public void dateChanged(DateChangeEvent evt) {
            closePopup();
            if (needSync())
                m_dateField.setDate(evt.getDate());
            if (m_lastDate == null || !m_lastDate.equals(evt.getDate()))
                fireDateChange(evt.getDate());
            m_lastDate = evt.getDate();
        }
    }

    public TimeZone getTimeZone() {
        return m_model.getTimeZone();
    }


    /* Use this to get the CalendarMenu ServerComponent. The calendar menu will be created lazily.*/
    public JComponent getPopupComponent() {
        if (m_calendarMenu == null) {
            m_calendarMenu = new CalendarMenu(m_model);
            m_calendarMenu.setFont( getFont() );
            // #TODO Property change listener for TimeZone
            m_calendarMenu.getDaySelection().setDateRenderer(m_dateRenderer);
            javax.swing.ToolTipManager.sharedInstance().registerComponent(m_calendarMenu.getDaySelection());
        }
        return m_calendarMenu;
    }

    public void setFont(Font font) {
        super.setFont(font);
        // Method called during super-constructor?
        if (m_calendarMenu == null || font == null)
            return;
        m_calendarMenu.setFont(font);
    }

    /** Selects the date relative to the given timezone.
     * The hour,minute,second and millisecond values will be ignored.
     */
    public void setDate(Date date) 
    {
        if ( date != null)
        {
            m_model.setDate(date);
        }
        else
        {
            boolean changed = m_dateField.getDate() != null;
        	m_dateField.setDate( null);
            m_lastDate =null;
            if ( changed )
            {
            	fireDateChange(null);
            }
        }
    }

    /** Parse the returned date with a calendar-object set to the
     *  correct time-zone to get the date,month and year. The
     *  hour,minute,second and millisecond values should be ignored.
     * @return the selected date
     * @see #getYear
     * @see #getMonth
     * @see #getDay
    */
    public Date getDate() {
        if ( m_dateField.isNullValue())
        {
            return null;
        }
        return m_model.getDate();
    }

    /** selects the specified day, month and year.
       @see #setDate(Date date)*/
    public void select(int day,int month,int year) {
        m_model.setDate(day,month,year);
    }

    /** sets the DateRenderer for the calendar */
    public void setDateRenderer(DateRenderer dateRenderer) {
        m_dateRenderer = dateRenderer;
        if (m_calendarMenu != null) {
            m_calendarMenu.getDaySelection().setDateRenderer(m_dateRenderer);
        }
        m_dateField.setDateRenderer(m_dateRenderer);
    }

    /** you can choose, if weekdays should be displayed in the right corner of the DateField.
        Default is true. This method simply calls setWeekdaysVisble on the DateField ServerComponent.
        If a DateRender is installed the weekday will be rendered with the DateRenderer.
        This includes a tooltip that shows up on the DateRenderer.
        @see DateField
    */
    public void setWeekdaysVisibleInDateField(boolean bVisible) {
        m_dateField.setWeekdaysVisible(bVisible);
    }

    /** @return the selected year (relative to the given TimeZone)
    @see #getDate
    @see #getMonth
    @see #getDay
    */
    public int getYear() {return m_model.getYear(); }

    /** @return the selected month (relative to the given TimeZone)
    @see #getDate
    @see #getYear
    @see #getDay
    */
    public int getMonth() {return m_model.getMonth(); }

    /** @return the selected day (relative to the given TimeZone)
    @see #getDate
    @see #getYear
    @see #getMonth
    */
    public int getDay() {return m_model.getDay(); }

    /** registers new DateChangeListener for this component.
     *  A DateChangeEvent will be fired to every registered DateChangeListener
     *  when the a different date is selected.
     * @see DateChangeListener
     * @see DateChangeEvent
    */
    public void addDateChangeListener( DateChangeListener listener ) {
        m_listenerList.add(listener);
    }

    /** removes a listener from this component.*/
    public void removeDateChangeListener( DateChangeListener listener ) {
        m_listenerList.remove(listener);
    }

    public DateChangeListener[] getDateChangeListeners() {
        return m_listenerList.toArray(new DateChangeListener[]{});
    }

    /** A DateChangeEvent will be fired to every registered DateChangeListener
     *  when the a different date is selected.
    */
    protected void fireDateChange( Date date ) {
        if (m_listenerList.size() == 0)
            return;
        DateChangeListener[] listeners = getDateChangeListeners();
        DateChangeEvent evt = new DateChangeEvent(this,date);
        for (int i = 0;i<listeners.length;i++) {
            listeners[i].dateChanged(evt);
        }
    }

    protected void showPopup() {
        validateEditor();
        super.showPopup();
    }

    /** test if we need to synchronize the dateModel and the dateField*/
    private boolean needSync() {
        return (isNullValuePossible() && m_dateField.getDate() == null ) || (m_dateField.getDate() != null && !m_model.sameDate(m_dateField.getDate()))  ;
    }

    protected void validateEditor() {
        if (needSync()  )
            if (m_dateField.isNullValue())
            {
                if ( m_lastDate != null)
                {
                	m_lastDate = null;
                	fireDateChange(null);
                }
            }
            else
            {
                m_model.setDate(m_dateField.getDate());
            }
    }
    
    public boolean isNullValuePossible() 
    {
        return m_dateField.isNullValuePossible();
    }

    public void setNullValuePossible(boolean nullValuePossible) 
    {
        m_dateField.setNullValuePossible(nullValuePossible);
    }

}

