/*--------------------------------------------------------------------------*
 | Copyright (C) 2006  Christopher Kohlhaas                                 |
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

package org.rapla.plugin.abstractcalendar;

import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.border.TitledBorder;

import org.rapla.components.calendar.DateChangeEvent;
import org.rapla.components.calendar.DateChangeListener;
import org.rapla.components.calendar.RaplaArrowButton;
import org.rapla.components.calendar.RaplaCalendar;
import org.rapla.components.layout.TableLayout;
import org.rapla.entities.domain.Period;
import org.rapla.facade.CalendarModel;
import org.rapla.facade.PeriodModel;
import org.rapla.facade.QueryModule;
import org.rapla.framework.Disposable;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.gui.RaplaGUIComponent;
import org.rapla.gui.internal.common.PeriodChooser;
import org.rapla.gui.toolkit.RaplaButton;
import org.rapla.gui.toolkit.RaplaWidget;

public class DateChooserPanel extends RaplaGUIComponent
    implements
        Disposable
        ,RaplaWidget
{
    Collection<DateChangeListener> listenerList = new ArrayList<DateChangeListener>();

    JPanel panel = new JPanel();
    JButton prevButton = new RaplaArrowButton('<', 20);
    RaplaCalendar dateSelection;
    PeriodChooser periodChooser;
    JButton nextButton = new RaplaArrowButton('>', 20);
    int incrementSize = Calendar.WEEK_OF_YEAR;
    CalendarModel model;
    Listener listener = new Listener();
    JPanel periodPanel;
    
    JButton todayButton= new RaplaButton(getString("today"), RaplaButton.SMALL);
    
    public DateChooserPanel(RaplaContext sm, CalendarModel model) throws RaplaException {
        super( sm );
        this.model = model;
        prevButton.setSize(30, 20);
        nextButton.setSize(30, 20);
        
        periodChooser = new PeriodChooser(getContext(),PeriodChooser.START_ONLY);
        dateSelection = createRaplaCalendar();

        //prevButton.setText("<");
        //nextButton.setText(">");
        double pre =TableLayout.PREFERRED;
        double[][] sizes = {{5,pre,5,pre,2,pre,0.02,0.9,5,0.02}
                            ,{/*0.5,*/pre/*,0.5*/}};
        TableLayout tableLayout = new TableLayout(sizes);
        JPanel calendarPanel = new JPanel();
        TitledBorder titleBorder = BorderFactory.createTitledBorder(getI18n().getString("date")); 
        calendarPanel.setBorder(titleBorder);
        panel.setLayout(tableLayout);
        calendarPanel.add(dateSelection);
        calendarPanel.add(todayButton);
        int todayWidth = (int)Math.max(40, todayButton.getPreferredSize().getWidth());
		todayButton.setPreferredSize( new Dimension(todayWidth,20));
        calendarPanel.add(prevButton);
        calendarPanel.add(nextButton);
        panel.add(calendarPanel, "1, 0");
        periodPanel = new JPanel(new GridLayout(1,1));
        titleBorder = BorderFactory.createTitledBorder(getI18n().getString("period"));
        periodPanel.setBorder(titleBorder);
        periodPanel.add(periodChooser);
        panel.add(periodPanel,"7,0");
     
        
        periodChooser.setDate(getQuery().today());

        nextButton.addActionListener( listener );
        prevButton.addActionListener( listener);

        dateSelection.addDateChangeListener( listener);
        periodChooser.addActionListener( listener);

        todayButton.addActionListener(listener);
        update();
    }

    boolean listenersEnabled = true;
    public void update() 
    {
        listenersEnabled = false;
        try {
            final PeriodModel periodModel = getPeriodModel();
            periodChooser.setPeriodModel( periodModel);
            if ( model.getSelectedDate() == null) {
                QueryModule query = getQuery();
				Date today = query.today();
				model.setSelectedDate( today);
            }
            Date date = model.getSelectedDate();
            periodChooser.setDate( date);
            dateSelection.setDate( date);
            periodPanel.setVisible( periodModel.getSize() > 0);

        } finally {
            listenersEnabled = true;
        }
    }

    public void dispose() {
        periodChooser.removeActionListener( listener );
        periodChooser.dispose();
    }

    public void setNavigationVisible( boolean enable) {
        nextButton.setVisible( enable);
        prevButton.setVisible( enable);
    }

    /** possible values are Calendar.DATE, Calendar.WEEK_OF_YEAR, Calendar.MONTH and Calendar.YEAR.
        Default is Calendar.WEEK_OF_YEAR.
     */
    public void setIncrementSize(int incrementSize) {
        this.incrementSize = incrementSize;
    }

    /** registers new DateChangeListener for this component.
     *  An DateChangeEvent will be fired to every registered DateChangeListener
     *  when the a different date is selected.
     * @see DateChangeListener
     * @see DateChangeEvent
    */
    public void addDateChangeListener(DateChangeListener listener) {
        listenerList.add(listener);
    }

    /** removes a listener from this component.*/
    public void removeDateChangeListener(DateChangeListener listener) {
        listenerList.remove(listener);
    }

    public DateChangeListener[] getDateChangeListeners() {
        return listenerList.toArray(new DateChangeListener[]{});
    }

    /** An ActionEvent will be fired to every registered ActionListener
     *  when the a different date is selected.
    */
    protected void fireDateChange(Date date) {
        if (listenerList.size() == 0)
            return;
        DateChangeListener[] listeners = getDateChangeListeners();
        DateChangeEvent evt = new DateChangeEvent(this,date);
        for (int i = 0;i<listeners.length;i++) {
            listeners[i].dateChanged(evt);
        }
    }

    public JComponent getComponent() {
        return panel;
    }
    
    protected int getIncrementAmount(int incrementSize) 
    {
        if (incrementSize == Calendar.WEEK_OF_YEAR)
        {
            int daysInWeekview = getCalendarOptions().getDaysInWeekview();
            return Math.max(1,daysInWeekview / 7 );
        }
        return 1;
    }

    class Listener implements ActionListener, DateChangeListener {

        public void actionPerformed(ActionEvent evt) {
            if (!listenersEnabled)
                return;

            Date date;

            Calendar calendar = getRaplaLocale().createCalendar();
            Date date2 = dateSelection.getDate();
			calendar.setTime(date2);

            if (evt.getSource() == prevButton) {
                calendar.add(incrementSize,-getIncrementAmount(incrementSize ));
            }
            //eingefuegt: rku
            if (evt.getSource() == todayButton) {
                Date today = getQuery().today();
				calendar.setTime(today);
            }
 
            if (evt.getSource() == nextButton) {
                calendar.add(incrementSize,getIncrementAmount(incrementSize ));
            }
            if (evt.getSource() == periodChooser) {
                final Date periodDate = periodChooser.getDate();
                Period period = periodChooser.getPeriod();
                final Date start = period.getStart();
                final Date end = period.getEnd();
                if ( period == null || start == null || end == null)
                {
                    getLogger().warn("Period start or end can't be null");
                    return;
                }
                date = periodDate;
                model.setStartDate( start );
                model.setEndDate( end );
            } else {
                date = calendar.getTime();
            }
            updateDates( date );
            fireDateChange( date);
        }

        public void dateChanged(DateChangeEvent evt) {
            if ( !listenersEnabled)
                return;
            try {
                listenersEnabled = false;
            } finally {
                listenersEnabled = true;
            }
            Date date = evt.getDate();
			updateDates( date);
            fireDateChange(date);
        }

        private void updateDates(Date date) {
            try {
                listenersEnabled = false;
                model.setSelectedDate( date );
		// EXCO: It seems not nice to me that the start date
		// is in the parameter and the end date is extracted
		// from the model.
		// But, with this way, I am certain that
		// nothing can get broken.
                Date endDate = model.getEndDate();
				periodChooser.setDate( date, endDate );
                dateSelection.setDate( date);
            } finally {
                listenersEnabled = true;
            }

        }


    }
}


