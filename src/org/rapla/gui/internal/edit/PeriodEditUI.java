/*--------------------------------------------------------------------------*
 | Copyright (C) 2006 Christopher Kohlhaas                                  |
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
package org.rapla.gui.internal.edit;

import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.Collection;

import javax.swing.Box;
import javax.swing.JComponent;
import javax.swing.JPanel;

import org.rapla.components.calendar.DateChangeEvent;
import org.rapla.components.calendar.DateChangeListener;
import org.rapla.components.calendar.RaplaCalendar;
import org.rapla.components.util.DateTools;
import org.rapla.entities.domain.Period;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;

public class PeriodEditUI extends AbstractEditUI<Period>
    implements
     DateChangeListener
{

    RaplaCalendar startDate;
    RaplaCalendar endDate;
    TextField nameField; 
    private boolean listenerEnabled = true;

    JPanel wrapper = new JPanel();
    /**
     * @param sm
     * @throws RaplaException
     */
    public PeriodEditUI(RaplaContext sm) throws RaplaException {
        super(sm);
        wrapper.setLayout(new BorderLayout());
        wrapper.add(editPanel,BorderLayout.CENTER);
        wrapper.add(Box.createVerticalStrut(150),BorderLayout.SOUTH);
        DateField startDateField = new DateField(sm,"start_date"); 
        DateField endDateField =  new DateField(sm,"end_date"); 
        Collection<EditField> fields = new ArrayList<EditField>();
        nameField = new TextField(sm,"name");
        fields.add(nameField);
        fields.add(startDateField);
        fields.add(endDateField);
        setFields(fields);
        startDate = startDateField.getCalendar();
        endDate = endDateField.getCalendar();
        startDate.addDateChangeListener(this);
        endDate.addDateChangeListener(this);
    }

    public void dateChanged(DateChangeEvent evt) {
        if (!listenerEnabled)
            return;
        listenerEnabled = false;
        if (endDate.getDate().getTime() <= startDate.getDate().getTime()) {
            if (evt.getSource() == startDate) {
                endDate.setDate(DateTools.addDay(DateTools.cutDate(startDate.getDate())));
                getLogger().debug("enddate adjusted");
            }
            if (evt.getSource() == endDate) {
                startDate.setDate(DateTools.subDay(DateTools.cutDate(endDate.getDate())));
                getLogger().debug("startdate adjusted");
            }
        }
        listenerEnabled = true;

    }

    public JComponent getComponent() {
        return wrapper;
    }

    public void mapToObjects() throws RaplaException {
    	Period period =  objectList.get(0);
    	period.setStart(startDate.getDate());
    	period.setEnd(DateTools.addDay(endDate.getDate()));
    	period.setName( nameField.getValue());

    	if (getName(period).length() == 0)
            throw new RaplaException(getString("error.no_name"));
        if ((period.getEnd().getTime() - period.getStart().getTime()) < DateTools.MILLISECONDS_PER_WEEK)
            showWarning(getString("warning.period_shorter_than_week"),getComponent());
    }
    
    public void mapFromObjects() 
    {
        Period period = objectList.iterator().next();
        nameField.setValue( period.getName());
        startDate.setDate( period.getStart());
        endDate.setDate(DateTools.subDay(period.getEnd()));
    }
}


