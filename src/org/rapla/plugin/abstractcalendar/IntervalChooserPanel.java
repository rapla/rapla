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
import org.rapla.components.util.DateTools;
import org.rapla.entities.domain.Period;
import org.rapla.facade.CalendarModel;
import org.rapla.facade.PeriodModel;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.gui.RaplaGUIComponent;
import org.rapla.gui.internal.common.PeriodChooser;
import org.rapla.gui.toolkit.RaplaButton;
import org.rapla.gui.toolkit.RaplaWidget;

public class IntervalChooserPanel extends RaplaGUIComponent
    implements
    RaplaWidget
{
    Collection<DateChangeListener> listenerList = new ArrayList<DateChangeListener>();
    PeriodChooser periodChooser;

    JPanel panel = new JPanel();
    RaplaCalendar startDateSelection;
    RaplaCalendar endDateSelection;
	// BJO 00000042   
    JButton startTodayButton= new RaplaButton(getString("today"), RaplaButton.SMALL);
    JButton prevStartButton = new RaplaArrowButton('<', 20);
    JButton nextStartButton = new RaplaArrowButton('>', 20);
    {
    	   prevStartButton.setSize(30, 20);
    	   nextStartButton.setSize(30, 20);
        
    }

    JButton endTodayButton= new RaplaButton(getString("today"), RaplaButton.SMALL);
    JButton prevEndButton = new RaplaArrowButton('<', 20);
    JButton nextEndButton = new RaplaArrowButton('>', 20);
    {
    	prevEndButton.setSize(30, 20);
        nextEndButton.setSize(30, 20);
    }

    int incrementSize = Calendar.WEEK_OF_YEAR;
	// BJO00000042 

    boolean listenersEnabled = true;
    CalendarModel model;
    Listener listener = new Listener();
    JPanel periodPanel;

    public IntervalChooserPanel(RaplaContext sm, CalendarModel model) throws RaplaException {
        super(sm);
        this.model = model;

        
        periodChooser = new PeriodChooser(getContext(),PeriodChooser.START_AND_END);
        periodChooser.setWeekOfPeriodVisible( false );

        startDateSelection = createRaplaCalendar();
        endDateSelection = createRaplaCalendar();
        //prevButton.setText("<");
        //nextButton.setText(">");
        double pre =TableLayout.PREFERRED;
        double[][] sizes = {{5,pre, 5, pre, 5,0.9,0.02}
                            ,{pre}};
        TableLayout tableLayout = new TableLayout(sizes);
        
        int todayWidth = (int)Math.max(40, startTodayButton.getPreferredSize().getWidth());
		
    	startTodayButton.setPreferredSize( new Dimension(todayWidth,20));
    	endTodayButton.setPreferredSize( new Dimension(todayWidth,20));
        
        panel.setLayout(tableLayout);
        
        JPanel startPanel = new JPanel();
        TitledBorder titleBorder = BorderFactory.createTitledBorder(getString("start_date"));
        startPanel.setBorder(titleBorder);
       
        startPanel.add(startDateSelection);
// BJO 00000042
        startPanel.add(startTodayButton);
        startPanel.add(prevStartButton);
        startPanel.add(nextStartButton);
        startTodayButton.addActionListener( listener );
        prevStartButton.addActionListener( listener );
        nextStartButton.addActionListener( listener );
// BJO 00000042
        panel.add(startPanel,"1,0");
        
        JPanel endPanel = new JPanel();
        titleBorder = BorderFactory.createTitledBorder(getString("end_date"));
        endPanel.setBorder(titleBorder);
        endPanel.add(endDateSelection);
// BJO 00000042
        endPanel.add(endTodayButton);
        endPanel.add(prevEndButton);
        endPanel.add(nextEndButton);
        endTodayButton.addActionListener( listener );
        prevEndButton.addActionListener( listener );
        nextEndButton.addActionListener( listener );
// BJO 00000042
        panel.add(endPanel,"3,0");
        
        periodPanel = new JPanel(new GridLayout(1,1));
        titleBorder = BorderFactory.createTitledBorder(getString("period"));
        periodPanel.setBorder(titleBorder);
        periodPanel.add(periodChooser);
        
        panel.add( periodPanel,"5,0");
        periodChooser.addActionListener( listener );
        

        startDateSelection.addDateChangeListener( listener );
        endDateSelection.addDateChangeListener( listener );
        update();
    }

    public void update() 
    {
        listenersEnabled = false;
        try {
            Date startDate =  model.getStartDate();
            startDateSelection.setDate( startDate);
            final PeriodModel periodModel = getPeriodModel();
            periodChooser.setPeriodModel( periodModel);
            periodChooser.setDate( startDate );
            Date endDate = model.getEndDate();
            periodPanel.setVisible( periodModel.getSize() > 0);
            endDateSelection.setDate( DateTools.subDay(endDate));
        } finally {
            listenersEnabled = true;
        }
    }
	// BJO00000042 
    /** possible values are Calendar.DATE, Calendar.WEEK_OF_YEAR, Calendar.MONTH and Calendar.YEAR.
    Default is Calendar.WEEK_OF_YEAR.
    */
    public void setIncrementSize(int incrementSize) {
    this.incrementSize = incrementSize;
    }
	// BJO00000042 
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
     *  when a different date is selected.
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

    class Listener implements DateChangeListener, ActionListener  {
        public void actionPerformed( ActionEvent e )
        {
            if ( !listenersEnabled )
                return;
// BJO 00000042
            Date date;
            Calendar calendar = getRaplaLocale().createCalendar();
            if (e.getSource() == prevStartButton) { 
                calendar.setTime(startDateSelection.getDate());
            	calendar.add(incrementSize,-1);
            	date = calendar.getTime(); 
            	startDateSelection.setDate(date); 
            }
            else
            	if (e.getSource() == nextStartButton) {
            			calendar.setTime(startDateSelection.getDate());
            			calendar.add(incrementSize,1);
            			date = calendar.getTime(); 
            			startDateSelection.setDate(date);
            	}
            	else
			        if (e.getSource() == prevEndButton) {
			        	calendar.setTime(endDateSelection.getDate());
			            calendar.add(incrementSize,-1);
			            date = calendar.getTime(); 
			            endDateSelection.setDate(date);
			        }
			        else
			            if (e.getSource() == nextEndButton) {
			                calendar.setTime(endDateSelection.getDate());
			            	calendar.add(incrementSize,1);
			            	date = calendar.getTime(); 
			            	endDateSelection.setDate(date);
			            }
				        else
				            if (e.getSource() == startTodayButton) {
				            	calendar.setTime(new Date());
				            	date = calendar.getTime(); 
				            	startDateSelection.setDate(date);
				            }
					        else
					            if (e.getSource() == endTodayButton) {
					            	calendar.setTime(new Date());
					            	date = calendar.getTime(); 
					            	endDateSelection.setDate(date);
					            }
					            else if (e.getSource() == periodChooser) {
				                    // BJO 00000042                 
					                Period period = periodChooser.getPeriod();
					                if ( period == null) {
					                    return;
					                }
					                
					                final Date periodStart = period.getStart();
                                    final Date periodEnd = period.getEnd();
                                    if ( periodEnd != null && periodEnd != null)
                                    {
                                        updateDates( periodStart, periodEnd);
                                        fireDateChange( periodStart);
                                    }
					            }
        }

        public void dateChanged(DateChangeEvent evt) {
            if ( !listenersEnabled )
                return;
            if ( evt.getSource() == startDateSelection)
            {
                Date newStartDate = startDateSelection.getDate();
                if (newStartDate.after( endDateSelection.getDate()))
                {
                    Date endDate = DateTools.addDays(newStartDate, 1);
                    endDateSelection.setDate( endDate);
                }
            }
            if ( evt.getSource() == endDateSelection)
            {
                Date newEndDate = endDateSelection.getDate();
                if (newEndDate.before( startDateSelection.getDate()))
                {
                    Date startDate = DateTools.addDays(newEndDate, -1);
                    startDateSelection.setDate( startDate);
                }
            }
            updateDates( startDateSelection.getDate(), DateTools.addDay(endDateSelection.getDate()));
            fireDateChange(evt.getDate());
        }

        private void updateDates(Date start, Date end) {
            try {
                listenersEnabled = false;
                model.setStartDate(  start);
                model.setEndDate( end );
                model.setSelectedDate( start );
 //               start
                startDateSelection.setDate( start);
                endDateSelection.setDate( end );
            } finally {
                listenersEnabled = true;
            }

        }
    }
}


