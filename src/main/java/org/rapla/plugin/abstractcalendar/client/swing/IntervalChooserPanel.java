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

package org.rapla.plugin.abstractcalendar.client.swing;

import org.rapla.RaplaResources;
import org.rapla.client.RaplaWidget;
import org.rapla.client.swing.RaplaGUIComponent;
import org.rapla.client.swing.internal.common.PeriodChooser;
import org.rapla.client.swing.toolkit.RaplaButton;
import org.rapla.components.calendar.DateChangeEvent;
import org.rapla.components.calendar.DateChangeListener;
import org.rapla.components.calendar.DateRenderer;
import org.rapla.components.calendar.RaplaArrowButton;
import org.rapla.components.calendar.RaplaCalendar;
import org.rapla.components.iolayer.IOInterface;
import org.rapla.components.layout.TableLayout;
import org.rapla.components.util.DateTools;
import org.rapla.entities.domain.Period;
import org.rapla.facade.CalendarModel;
import org.rapla.facade.PeriodModel;
import org.rapla.facade.client.ClientFacade;
import org.rapla.framework.RaplaInitializationException;
import org.rapla.framework.RaplaLocale;
import org.rapla.logger.Logger;

import javax.inject.Inject;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.border.TitledBorder;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;

public class IntervalChooserPanel extends RaplaGUIComponent implements RaplaWidget
{
    Collection<DateChangeListener> listenerList = new ArrayList<>();
    PeriodChooser periodChooser;

    JPanel panel = new JPanel();
    RaplaCalendar startDateSelection;
    RaplaCalendar endDateSelection;
    // BJO 00000042
    JButton startTodayButton = new RaplaButton(getString("today"), RaplaButton.SMALL);
    JButton prevStartButton = new RaplaArrowButton('<', 20);
    JButton nextStartButton = new RaplaArrowButton('>', 20);

    {
        prevStartButton.setSize(30, 20);
        nextStartButton.setSize(30, 20);

    }

    JButton endTodayButton = new RaplaButton(getString("today"), RaplaButton.SMALL);
    JButton prevEndButton = new RaplaArrowButton('<', 20);
    JButton nextEndButton = new RaplaArrowButton('>', 20);

    {
        prevEndButton.setSize(30, 20);
        nextEndButton.setSize(30, 20);
    }

    DateTools.IncrementSize incrementSize = DateTools.IncrementSize.WEEK_OF_YEAR;
    // BJO00000042

    boolean listenersEnabled = true;
    CalendarModel model;
    Listener listener = new Listener();
    JPanel periodPanel;

    @Inject
    public IntervalChooserPanel(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, CalendarModel model, DateRenderer dateRenderer, IOInterface ioInterface) throws
            RaplaInitializationException
    {
        super(facade, i18n, raplaLocale, logger);
        this.model = model;

        periodChooser = new PeriodChooser(i18n, facade.getRaplaFacade(), PeriodChooser.START_AND_END);
        periodChooser.setWeekOfPeriodVisible(false);

        startDateSelection = RaplaGUIComponent.createRaplaCalendar(dateRenderer, ioInterface, i18n,raplaLocale, logger);
        endDateSelection = RaplaGUIComponent.createRaplaCalendar(dateRenderer, ioInterface, i18n,raplaLocale, logger);
        //prevButton.setText("<");
        //nextButton.setText(">");
        double pre = TableLayout.PREFERRED;
        double[][] sizes = { { 5, pre, 5, pre, 5, 0.9, 0.02 }, { pre } };
        TableLayout tableLayout = new TableLayout(sizes);

        int todayWidth = (int) Math.max(40, startTodayButton.getPreferredSize().getWidth());

        startTodayButton.setPreferredSize(new Dimension(todayWidth, 20));
        endTodayButton.setPreferredSize(new Dimension(todayWidth, 20));

        panel.setLayout(tableLayout);

        JPanel startPanel = new JPanel();
        TitledBorder titleBorder = BorderFactory.createTitledBorder(getString("start_date"));
        startPanel.setBorder(titleBorder);

        startPanel.add(startDateSelection);
        // BJO 00000042
        startPanel.add(startTodayButton);
        startPanel.add(prevStartButton);
        startPanel.add(nextStartButton);
        startTodayButton.addActionListener(listener);
        prevStartButton.addActionListener(listener);
        nextStartButton.addActionListener(listener);
        // BJO 00000042
        panel.add(startPanel, "1,0");

        JPanel endPanel = new JPanel();
        titleBorder = BorderFactory.createTitledBorder(getString("end_date"));
        endPanel.setBorder(titleBorder);
        endPanel.add(endDateSelection);
        // BJO 00000042
        endPanel.add(endTodayButton);
        endPanel.add(prevEndButton);
        endPanel.add(nextEndButton);
        endTodayButton.addActionListener(listener);
        prevEndButton.addActionListener(listener);
        nextEndButton.addActionListener(listener);
        // BJO 00000042
        panel.add(endPanel, "3,0");

        periodPanel = new JPanel(new GridLayout(1, 1));
        titleBorder = BorderFactory.createTitledBorder(getString("period"));
        periodPanel.setBorder(titleBorder);
        periodPanel.add(periodChooser);

        panel.add(periodPanel, "5,0");
        periodChooser.addActionListener(listener);

        startDateSelection.addDateChangeListener(listener);
        endDateSelection.addDateChangeListener(listener);
        update();
    }

    public void update()
    {
        listenersEnabled = false;
        try
        {
            Date startDate = model.getStartDate();
            setStartDate(startDate);
            final PeriodModel periodModel = getPeriodModel();
            periodChooser.setPeriodModel(periodModel);
            periodChooser.setDate(startDate);
            Date endDate = model.getEndDate();
            periodPanel.setVisible(periodModel.getSize() > 0);
            setEndDate(endDate);
        }
        finally
        {
            listenersEnabled = true;
        }
    }
    // BJO00000042

    public void setIncrementSize(DateTools.IncrementSize incrementSize)
    {
        this.incrementSize = incrementSize;
    }
    // BJO00000042

    /** registers new DateChangeListener for this component.
     *  An DateChangeEvent will be fired to every registered DateChangeListener
     *  when the a different date is selected.
     * @see DateChangeListener
     * @see DateChangeEvent
     */
    public void addDateChangeListener(DateChangeListener listener)
    {
        listenerList.add(listener);
    }

    /** removes a listener from this component.*/
    public void removeDateChangeListener(DateChangeListener listener)
    {
        listenerList.remove(listener);
    }

    public DateChangeListener[] getDateChangeListeners()
    {
        return listenerList.toArray(new DateChangeListener[] {});
    }

    /** An ActionEvent will be fired to every registered ActionListener
     *  when a different date is selected.
     */
    protected void fireDateChange(Date date)
    {
        if (listenerList.size() == 0)
            return;
        DateChangeListener[] listeners = getDateChangeListeners();
        DateChangeEvent evt = new DateChangeEvent(this, date);
        for (int i = 0; i < listeners.length; i++)
        {
            listeners[i].dateChanged(evt);
        }
    }



    public JComponent getComponent()
    {
        return panel;
    }

    class Listener implements DateChangeListener, ActionListener
    {
        public void actionPerformed(ActionEvent e)
        {
            if (!listenersEnabled)
                return;
            // BJO 00000042
            final Object source = e.getSource();
            if (source == prevStartButton)
            {
                setStartDate(DateTools.add(getStartDate(), incrementSize, -1));
            }
            else if (source == nextStartButton)
            {
                setStartDate(DateTools.add(getStartDate(), incrementSize, 1));
            }
            else if (source == prevEndButton)
            {
                setEndDate(DateTools.add(getEndDate(), incrementSize, -1));
            }
            else if (source == nextEndButton)
            {
                setEndDate(DateTools.add(getEndDate(), incrementSize, 1));
            }
            else if (source == startTodayButton)
            {
                setStartDate(getFacade().today());
            }
            else if (source == endTodayButton)
            {
                setEndDate(DateTools.addDay(getFacade().today()));
            }
            else if (source == periodChooser)
            {
                Period period = periodChooser.getPeriod();
                if (period == null)
                {
                    return;
                }

                final Date periodStart = period.getStart();
                final Date periodEnd = period.getEnd();
                if (periodEnd != null && periodEnd != null)
                {
                    updateDates(periodStart, periodEnd);
                    fireDateChange(periodStart);
                }
            }
        }

        public void dateChanged(DateChangeEvent evt)
        {
            if (!listenersEnabled)
                return;
            final Object source = evt.getSource();
            Date startDate = getStartDate();
            Date endDate = getEndDate();
            if (source == startDateSelection)
            {
                if (endDate != null && startDate.after(DateTools.subDay(endDate)))
                {
                    endDate = DateTools.addDay(startDate );
                }
            }
            else if (source == endDateSelection)
            {
                endDate = getEndDate();
                if (endDate.before(startDate))
                {
                    startDate = DateTools.subDay(endDate);
                }
            }
            // We need to fill the date to make it a proper end Date
            updateDates(startDate,endDate);
            fireDateChange(evt.getDate());
        }

        private void updateDates(Date start, Date end)
        {
            try
            {
                listenersEnabled = false;
                model.setStartDate(start);
                model.setEndDate(end);
                model.setSelectedDate(start);
                //               start
                setStartDate( start);
                setEndDate( end);


            }
            finally
            {
                listenersEnabled = true;
            }

        }
    }

    private void setStartDate(Date date)
    {
        startDateSelection.setDate(date);
    }

    private void setEndDate(Date date)
    {
        if (date == null)
        {
            endDateSelection.setDate(null);
        }
        else
        {
            endDateSelection.setDate(DateTools.subDay(date));
        }
    }

    private Date getStartDate()
    {
        return startDateSelection.getDate();
    }

    private Date getEndDate()
    {
        final Date date = endDateSelection.getDate();
        if ( date == null)
        {
            return null;
        }
        else
        {
            return DateTools.addDay(DateTools.cutDate( date));
        }
    }
}


